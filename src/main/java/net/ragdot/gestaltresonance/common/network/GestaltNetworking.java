package net.ragdot.gestaltresonance.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltAttackEvents;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.LedgeGrabLogic;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.passive.GestaltPassive;
import net.ragdot.gestaltresonance.common.passive.GestaltPassiveRegistry;

import javax.annotation.Nullable;
import java.util.Optional;

@EventBusSubscriber(modid = GestaltResonance.MODID)
public class GestaltNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GestaltResonance.MODID).versioned("1");
        registrar.playToServer(ToggleSummonC2S.TYPE, ToggleSummonC2S.STREAM_CODEC, GestaltNetworking::handleToggle);
        registrar.playToClient(SyncGestaltStateS2C.TYPE, SyncGestaltStateS2C.STREAM_CODEC, GestaltNetworking::handleSync);
        // Ledge grab packets
        registrar.playToServer(StartLedgeGrabC2S.TYPE, StartLedgeGrabC2S.STREAM_CODEC, GestaltNetworking::handleStartLedgeGrab);
        registrar.playToServer(StopLedgeGrabC2S.TYPE, StopLedgeGrabC2S.STREAM_CODEC, GestaltNetworking::handleStopLedgeGrab);
        registrar.playToClient(SyncLedgeGrabS2C.TYPE, SyncLedgeGrabS2C.STREAM_CODEC, GestaltNetworking::handleSyncLedgeGrab);
        // Guard packets
        registrar.playToServer(StartGuardC2S.TYPE, StartGuardC2S.STREAM_CODEC, GestaltNetworking::handleStartGuard);
        registrar.playToServer(StopGuardC2S.TYPE, StopGuardC2S.STREAM_CODEC, GestaltNetworking::handleStopGuard);
        registrar.playToClient(TriggerGuardS2C.TYPE, TriggerGuardS2C.STREAM_CODEC, GestaltNetworking::handleTriggerGuard);
        // Crash packet
        registrar.playToClient(TriggerGestaltCrashS2C.TYPE, TriggerGestaltCrashS2C.STREAM_CODEC, GestaltNetworking::handleTriggerGestaltCrash);
        // Gestalt XP/level sync
        registrar.playToClient(SyncGestaltXpS2C.TYPE, SyncGestaltXpS2C.STREAM_CODEC, GestaltNetworking::handleSyncGestaltXp);
        // Attack chain packets
        registrar.playToServer(AttackInputC2S.TYPE, AttackInputC2S.STREAM_CODEC, GestaltNetworking::handleAttackInput);
        registrar.playToClient(SyncAttackActionS2C.TYPE, SyncAttackActionS2C.STREAM_CODEC, GestaltNetworking::handleSyncAttackAction);
    }

    private static void handleAttackInput(AttackInputC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltAttackEvents.handleAttackInput(serverPlayer);
            }
        });
    }

    private static void handleSyncAttackAction(SyncAttackActionS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            GestaltAction newAction = packet.toAction();
            GestaltAction prevAction = state.getAction();
            state.setAction(newAction);
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

            // Whenever the chain ends or advances, notify the animation system to reset.
            // This guarantees the client resets even if IDLE→HIT_1 packets arrive in
            // the same client tick (race condition where setupAnim never observes IDLE).
            boolean prevWasHit = prevAction == GestaltAction.HIT_1
                    || prevAction == GestaltAction.HIT_2
                    || prevAction == GestaltAction.HIT_3;
            if (prevWasHit && onChainTransitionCallback != null) {
                onChainTransitionCallback.accept(targetPlayer.getUUID());
            }
        });
    }

    /**
     * Client-side callback invoked when a hit-chain transition occurs. The client mod
     * registers this in setup to forward the event to {@code GestaltModel.notifyChainEnd}.
     * Common code can't reference client classes directly, hence the indirection.
     */
    public static java.util.function.Consumer<java.util.UUID> onChainTransitionCallback = null;

    private static void handleToggle(ToggleSummonC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer serverPlayer) {
                PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                boolean wasSummoned = state.isSummoned();
                // Cancel any active attack chain before toggling
                if (wasSummoned) GestaltAttackEvents.cancelChain(serverPlayer);
                state.toggleSummon();

                // Block re-summon during crash cooldown or while hunger is still too low
                if (!wasSummoned && state.isSummoned()) {
                    long currentTick = serverPlayer.getServer().getTickCount();
                    if (state.hasCrashCooldown(currentTick) ||
                            serverPlayer.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) {
                        state.setSummoned(false);
                        serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                        return;
                    }
                }

                serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

                // Activate/deactivate passive ability
                GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
                if (passive != null) {
                    if (state.isSummoned() && !wasSummoned) {
                        passive.onActivate(serverPlayer);
                    } else if (!state.isSummoned() && wasSummoned) {
                        passive.onDeactivate(serverPlayer);
                    }
                }

                if (state.isSummoned() && !wasSummoned) {
                    serverPlayer.playNotifySound(GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                } else if (!state.isSummoned() && wasSummoned) {
                    serverPlayer.playNotifySound(GestaltSounds.GESTALT_DISMISS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                GestaltResonance.LOGGER.debug("Gestalt toggled for {}: summoned={}, id={}",
                        serverPlayer.getName().getString(), state.isSummoned(), state.getGestaltId());
                syncToTracking(serverPlayer);
            }
        });
    }

    private static void handleSync(SyncGestaltStateS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (target instanceof Player targetPlayer) {
                PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                state.setSummoned(packet.summoned());
                state.setGestaltId(packet.gestaltId());
                targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            }
        });
    }

    // --- Ledge grab handlers ---

    private static void handleStartLedgeGrab(StartLedgeGrabC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer serverPlayer) {
                PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                // Don't start if already grabbing
                if (state.isLedgeGrabbing()) return;

                // Server re-validates the candidate
                Vec3 anchor = LedgeGrabLogic.validateLedge(serverPlayer, packet.ledgePos(), packet.face());
                if (anchor == null) {
                    GestaltResonance.LOGGER.debug("Ledge grab rejected for {}: validation failed", serverPlayer.getName().getString());
                    return;
                }

                state.startLedgeGrab(packet.ledgePos(), packet.face(), anchor);
                serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                syncLedgeGrabToTracking(serverPlayer, true, packet.ledgePos(), packet.face());
                GestaltResonance.LOGGER.debug("Ledge grab started for {} at {}", serverPlayer.getName().getString(), packet.ledgePos());
            }
        });
    }

    private static void handleStopLedgeGrab(StopLedgeGrabC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer serverPlayer) {
                PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                if (!state.isLedgeGrabbing()) return;

                LedgeGrabLogic.startMantle(serverPlayer, state);
                GestaltResonance.LOGGER.debug("Ledge grab stopped (mantle) for {}", serverPlayer.getName().getString());
            }
        });
    }

    private static void handleSyncLedgeGrab(SyncLedgeGrabS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (target instanceof Player targetPlayer) {
                PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                if (packet.grabbing() && packet.ledgePos().isPresent()) {
                    // Client-side: set boolean + face for rendering/animation purposes
                    // We don't need full anchor data on client — server controls movement
                    state.startLedgeGrab(packet.ledgePos().get(), packet.ledgeFace(), null);
                } else {
                    state.clearLedgeGrab();
                }
                targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            }
        });
    }

    private static void handleStartGuard(StartGuardC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isSummoned() || state.isGuarding()) return;
            if (state.hasGuardCooldown(serverPlayer.getServer().getTickCount())) return;
            state.startGuard();
            serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            syncGuardToTracking(serverPlayer, true);
        });
    }

    private static void handleStopGuard(StopGuardC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isGuarding()) return;
            state.clearGuard();
            serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            syncGuardToTracking(serverPlayer, false);
        });
    }

    private static void handleTriggerGuard(TriggerGuardS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;

            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (packet.active()) {
                state.setAction(GestaltAction.GUARD);
                targetPlayer.playSound(SoundEvents.SHIELD_BLOCK, 0.8f, 1.1f);
            } else if (state.getAction() == GestaltAction.GUARD) {
                state.setAction(GestaltAction.IDLE);
            }
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleTriggerGestaltCrash(TriggerGestaltCrashS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setCrashingOut(true);
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleSyncGestaltXp(SyncGestaltXpS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setGestaltLevel(packet.gestaltLevel());
            state.setGestaltXp(packet.gestaltXp());
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    /** Send the gestalt level and XP to the owning player's client. */
    public static void syncGestaltXpToPlayer(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        SyncGestaltXpS2C packet = new SyncGestaltXpS2C(
                serverPlayer.getId(), state.getGestaltLevel(), state.getGestaltXp());
        PacketDistributor.sendToPlayer(serverPlayer, packet);
    }

    /** Notify all tracking clients that a player's gestalt crashed out (hunger). */
    public static void syncCrashToTracking(ServerPlayer serverPlayer) {
        TriggerGestaltCrashS2C packet = new TriggerGestaltCrashS2C(serverPlayer.getId());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Send the current gestalt state of a player to all tracking clients + the player. */
    public static void syncToTracking(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        SyncGestaltStateS2C packet = new SyncGestaltStateS2C(
                serverPlayer.getId(), state.isSummoned(), state.getGestaltId());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Send ledge grab state to all tracking clients + the player. */
    public static void syncLedgeGrabToTracking(ServerPlayer serverPlayer, boolean grabbing, @Nullable BlockPos ledgePos, @Nullable Direction face) {
        int faceIndex = face != null ? face.ordinal() : -1;
        SyncLedgeGrabS2C packet = new SyncLedgeGrabS2C(
                serverPlayer.getId(), grabbing, Optional.ofNullable(ledgePos), faceIndex);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Notify all tracking clients that a player's guard activated or expired. */
    public static void syncGuardToTracking(ServerPlayer serverPlayer, boolean active) {
        TriggerGuardS2C packet = new TriggerGuardS2C(serverPlayer.getId(), active);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Notify all tracking clients of the current attack-chain action (HIT_1/2/3 or IDLE). */
    public static void syncAttackActionToTracking(ServerPlayer serverPlayer, GestaltAction action) {
        SyncAttackActionS2C packet = new SyncAttackActionS2C(
                serverPlayer.getId(), SyncAttackActionS2C.fromAction(action));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }
}
