package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.common.block.PopSproutBlock;
import net.ragdot.gestaltresonance.common.block.PopSproutBlockEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.PhaseBlossomZoneTracker;
import net.ragdot.gestaltresonance.common.PopSproutTracker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives resonance gains, dissonance losses, and decay each server tick.
 *
 * Resonance gains and dissonance losses from specific abilities are also called
 * directly from those ability handlers via {@link #applyResonance}.
 */
public class GestaltResonanceEvents {

    // Per-player kill timestamps for multi-kill detection
    private static final Map<UUID, Deque<Long>> recentKillsMap = new HashMap<>();

    // Pending overkill flag: set during LivingIncomingDamageEvent when gestalt damage would kill
    private static final Map<UUID, Boolean> pendingOverkill = new HashMap<>();

    // Fall-break kill tracking: entity -> attacking player, plus hit tick for cleanup
    private static final Map<UUID, UUID> fallBreakDamagedBy = new HashMap<>();
    private static final Map<UUID, Long> fallBreakHitTick   = new HashMap<>();

    // Grace period: suppress decay for 5 ticks after any resonance gain
    private static final Map<UUID, Long> lastGainTick = new HashMap<>();
    private static final int GAIN_GRACE_TICKS = 5;

    // -------------------------------------------------------------------------
    // Public static helper: apply a resonance change, handle crash, sync to client
    // -------------------------------------------------------------------------

    /**
     * Apply a resonance delta to a player. Positive = gain (tier multiplier applied),
     * negative = dissonance loss (flat). Triggers crash if dissonance cap is hit.
     * Caller must ensure player has an awakened gestalt.
     */
    public static void applyResonance(ServerPlayer player, int baseAmount) {
        if (baseAmount == 0) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isAwakened()) return;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return;

        if (baseAmount > 0) {
            state.addResonance(baseAmount, stats);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            recordResonanceGain(player.getUUID(), player.getServer().getTickCount());
            GestaltNetworking.syncResonanceToPlayer(player);
        } else {
            boolean crashed = state.addDissonance(-baseAmount, stats);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            if (crashed && state.isSummoned()) {
                // crashGestalt calls setData internally, so sync after it returns
                GestaltAcquisitionEvents.crashGestalt(player);
            }
            GestaltNetworking.syncResonanceToPlayer(player);
        }
    }

    // -------------------------------------------------------------------------
    // Decay (server tick)
    // -------------------------------------------------------------------------

    static void recordResonanceGain(UUID playerUUID, long tick) {
        lastGainTick.put(playerUUID, tick);
    }

    public static void trackFallBreakHit(UUID entityId, UUID playerId, long tick) {
        fallBreakDamagedBy.put(entityId, playerId);
        fallBreakHitTick.put(entityId, tick);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();

        // Clean up stale fall-break hit entries (entity survived or kill already processed)
        fallBreakHitTick.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 10) {
                fallBreakDamagedBy.remove(entry.getKey());
                return true;
            }
            return false;
        });

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!state.isAwakened() || state.getResonanceValue() == 0) continue;

            GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
            if (stats == null) continue;

            int interval;
            int rate;

            if (!state.isSummoned()) {
                interval = GestaltCosts.DECAY_UNSUMMONED_INTERVAL;
                rate = GestaltCosts.DECAY_UNSUMMONED_RATE;
            } else {
                if (now % 10 == 0 && hasHostileMobNearby(player)) {
                    state.setLastHostileMobNearbyTick(now);
                    player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                }
                long timeSinceHostile = now - state.getLastHostileMobNearbyTick();
                boolean inHostileContext = state.getLastHostileMobNearbyTick() >= 0
                        && timeSinceHostile <= GestaltCosts.DECAY_HOSTILE_GRACE_TICKS;

                interval = inHostileContext
                        ? GestaltCosts.DECAY_SUMMONED_HOSTILE_INTERVAL
                        : GestaltCosts.DECAY_SUMMONED_NO_HOSTILE_INTERVAL;
                rate = 1;
            }

            long lastGain = lastGainTick.getOrDefault(player.getUUID(), -1L);
            if (now - lastGain < GAIN_GRACE_TICKS) continue;

            if (now % interval == 0) {
                state.decayResonance(rate);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncResonanceToPlayer(player);
            }
        }
    }

    private static boolean hasHostileMobNearby(ServerPlayer player) {
        double r = GestaltCosts.DECAY_HOSTILE_DETECTION_RADIUS;
        return !player.level().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(r), e -> true).isEmpty();
    }

    // -------------------------------------------------------------------------
    // PvP mitigation: players receive only 60% of gestalt damage
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerGestaltDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (event.getSource().is(GestaltDamageTypes.GESTALT)) {
            event.setAmount(event.getAmount() * 0.6f);
        }
    }

    // -------------------------------------------------------------------------
    // Wolf protection: gestalt attacks never hurt dogs/wolves
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onWolfProtection(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.animal.Wolf)) return;
        if (event.getSource().is(GestaltDamageTypes.GESTALT)) {
            event.setCanceled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Overkill detection (fires before death)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPreKillDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer) return; // handled separately
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().typeHolder().is(GestaltDamageTypes.GESTALT)) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isAwakened()) return;

        if (event.getAmount() > event.getEntity().getHealth()) {
            pendingOverkill.put(player.getUUID(), true);
        }
    }

    // -------------------------------------------------------------------------
    // Kill resonance
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().typeHolder().is(GestaltDamageTypes.GESTALT)) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isAwakened()) return;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return;

        LivingEntity killed = event.getEntity();
        boolean isHostile = killed instanceof Monster;
        long now = player.getServer().getTickCount();
        UUID uuid = player.getUUID();

        int totalGain = GestaltCosts.GAIN_KILL;

        // Overkill bonus (hostile mobs only)
        if (Boolean.TRUE.equals(pendingOverkill.remove(uuid)) && isHostile) {
            totalGain += GestaltCosts.GAIN_OVERKILL_HOSTILE;
        }

        // Fall break into kill: only when this entity was directly hit by fall-break-transferred damage
        if (player.getUUID().equals(fallBreakDamagedBy.remove(killed.getUUID()))) {
            fallBreakHitTick.remove(killed.getUUID());
            totalGain += GestaltCosts.GAIN_FALL_BREAK_KILL;
        }

        // Multi-kill: award bonus on the 2nd+ kill within the window
        Deque<Long> kills = recentKillsMap.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        while (!kills.isEmpty() && now - kills.peekFirst() > GestaltCosts.GAIN_MULTI_KILL_WINDOW) {
            kills.pollFirst();
        }
        kills.addLast(now);
        if (kills.size() >= 2) {
            totalGain += GestaltCosts.GAIN_MULTI_KILL;
        }

        state.addResonance(totalGain, stats);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        recordResonanceGain(uuid, now);
        GestaltNetworking.syncResonanceToPlayer(player);
    }

    // -------------------------------------------------------------------------
    // PopSprout tracker cleanup on manual block break
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPopSproutBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof PopSproutBlock)) return;
        net.minecraft.world.level.LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor.isClientSide()) return;
        BlockPos pos = event.getPos();
        if (levelAccessor.getBlockEntity(pos) instanceof PopSproutBlockEntity be) {
            UUID ownerUuid = be.getOwnerUuid();
            if (ownerUuid != null && levelAccessor instanceof ServerLevel serverLevel) {
                PopSproutTracker.get(serverLevel.getServer()).removeSprout(ownerUuid, pos);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dissonance from player taking damage while summoned
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerDamaged(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getEntity() == null) return; // ignore environmental damage
        if (event.getAmount() <= 0f) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned() || !state.isAwakened()) return;

        GestaltAction action = state.getAction();
        // Guard hits are handled by GestaltGuardEvents
        if (action == GestaltAction.GUARD) return;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return;

        int dissonance;
        if (action == GestaltAction.CHARGED_STRIKE_TRAVEL) {
            dissonance = GestaltCosts.LOSS_HIT_CHARGED_TRAVEL;
        } else {
            dissonance = GestaltCosts.LOSS_DAMAGE_WHILE_SUMMONED;
            if (action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2
                    || action == GestaltAction.HIT_3) {
                dissonance += GestaltCosts.LOSS_HIT_MID_COMBO;
            }
        }

        // Near-death: approximate post-hit health using raw incoming damage
        float projectedHealth = player.getHealth() - event.getAmount();
        if (projectedHealth < GestaltCosts.LOSS_NEAR_DEATH_THRESHOLD) {
            dissonance += GestaltCosts.LOSS_NEAR_DEATH;
        }

        boolean crashed = state.addDissonance(dissonance, stats);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        if (crashed && state.isSummoned()) {
            GestaltAcquisitionEvents.crashGestalt(player);
        }
        GestaltNetworking.syncResonanceToPlayer(player);
    }

    @SubscribeEvent
    public void onSuppressPhaseZoneWall(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.IN_WALL)) return;
        LivingEntity entity = event.getEntity();
        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        if (PhaseBlossomZoneTracker.isPhased(entity.level(), eyePos)) {
            event.setCanceled(true);
        }
    }

}
