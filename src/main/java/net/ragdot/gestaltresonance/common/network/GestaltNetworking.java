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
import net.ragdot.gestaltresonance.common.GestaltChargedStrikeEvents;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.GestaltXpChannelEvents;
import net.ragdot.gestaltresonance.common.LedgeGrabLogic;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSoulProjectionEvents;
import net.ragdot.gestaltresonance.common.SoulProjectionExitType;
import net.ragdot.gestaltresonance.common.WallSlideLogic;
import net.ragdot.gestaltresonance.common.passive.AmenBreakPassive;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower2G;
import net.ragdot.gestaltresonance.common.power.spillways.SpillwaysPower2G;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower3G;
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
        // Fall break impact (shake feedback)
        registrar.playToClient(FallBreakImpactS2C.TYPE, FallBreakImpactS2C.STREAM_CODEC, GestaltNetworking::handleFallBreakImpact);
        // Gestalt XP/level sync
        registrar.playToClient(SyncGestaltXpS2C.TYPE, SyncGestaltXpS2C.STREAM_CODEC, GestaltNetworking::handleSyncGestaltXp);
        // Attack chain packets
        registrar.playToServer(AttackInputC2S.TYPE, AttackInputC2S.STREAM_CODEC, GestaltNetworking::handleAttackInput);
        registrar.playToClient(SyncAttackActionS2C.TYPE, SyncAttackActionS2C.STREAM_CODEC, GestaltNetworking::handleSyncAttackAction);
        // Throw packet
        registrar.playToServer(ThrowInputC2S.TYPE, ThrowInputC2S.STREAM_CODEC, GestaltNetworking::handleThrowInput);
        // XP channeling packets
        registrar.playToServer(StartChannelXpC2S.TYPE, StartChannelXpC2S.STREAM_CODEC, GestaltNetworking::handleStartChannelXp);
        registrar.playToServer(StopChannelXpC2S.TYPE, StopChannelXpC2S.STREAM_CODEC, GestaltNetworking::handleStopChannelXp);
        registrar.playToClient(SyncChannelStateS2C.TYPE, SyncChannelStateS2C.STREAM_CODEC, GestaltNetworking::handleSyncChannelState);
        // Skin packets
        registrar.playToServer(SelectGestaltSkinC2S.TYPE, SelectGestaltSkinC2S.STREAM_CODEC, GestaltNetworking::handleSelectSkin);
        registrar.playToClient(SyncSelectedSkinS2C.TYPE, SyncSelectedSkinS2C.STREAM_CODEC, GestaltNetworking::handleSyncSelectedSkin);
        registrar.playToClient(SyncUnlockedSkinsS2C.TYPE, SyncUnlockedSkinsS2C.STREAM_CODEC, GestaltNetworking::handleSyncUnlockedSkins);
        registrar.playToClient(SkinUnlockedToastS2C.TYPE, SkinUnlockedToastS2C.STREAM_CODEC, GestaltNetworking::handleSkinUnlockedToast);
        // Charged-strike packets
        registrar.playToServer(StartChargedStrikeC2S.TYPE, StartChargedStrikeC2S.STREAM_CODEC, GestaltNetworking::handleStartChargedStrike);
        registrar.playToServer(ReleaseChargedStrikeC2S.TYPE, ReleaseChargedStrikeC2S.STREAM_CODEC, GestaltNetworking::handleReleaseChargedStrike);
        registrar.playToClient(SyncChargedStrikeTravelS2C.TYPE, SyncChargedStrikeTravelS2C.STREAM_CODEC, GestaltNetworking::handleSyncChargedStrikeTravel);
        // Wall slide sync
        registrar.playToClient(SyncWallSlideS2C.TYPE, SyncWallSlideS2C.STREAM_CODEC, GestaltNetworking::handleSyncWallSlide);
        // Soul projection packets
        registrar.playToServer(SoulProjectionActivateC2S.TYPE, SoulProjectionActivateC2S.STREAM_CODEC, GestaltNetworking::handleSoulProjectionActivate);
        registrar.playToServer(SoulProjectionExitC2S.TYPE, SoulProjectionExitC2S.STREAM_CODEC, GestaltNetworking::handleSoulProjectionExit);
        registrar.playToServer(SoulProjectionActionC2S.TYPE, SoulProjectionActionC2S.STREAM_CODEC, GestaltNetworking::handleSoulProjectionAction);
        // Power activation
        registrar.playToServer(PowerActivateC2S.TYPE, PowerActivateC2S.STREAM_CODEC, GestaltNetworking::handlePowerActivate);
        registrar.playToClient(SyncSoulProjectionS2C.TYPE, SyncSoulProjectionS2C.STREAM_CODEC, GestaltNetworking::handleSyncSoulProjection);
        registrar.playToClient(SoulProjectionYankS2C.TYPE, SoulProjectionYankS2C.STREAM_CODEC, GestaltNetworking::handleSoulProjectionYank);
        // Mining sync (so other players see the mining pose)
        registrar.playToServer(SyncMiningStateC2S.TYPE, SyncMiningStateC2S.STREAM_CODEC, GestaltNetworking::handleSyncMiningStateC2S);
        registrar.playToClient(SyncMiningStateS2C.TYPE, SyncMiningStateS2C.STREAM_CODEC, GestaltNetworking::handleSyncMiningStateS2C);
        // Resonance HUD sync (owning client only)
        registrar.playToClient(SyncResonanceS2C.TYPE, SyncResonanceS2C.STREAM_CODEC, GestaltNetworking::handleSyncResonance);
        // Cooldown HUD sync (owning client only)
        registrar.playToClient(SyncCooldownS2C.TYPE, SyncCooldownS2C.STREAM_CODEC, GestaltNetworking::handleSyncCooldown);
        // Per-power-slot cooldown overlay sync (owning client only)
        registrar.playToClient(SyncPowerCooldownS2C.TYPE, SyncPowerCooldownS2C.STREAM_CODEC, GestaltNetworking::handleSyncPowerCooldown);
        // Hit-chain particle burst
        registrar.playToClient(SpawnHitParticlesS2C.TYPE, SpawnHitParticlesS2C.STREAM_CODEC, GestaltNetworking::handleSpawnHitParticles);
        // Phase Out (Power 2G)
        registrar.playToServer(PhaseOutToggleC2S.TYPE, PhaseOutToggleC2S.STREAM_CODEC, GestaltNetworking::handlePhaseOutToggle);
        registrar.playToClient(PhaseOutStateSyncS2C.TYPE, PhaseOutStateSyncS2C.STREAM_CODEC, GestaltNetworking::handlePhaseOutStateSync);
        // Moist Air - Spillways (Power 2G)
        registrar.playToServer(MoistAirToggleC2S.TYPE, MoistAirToggleC2S.STREAM_CODEC, GestaltNetworking::handleMoistAirToggle);
        registrar.playToClient(MoistAirStateSyncS2C.TYPE, MoistAirStateSyncS2C.STREAM_CODEC, GestaltNetworking::handleMoistAirStateSync);
        // Phase Court (Power 3G)
        registrar.playToClient(SyncPhaseCourtS2C.TYPE, SyncPhaseCourtS2C.STREAM_CODEC, GestaltNetworking::handleSyncPhaseCourt);
        // Time Phase (Power 3S)
        registrar.playToClient(SyncTimePhaseS2C.TYPE, SyncTimePhaseS2C.STREAM_CODEC, GestaltNetworking::handleSyncTimePhase);
        // Ghost translucency broadcast (Phase Court + Time Phase) — sent to all tracking clients
        registrar.playToClient(SyncPlayerGhostS2C.TYPE, SyncPlayerGhostS2C.STREAM_CODEC, GestaltNetworking::handleSyncPlayerGhost);
        // Client-only afterimages (used by Time Phase and Phase Court — recipient-only visuals)
        registrar.playToClient(SpawnAfterimageS2C.TYPE, SpawnAfterimageS2C.STREAM_CODEC, GestaltNetworking::handleSpawnAfterimage);
        registrar.playToClient(DiscardAfterimageS2C.TYPE, DiscardAfterimageS2C.STREAM_CODEC, GestaltNetworking::handleDiscardAfterimage);
        registrar.playToClient(ClearAfterimagesS2C.TYPE, ClearAfterimagesS2C.STREAM_CODEC, GestaltNetworking::handleClearAfterimages);
        // Dusty Documents (writable file save)
        registrar.playToServer(SaveDustyDocC2S.TYPE, SaveDustyDocC2S.STREAM_CODEC, GestaltNetworking::handleSaveDustyDoc);
    }

    private static void handleAttackInput(AttackInputC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltAttackEvents.handleAttackInput(serverPlayer);
            }
        });
    }

    private static void handleThrowInput(ThrowInputC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                // Clear wall slide before throw so the action is IDLE for GestaltThrowEvents
                PlayerGestaltState wsState = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                if (wsState.isWallSliding()) {
                    serverPlayer.setNoGravity(false);
                    wsState.clearWallSlide();
                    serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), wsState);
                    syncWallSlideToTracking(serverPlayer, false, null);
                }
                GestaltThrowEvents.handleThrowInput(serverPlayer);
            }
        });
    }

    private static void handleStartChannelXp(StartChannelXpC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltXpChannelEvents.handleStart(serverPlayer);
            }
        });
    }

    private static void handleStopChannelXp(StopChannelXpC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltXpChannelEvents.handleStop(serverPlayer);
            }
        });
    }

    private static void handleSyncChannelState(SyncChannelStateS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setChannelingXp(packet.active());
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleSelectSkin(SelectGestaltSkinC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isAwakened()) return;
            net.minecraft.resources.ResourceLocation skinId = packet.skinId();
            net.ragdot.gestaltresonance.common.skin.GestaltSkin skin =
                    net.ragdot.gestaltresonance.common.skin.GestaltSkinRegistry.getSkin(state.getGestaltId(), skinId);
            if (skin == null) return;
            // Default skins are always valid; non-defaults must be unlocked.
            if (!skin.isDefault() && !state.isSkinUnlocked(skinId)) return;
            state.setSelectedSkin(skinId);
            serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            syncSelectedSkinToTracking(serverPlayer);
        });
    }

    private static void handleSyncSelectedSkin(SyncSelectedSkinS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setSelectedSkin(packet.skinId());
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleSyncUnlockedSkins(SyncUnlockedSkinsS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setUnlockedSkins(new java.util.HashSet<>(packet.unlockedSkins()));
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleSkinUnlockedToast(SkinUnlockedToastS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // The actual toast is shown via a client-side hook so this class stays common-side.
            net.ragdot.gestaltresonance.client.gui.SkinUnlockToastQueue.queue(packet.skinId());
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
            if (newAction == GestaltAction.THROW && targetPlayer != player) {
                state.setThrowOrigin(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), targetPlayer.yBodyRot);
            }
            // Clear charged-strike data when transitioning to a non-charge state. HIT_3 is
            // intentionally allowed to keep the data so the strike renders at the target.
            if (newAction != GestaltAction.CHARGED_STRIKE_WINDUP
                    && newAction != GestaltAction.CHARGED_STRIKE_TRAVEL
                    && newAction != GestaltAction.HIT_3) {
                state.clearChargedStrikeData();
            }
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

    /** Client-side callback: spawn gust particles at the given impact position. */
    public static java.util.function.Consumer<SpawnHitParticlesS2C> onHitParticlesCallback = null;

    private static void handleSpawnHitParticles(SpawnHitParticlesS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (onHitParticlesCallback != null) {
                onHitParticlesCallback.accept(packet);
            }
        });
    }

    private static void handleToggle(ToggleSummonC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer serverPlayer) {
                PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                boolean wasSummoned = state.isSummoned();
                if (wasSummoned && (state.isPhaseCourtActive() || state.isTimePhaseActive())) {
                    serverPlayer.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                    return;
                }
                if (wasSummoned) {
                    GestaltAttackEvents.cancelChain(serverPlayer);
                    GestaltThrowEvents.cancelThrow(serverPlayer);
                    state.setAction(GestaltAction.IDLE);
                }
                state.toggleSummon();

                // Block re-summon during crash cooldown or while hunger is still too low
                if (!wasSummoned && state.isSummoned()) {
                    long currentTick = serverPlayer.getServer().getTickCount();
                    if (state.hasCrashCooldown(currentTick)) {
                        state.setSummoned(false);
                        serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                        serverPlayer.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                        serverPlayer.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("Your gestalt is still recovering."), true);
                        return;
                    }
                    if (serverPlayer.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) {
                        state.setSummoned(false);
                        serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                        serverPlayer.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                        return;
                    }
                    // Amen Break cannot be summoned while a cat is within 4 blocks.
                    if (GestaltIds.AMEN_BREAK.equals(state.getGestaltId())
                            && AmenBreakPassive.hasCatNearby(serverPlayer)) {
                        state.setSummoned(false);
                        serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                        serverPlayer.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
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
                    serverPlayer.level().playSound(serverPlayer, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            GestaltSounds.GESTALT_SUMMON.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                } else if (!state.isSummoned() && wasSummoned) {
                    serverPlayer.playNotifySound(GestaltSounds.GESTALT_DISMISS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                    serverPlayer.level().playSound(serverPlayer, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            GestaltSounds.GESTALT_DISMISS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
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
                boolean wasSummoned = state.isSummoned();
                state.setSummoned(packet.summoned());
                state.setGestaltId(packet.gestaltId());
                targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                if (wasSummoned && !packet.summoned() && onGestaltUnsummonCallback != null) {
                    onGestaltUnsummonCallback.accept(targetPlayer.getUUID());
                }
            }
        });
    }

    /** Client-side callback invoked when any player's gestalt transitions from summoned to unsummoned. */
    public static java.util.function.Consumer<java.util.UUID> onGestaltUnsummonCallback = null;

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

            // Power windups cannot be interrupted by guard — guard dropped at activation.
            if (state.getAction() == GestaltAction.POWER_1G_WINDUP) return;
            // Wind-up is interrupted only by left-click release (handled by ReleaseChargedStrikeC2S).
            if (state.getAction() == GestaltAction.CHARGED_STRIKE_WINDUP) return;
            // Active charged-strike strike phase (HIT_3 with target id) is not cancellable.
            if (state.getAction() == GestaltAction.HIT_3
                    && state.getChargedStrikeTargetEntityId() >= 0) return;
            // Fresh right-click during charged-strike travel cancels the strike and proceeds to guard.
            if (state.getAction() == GestaltAction.CHARGED_STRIKE_TRAVEL) {
                GestaltChargedStrikeEvents.handleCancelByGuard(serverPlayer);
                state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            }
            // Guard takes priority over an in-progress hit chain — drop the chain on entry.
            if (state.getAction() == GestaltAction.HIT_1
                    || state.getAction() == GestaltAction.HIT_2
                    || state.getAction() == GestaltAction.HIT_3) {
                GestaltAttackEvents.cancelChainSilently(serverPlayer);
            }

            state.startGuard();
            state.setGuardActivatedTick(serverPlayer.getServer().getTickCount());
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
                // Cover the cancel-during-travel path: leftover charge data from TRAVEL would
                // otherwise keep the renderer treating future HIT_3s as charged strikes.
                state.clearChargedStrikeData();
                targetPlayer.playSound(SoundEvents.WOLF_ARMOR_REPAIR, 0.8f, 1.1f);
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
            if (target == player && onSelfCrashCallback != null) {
                onSelfCrashCallback.run();
            }
        });
    }

    /** Client-side callback invoked when the local player's own gestalt crashes. */
    public static Runnable onSelfCrashCallback = null;

    /**
     * Client-side callback invoked on a fall break impact for the given player UUID.
     * The client mod registers this in setup to forward to the renderer's shake state.
     */
    public static java.util.function.Consumer<java.util.UUID> onFallBreakImpactCallback = null;

    private static void handleFallBreakImpact(FallBreakImpactS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            if (onFallBreakImpactCallback != null) {
                onFallBreakImpactCallback.accept(targetPlayer.getUUID());
            }
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

    /** Sync the player's currently selected skin to all tracking clients (and self). */
    public static void syncSelectedSkinToTracking(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        SyncSelectedSkinS2C packet = new SyncSelectedSkinS2C(serverPlayer.getId(), state.getSelectedSkin());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Sync the player's full unlocked skin set to themselves only. */
    public static void syncUnlockedSkinsToOwner(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        SyncUnlockedSkinsS2C packet = new SyncUnlockedSkinsS2C(new java.util.ArrayList<>(state.getUnlockedSkins()));
        PacketDistributor.sendToPlayer(serverPlayer, packet);
    }

    /** Push a single skin-unlocked toast to the owning player. */
    public static void sendSkinUnlockedToast(ServerPlayer serverPlayer, net.minecraft.resources.ResourceLocation skinId) {
        PacketDistributor.sendToPlayer(serverPlayer, new SkinUnlockedToastS2C(skinId));
    }

    /** Notify all tracking clients that a player's XP channel started or stopped. */
    public static void syncChannelStateToTracking(ServerPlayer serverPlayer, boolean active, boolean broken) {
        SyncChannelStateS2C packet = new SyncChannelStateS2C(serverPlayer.getId(), active, broken);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Notify all tracking clients that a player's gestalt crashed out (hunger). */
    public static void syncCrashToTracking(ServerPlayer serverPlayer) {
        TriggerGestaltCrashS2C packet = new TriggerGestaltCrashS2C(serverPlayer.getId());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Notify all tracking clients (and self) that a player's gestalt just fall-broke a landing. */
    public static void broadcastFallBreakImpact(ServerPlayer serverPlayer) {
        FallBreakImpactS2C packet = new FallBreakImpactS2C(serverPlayer.getId());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    /** Send the current gestalt state of a player to all tracking clients + the player. */
    public static void syncToTracking(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        SyncGestaltStateS2C packet = new SyncGestaltStateS2C(
                serverPlayer.getId(), state.isSummoned(), state.getGestaltId());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    private static void handleSyncWallSlide(SyncWallSlideS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (packet.sliding() && packet.wallFace() != null) {
                state.startWallSlide(packet.wallFace());
            } else {
                state.clearWallSlide();
            }
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    /** Send wall slide state to all tracking clients + the player. */
    public static void syncWallSlideToTracking(ServerPlayer serverPlayer, boolean sliding, @Nullable Direction face) {
        int faceIndex = face != null ? face.ordinal() : -1;
        SyncWallSlideS2C packet = new SyncWallSlideS2C(serverPlayer.getId(), sliding, faceIndex);
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

    // ── Charged strike ──────────────────────────────────────────────────────

    private static void handleStartChargedStrike(StartChargedStrikeC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltChargedStrikeEvents.handleStart(serverPlayer);
            }
        });
    }

    private static void handleReleaseChargedStrike(ReleaseChargedStrikeC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltChargedStrikeEvents.handleRelease(serverPlayer, packet.targetEntityId());
            }
        });
    }

    private static void handleSyncChargedStrikeTravel(SyncChargedStrikeTravelS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.playerEntityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setChargedStrikeLaunch(packet.launchX(), packet.launchY(), packet.launchZ());
            state.setChargedStrikeTargetEntityId(packet.targetEntityId());
            state.setChargedStrikeSpeedTier(packet.speedTier());
            state.setChargedStrikeTraveled(0.0);
            // targetDistance is recomputed client-side each frame from the live target entity position.
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    // ── Mining sync ─────────────────────────────────────────────────────────

    private static void handleSyncMiningStateC2S(SyncMiningStateC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isMining() == packet.mining()) return;
            state.setMining(packet.mining());
            serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            // Send to tracking clients only — local client already knows.
            SyncMiningStateS2C s2c = new SyncMiningStateS2C(serverPlayer.getId(), packet.mining());
            PacketDistributor.sendToPlayersTrackingEntity(serverPlayer, s2c);
        });
    }

    private static void handleSyncMiningStateS2C(SyncMiningStateS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setMining(packet.mining());
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleSyncResonance(SyncResonanceS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setResonanceValue(packet.resonanceValue());
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    private static void handleSyncCooldown(SyncCooldownS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.ragdot.gestaltresonance.client.GestaltCooldownHud.onCooldownReceived(packet.totalTicks());
        });
    }

    /** Notify the owning player's client that a shared cooldown just started. */
    public static void syncCooldownToPlayer(ServerPlayer serverPlayer, int totalTicks) {
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncCooldownS2C(serverPlayer.getId(), totalTicks));
    }

    private static void handleSyncPowerCooldown(SyncPowerCooldownS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                net.ragdot.gestaltresonance.client.PowerCooldownClient.set(packet.index(), packet.totalTicks()));
    }

    /** Notify the owning player's client that a per-slot power cooldown just started. */
    public static void syncPowerCooldown(ServerPlayer serverPlayer, int index, int totalTicks) {
        PacketDistributor.sendToPlayer(serverPlayer, new SyncPowerCooldownS2C(index, totalTicks));
    }

    /** Send the current resonance value to the owning player's client. */
    public static void syncResonanceToPlayer(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncResonanceS2C(serverPlayer.getId(), state.getResonanceValue()));
    }

    /** Spawn gust particles at an impact position for all clients tracking the attacker. */
    public static void broadcastHitParticles(ServerPlayer serverPlayer, float x, float y, float z, byte hitNumber) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                new SpawnHitParticlesS2C(x, y, z, hitNumber));
    }

    /** Broadcast the charged-strike travel data to all tracking clients (and self). */
    public static void broadcastChargedStrikeTravel(ServerPlayer serverPlayer,
                                                    int targetEntityId,
                                                    double launchX, double launchY, double launchZ,
                                                    byte speedTier) {
        SyncChargedStrikeTravelS2C packet = new SyncChargedStrikeTravelS2C(
                serverPlayer.getId(), targetEntityId, launchX, launchY, launchZ, speedTier);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, packet);
    }

    // ── Soul projection ──────────────────────────────────────────────────────

    private static void handleSoulProjectionActivate(SoulProjectionActivateC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                GestaltSoulProjectionEvents.activateSoulProjection(serverPlayer);
            }
        });
    }

    private static void handleSoulProjectionExit(SoulProjectionExitC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isSoulProjecting()) {
                // State already cleared (race condition): still clean up any orphaned body double.
                net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity.dismissExistingDoubles(
                        serverPlayer.level(), serverPlayer.getUUID());
                return;
            }
            // Client may request CLEAN or EMERGENCY; treat anything else as EMERGENCY for safety.
            SoulProjectionExitType requested = SoulProjectionExitType.fromByte(packet.exitType());
            SoulProjectionExitType allowed = (requested == SoulProjectionExitType.CLEAN)
                    ? SoulProjectionExitType.CLEAN
                    : SoulProjectionExitType.EMERGENCY;
            GestaltSoulProjectionEvents.teardown(serverPlayer, allowed, null, 0f);
        });
    }

    private static void handleSoulProjectionAction(SoulProjectionActionC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            if (packet.action() == SoulProjectionActionC2S.ACTION_PICKUP) {
                GestaltSoulProjectionEvents.handlePickup(serverPlayer, packet.targetEntityId());
            }
        });
    }

    private static void handleSyncSoulProjection(SyncSoulProjectionS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = ctx.player();
            net.minecraft.world.entity.Entity target = player.level().getEntity(packet.entityId());
            if (!(target instanceof net.minecraft.world.entity.player.Player targetPlayer)) return;
            PlayerGestaltState state = targetPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            boolean wasProjecting = state.isSoulProjecting();
            state.setSoulProjecting(packet.projecting());
            state.setBodyDoubleEntityId(packet.bodyDoubleEntityId());
            if (packet.projecting()) {
                state.setSoulProjectionAnchor(packet.anchor());
                state.setSoulProjectionMaxRange((float) net.ragdot.gestaltresonance.common.GestaltCosts.soulProjectionRangeFor(state));
            } else {
                state.setSoulProjectionAnchor(null);
                state.setSoulProjectionMaxRange(0f);
            }
            targetPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            // Fire callback for the owning player when projection state changes
            if (targetPlayer == player && wasProjecting != packet.projecting()
                    && onSoulProjectionStateCallback != null) {
                onSoulProjectionStateCallback.accept(packet.projecting());
            }
        });
    }

    private static void handleSoulProjectionYank(SoulProjectionYankS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (onSoulProjectionYankCallback != null) {
                onSoulProjectionYankCallback.accept(packet);
            }
        });
    }

    /** Client-side callback invoked when the owning player gets yanked back from soul projection. */
    public static java.util.function.Consumer<SoulProjectionYankS2C> onSoulProjectionYankCallback = null;

    /** Client-side callback invoked when the owning player's soul projection state changes. */
    public static java.util.function.Consumer<Boolean> onSoulProjectionStateCallback = null;

    /** Sync soul projection state to all tracking clients and self. */
    public static void syncSoulProjectionToTracking(ServerPlayer serverPlayer, boolean projecting,
                                                    int bodyDoubleEntityId, @Nullable Vec3 anchor) {
        Vec3 a = anchor != null ? anchor : Vec3.ZERO;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                new SyncSoulProjectionS2C(serverPlayer.getId(), projecting, bodyDoubleEntityId,
                        a.x, a.y, a.z));
    }

    // ── Power activation ─────────────────────────────────────────────────────

    private static void handlePowerActivate(PowerActivateC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isSummoned()) return;

            GestaltPowerSlot slot = GestaltPowerSlot.fromByte(packet.slot());

            // Determine chord modifier from current server-side state (never trusted from client).
            GestaltPowerModifier modifier;
            if (state.isGuarding()) {
                modifier = GestaltPowerModifier.GUARD;
            } else if (serverPlayer.isShiftKeyDown()) {
                modifier = GestaltPowerModifier.SNEAK;
            } else {
                modifier = GestaltPowerModifier.NONE;
            }

            GestaltResonance.LOGGER.debug("Power activate: {} slot={} modifier={} gestalt={}",
                    serverPlayer.getName().getString(), slot, modifier, state.getGestaltId());
            GestaltPowerRegistry.tryActivate(serverPlayer,
                    new GestaltPowerKey(state.getGestaltId(), slot, modifier));
        });
    }

    /** Send a yank-back notification to the owning player's client (with exit type, damage, snap pos). */
    public static void sendSoulProjectionYank(ServerPlayer serverPlayer, SoulProjectionExitType exitType,
                                              float damageAmount, Vec3 snapPos) {
        PacketDistributor.sendToPlayer(serverPlayer,
                new SoulProjectionYankS2C(serverPlayer.getId(), exitType.toByte(), damageAmount,
                        snapPos.x, snapPos.y, snapPos.z));
    }

    // ── Phase Out (Power 2G) ─────────────────────────────────────────────────

    private static void handlePhaseOutToggle(PhaseOutToggleC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                AmenBreakPower2G.toggle(serverPlayer);
            }
        });
    }

    private static void handlePhaseOutStateSync(PhaseOutStateSyncS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = ctx.player();
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setPhaseOutArmed(packet.armed());
            state.setPhaseOutActive(packet.active());
            state.setPhaseOutCooldownTicks(packet.cooldownTicks());
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            // 2G (POWER_2, GUARD): slot=1, modifier=1 → 1×3+1 = 4
            net.ragdot.gestaltresonance.client.PowerCooldownClient.setFromRemaining(
                    4, packet.cooldownTicks(), GestaltCosts.PHASE_OUT_COOLDOWN_TICKS);
            if (onPhaseOutStateCallback != null) {
                onPhaseOutStateCallback.accept(packet);
            }
        });
    }

    /** Client-side callback invoked when Phase Out state changes. */
    public static java.util.function.Consumer<PhaseOutStateSyncS2C> onPhaseOutStateCallback = null;

    // ── Moist Air - Spillways (Power 2G) ─────────────────────────────────────

    private static void handleMoistAirToggle(MoistAirToggleC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                SpillwaysPower2G.toggle(serverPlayer);
            }
        });
    }

    private static void handleMoistAirStateSync(MoistAirStateSyncS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = ctx.player();
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setMoistAirActive(packet.active());
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            net.ragdot.gestaltresonance.client.GestaltResonanceHud.onMoistAirState(packet);
            if (onMoistAirStateCallback != null) onMoistAirStateCallback.accept(packet);
        });
    }

    /** Client-side callback invoked when Moist Air state changes. */
    public static java.util.function.Consumer<MoistAirStateSyncS2C> onMoistAirStateCallback = null;

    // ── Phase Court (Power 3G) ────────────────────────────────────────────────

    public static java.util.function.Consumer<SyncPhaseCourtS2C> onPhaseCourtStateCallback = null;

    private static void handleSyncPhaseCourt(SyncPhaseCourtS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = ctx.player();
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setPhaseCourtActive(packet.active());
            state.setPhaseCourtTicksRemaining(packet.ticksRemaining());
            state.setBreakCoreUsed(packet.breakCoreUsed());
            state.setPhaseCourtCooldownTicks(packet.cooldownTicks());
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            // 3G (POWER_3, GUARD): slot=2, modifier=1 → 2×3+1 = 7
            net.ragdot.gestaltresonance.client.PowerCooldownClient.setFromRemaining(
                    7, packet.cooldownTicks(), GestaltCosts.PHASE_COURT_COOLDOWN_TICKS);
            if (onPhaseCourtStateCallback != null) {
                onPhaseCourtStateCallback.accept(packet);
            }
        });
    }

    /** Send the current Phase Court state to the owning player's client. */
    public static void syncPhaseCourtToPlayer(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncPhaseCourtS2C(
                        state.isPhaseCourtActive(),
                        state.getPhaseCourtTicksRemaining(),
                        state.isBreakCoreUsed(),
                        state.getPhaseCourtCooldownTicks(),
                        state.getPhaseCourtPostHitTick() > 0));
        syncPlayerGhostToTracking(serverPlayer);
    }

    // ── Time Phase (Power 3S) ─────────────────────────────────────────────────

    private static void handleSyncTimePhase(SyncTimePhaseS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = ctx.player();
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setTimePhaseActive(packet.active());
            state.setTimePhasePredictionPhase(packet.predictionPhase());
            state.setTimePhaseTicksRemaining(packet.ticksRemaining());
            state.setTimePhaseCooldownTicks(packet.cooldownTicks());
            // 3S (POWER_3, SNEAK): slot=2, modifier=2 → 2×3+2 = 8
            net.ragdot.gestaltresonance.client.PowerCooldownClient.setFromRemaining(
                    8, packet.cooldownTicks(), GestaltCosts.TIME_PHASE_COOLDOWN_TICKS);
            state.setTimePhaseBodyDoubleId(packet.bodyDoubleEntityId());
            state.setTimePhaseTrackedCount(packet.trackedCount());
            int[] ids = state.getTimePhaseTrackedIds();
            int[] packetIds = packet.trackedEntityIds();
            System.arraycopy(packetIds, 0, ids, 0, Math.min(packetIds.length, ids.length));
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    /** Send the current Time Phase state to the owning player's client. */
    public static void syncTimePhaseToPlayer(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        int[] ids = new int[GestaltCosts.TIME_PHASE_MAX_ENTITIES];
        int[] tracked = state.getTimePhaseTrackedIds();
        System.arraycopy(tracked, 0, ids, 0, ids.length);
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncTimePhaseS2C(
                        state.isTimePhaseActive(),
                        state.isTimePhasePredictionPhase(),
                        state.getTimePhaseTicksRemaining(),
                        state.getTimePhaseCooldownTicks(),
                        state.getTimePhaseBodyDoubleId(),
                        state.getTimePhaseTrackedCount(),
                        ids));
        syncPlayerGhostToTracking(serverPlayer);
    }

    /**
     * Broadcast ghost/translucency state to all clients tracking this player.
     * Lets other players see Phase Court and Time Phase opacity effects.
     */
    public static void syncPlayerGhostToTracking(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                new SyncPlayerGhostS2C(
                        serverPlayer.getId(),
                        state.isPhaseCourtActive(),
                        state.isTimePhaseActive()));
    }

    private static void handleSyncPlayerGhost(SyncPlayerGhostS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            net.minecraft.world.entity.Entity entity = mc.level.getEntity(packet.entityId());
            if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return;
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setPhaseCourtActive(packet.phaseCourtActive());
            state.setTimePhaseActive(packet.timePhaseActive());
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        });
    }

    // ── Client-only afterimages (Time Phase + Phase Court) ───────────────────

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_AFTERIMAGE_ID =
            new java.util.concurrent.atomic.AtomicInteger(1);

    /** Allocate a server-side id for a new afterimage. Unique per server lifetime. */
    public static int nextAfterimageId() {
        return NEXT_AFTERIMAGE_ID.getAndIncrement();
    }

    /** Spawn a client-only afterimage for the given player. fadeRate=0 → persistent. tint is packed RGB. */
    public static void sendSpawnAfterimage(ServerPlayer serverPlayer, int id,
                                           double x, double y, double z,
                                           int sourceEntityId,
                                           float opacity, float fadeRate, int tint) {
        PacketDistributor.sendToPlayer(serverPlayer,
                new SpawnAfterimageS2C(id, x, y, z, sourceEntityId, opacity, fadeRate, tint));
    }

    /** Remove a single afterimage from the player's client by id. */
    public static void sendDiscardAfterimage(ServerPlayer serverPlayer, int id) {
        PacketDistributor.sendToPlayer(serverPlayer, new DiscardAfterimageS2C(id));
    }

    /** Wipe all client-side afterimages on the given player. */
    public static void sendClearAfterimages(ServerPlayer serverPlayer) {
        PacketDistributor.sendToPlayer(serverPlayer, new ClearAfterimagesS2C());
    }

    private static void handleSpawnAfterimage(SpawnAfterimageS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (onSpawnAfterimageCallback != null) onSpawnAfterimageCallback.accept(packet);
        });
    }

    private static void handleDiscardAfterimage(DiscardAfterimageS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (onDiscardAfterimageCallback != null) onDiscardAfterimageCallback.accept(packet);
        });
    }

    private static void handleClearAfterimages(ClearAfterimagesS2C packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (onClearAfterimagesCallback != null) onClearAfterimagesCallback.run();
        });
    }

    /** Client-side callback: forward SpawnAfterimage to ClientAfterimageManager. */
    public static java.util.function.Consumer<SpawnAfterimageS2C> onSpawnAfterimageCallback = null;
    /** Client-side callback: forward DiscardAfterimage to ClientAfterimageManager. */
    public static java.util.function.Consumer<DiscardAfterimageS2C> onDiscardAfterimageCallback = null;
    /** Client-side callback: clear all client-side afterimages. */
    public static Runnable onClearAfterimagesCallback = null;

    // ── Dusty Documents (writable file) ─────────────────────────────────────

    private static void handleSaveDustyDoc(SaveDustyDocC2S packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            int slot = packet.slot();
            if (slot < 0 || slot > 40) return;
            net.minecraft.world.item.ItemStack stack = serverPlayer.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof net.ragdot.gestaltresonance.common.item.DustyDocumentsWritableItem)) return;

            var pageObjects = packet.pages().stream()
                    .map(net.minecraft.server.network.Filterable::passThrough)
                    .collect(java.util.stream.Collectors.toList());
            stack.set(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT,
                    new net.minecraft.world.item.component.WritableBookContent(pageObjects));

            packet.title().ifPresentOrElse(
                    title -> stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal(title)),
                    () -> stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        });
    }

    /** Send the current Phase Out state to the owning player's client. */
    public static void syncPhaseOutToPlayer(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        int resonance = Math.max(0, state.getResonanceValue());
        boolean canAfford = resonance + state.getTotalGestaltXp() >= GestaltCosts.PHASE_OUT_COST_TOTAL;
        PacketDistributor.sendToPlayer(serverPlayer,
                new PhaseOutStateSyncS2C(
                        state.isPhaseOutArmed(),
                        state.isPhaseOutActive(),
                        state.getPhaseOutCooldownTicks(),
                        canAfford));
    }

    /** Send the current Moist Air state to the owning player's client. */
    public static void syncMoistAirToPlayer(ServerPlayer serverPlayer) {
        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        PacketDistributor.sendToPlayer(serverPlayer, new MoistAirStateSyncS2C(state.isMoistAirActive()));
    }
}
