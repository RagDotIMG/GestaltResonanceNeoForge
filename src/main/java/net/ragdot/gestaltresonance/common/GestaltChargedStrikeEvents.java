package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side state machine for the charged-strike ability.
 *
 * Phases:
 *   WINDUP — set on StartChargedStrikeC2S; tick counts up. Release before WINDUP_TICKS = abort.
 *   TRAVEL — gestalt homes on target each server tick at SPD-keyed speed.
 *   STRIKE — action becomes HIT_3; the existing GestaltAttackEvents chain pipeline delivers damage.
 *
 * Cooldowns are written into the existing {@link GestaltAttackEvents} cooldown map
 * so charged strike and the standard hit chain share the same lockout.
 */
public class GestaltChargedStrikeEvents {

    /** Per-player runtime data; entries exist only while a charged strike is active. */
    private static final Map<UUID, Data> dataMap = new HashMap<>();

    /** Phase enum — stored on Data; not the same as GestaltAction. */
    private static final int PHASE_WINDUP = 0;
    private static final int PHASE_TRAVEL = 1;
    /** Strike has fired; entry stays in dataMap so applyDamage can read targetEntityId. */
    private static final int PHASE_STRIKE_PENDING = 2;

    private static class Data {
        int phase;
        int tick;
        int targetEntityId;
        double launchX, launchY, launchZ;
        int speedTier;
        double targetDistance;
        double traveled;
        /** True between strike trigger and HIT_3 damage tick — read+cleared by applyDamage. */
        boolean strikePending;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Begin the wind-up phase. Called from StartChargedStrikeC2S handler. Only valid entry is GUARD. */
    public static void handleStart(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;
        if (state.getAction() != GestaltAction.GUARD) return;

        long now = player.getServer().getTickCount();
        if (GestaltAttackEvents.isOnSharedCooldown(player.getUUID(), now)) return;

        // Drop guard immediately (no protection during windup).
        state.clearGuard();

        Data d = new Data();
        d.phase = PHASE_WINDUP;
        d.tick = 0;
        dataMap.put(player.getUUID(), d);

        state.setAction(GestaltAction.CHARGED_STRIKE_WINDUP);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncGuardToTracking(player, false);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.CHARGED_STRIKE_WINDUP);
    }

    /** Resolve a release. Called from ReleaseChargedStrikeC2S handler with the client-picked target id. */
    public static void handleRelease(ServerPlayer player, int targetEntityId) {
        UUID uuid = player.getUUID();
        Data d = dataMap.get(uuid);
        if (d == null || d.phase != PHASE_WINDUP) return;

        // Abort: release before windup completes — no cooldown.
        if (d.tick < GestaltCosts.CHARGED_STRIKE_WINDUP_TICKS) {
            clearAndIdle(player, uuid);
            return;
        }

        // Validate target server-side.
        LivingEntity target = resolveTarget(player, targetEntityId);
        int range = chargedStrikeRange(player);
        if (target == null || !target.isAlive()
                || player.distanceTo(target) > range) {
            // Whiff: no valid target on fire — no cooldown, but apply dissonance.
            clearAndIdle(player, uuid);
            GestaltResonanceEvents.applyResonance(player, -GestaltCosts.LOSS_CHARGED_WHIFF);
            return;
        }

        // Fire — transition to TRAVEL or skip directly to STRIKE on SPD 5.
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int speedTier = (stats != null) ? Math.max(1, Math.min(5, stats.speed())) : 1;

        d.targetEntityId = target.getId();
        d.launchX = player.getX();
        d.launchY = player.getY();
        d.launchZ = player.getZ();
        d.speedTier = speedTier;
        d.targetDistance = player.position().distanceTo(target.position());
        d.traveled = 0.0;

        // Stash on state so renderers can read it.
        state.setChargedStrikeLaunch(d.launchX, d.launchY, d.launchZ);
        state.setChargedStrikeTargetEntityId(d.targetEntityId);
        state.setChargedStrikeSpeedTier(d.speedTier);
        state.setChargedStrikeTargetDistance(d.targetDistance);
        state.setChargedStrikeTraveled(0.0);

        if (speedTier == 5) {
            // Instant: skip travel — go straight to strike. Broadcast travel info so clients
            // know to render at the target (no prior SyncChargedStrikeTravelS2C was sent).
            triggerStrike(player, d, true);
            return;
        }

        d.phase = PHASE_TRAVEL;
        d.tick = 0;
        state.setAction(GestaltAction.CHARGED_STRIKE_TRAVEL);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.broadcastChargedStrikeTravel(
                player, d.targetEntityId, d.launchX, d.launchY, d.launchZ, (byte) d.speedTier);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.CHARGED_STRIKE_TRAVEL);
    }

    /**
     * Called from StartGuardC2S handler when the player's action is CHARGED_STRIKE_TRAVEL.
     * Cancels the travel, applies the cancel cooldown, and returns true so the guard handler
     * can proceed to start a fresh guard.
     */
    public static boolean handleCancelByGuard(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Data d = dataMap.remove(uuid);
        if (d == null) return false;
        long now = player.getServer().getTickCount();
        GestaltAttackEvents.setSharedCooldown(uuid, now + GestaltCosts.CHARGED_STRIKE_CANCEL_COOLDOWN_TICKS);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.CHARGED_STRIKE_CANCEL_COOLDOWN_TICKS);

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        state.clearChargedStrikeData();
        // Action will be set to GUARD by the caller after this returns true.
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        return true;
    }

    /** Hard cancel — clear all state without applying cooldown. Used by unsummon, crash, etc. */
    public static void cancelStrike(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (dataMap.remove(uuid) == null) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        state.clearChargedStrikeData();
        // Don't change action here — callers (cancelChain, crash) handle that.
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
    }

    /**
     * Called by GestaltAttackEvents.applyDamage during HIT_3. If a charged strike is pending
     * for this player, returns the target entity id (and clears the pending flag); otherwise -1.
     */
    public static int consumeStrikePending(UUID uuid) {
        Data d = dataMap.get(uuid);
        if (d == null || !d.strikePending) return -1;
        d.strikePending = false;
        int id = d.targetEntityId;
        // After the damage tick fires, the data is no longer needed.
        dataMap.remove(uuid);
        return id;
    }

    /** Range used for both the client-side crosshair indicator and server-side validation. */
    public static int chargedStrikeRange(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int rng = (stats != null) ? stats.range() : 0;
        return 1 + 2 * rng;
    }

    // -------------------------------------------------------------------------
    // Per-tick advancement
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        var iter = dataMap.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            UUID uuid = entry.getKey();
            Data d = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                iter.remove();
                continue;
            }

            d.tick++;
            if (d.phase == PHASE_WINDUP || d.phase == PHASE_STRIKE_PENDING) {
                // Windup waits for ReleaseChargedStrikeC2S; strike-pending waits for the
                // damage tick to consume the entry via consumeStrikePending. Nothing to do here.
                continue;
            }

            // PHASE_TRAVEL
            Entity raw = player.level().getEntity(d.targetEntityId);
            LivingEntity target = (raw instanceof LivingEntity le) ? le : null;
            if (target == null || !target.isAlive()) {
                // Whiff: target died mid-travel — 10-tick cooldown.
                iter.remove();
                GestaltAttackEvents.setSharedCooldown(uuid, now + GestaltCosts.CHARGED_STRIKE_DEATH_WHIFF_COOLDOWN_TICKS);
                GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.CHARGED_STRIKE_DEATH_WHIFF_COOLDOWN_TICKS);
                idleAndBroadcast(player);
                continue;
            }

            d.traveled += GestaltCosts.CHARGED_STRIKE_TRAVEL_SPEED_BY_SPD[d.speedTier];

            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setChargedStrikeTraveled(d.traveled);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

            // Have we reached the target? Check vs current target distance (homing).
            double currentDistance = player.position().distanceTo(target.position());
            if (d.traveled >= currentDistance) {
                // Strike: triggerStrike updates the phase in-place — entry stays in dataMap
                // for consumeStrikePending. No iter.remove() needed.
                triggerStrike(player, d, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Transition to the strike phase. Entry stays in dataMap (phase=STRIKE_PENDING) so the
     *  damage hook can read targetEntityId on HIT_3's damage tick. The TRAVEL/WINDUP loop
     *  must check phase to avoid retriggering. */
    private static void triggerStrike(ServerPlayer player, Data d, boolean broadcastTravelInfo) {
        d.phase = PHASE_STRIKE_PENDING;
        d.strikePending = true;
        dataMap.put(player.getUUID(), d);

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        state.setAction(GestaltAction.HIT_3);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltAttackEvents.startStrikeChainData(player.getUUID());

        if (broadcastTravelInfo) {
            // SPD=5 instant path needs the launch+target data sent so clients can render at the target.
            GestaltNetworking.broadcastChargedStrikeTravel(
                    player, d.targetEntityId, d.launchX, d.launchY, d.launchZ, (byte) d.speedTier);
        }
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.HIT_3);
    }

    private static void clearAndIdle(ServerPlayer player, UUID uuid) {
        dataMap.remove(uuid);
        idleAndBroadcast(player);
    }

    private static void idleAndBroadcast(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        state.clearChargedStrikeData();
        state.setAction(GestaltAction.IDLE);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
    }

    private static LivingEntity resolveTarget(ServerPlayer player, int entityId) {
        if (entityId < 0) return null;
        Entity e = player.level().getEntity(entityId);
        return (e instanceof LivingEntity le) ? le : null;
    }
}
