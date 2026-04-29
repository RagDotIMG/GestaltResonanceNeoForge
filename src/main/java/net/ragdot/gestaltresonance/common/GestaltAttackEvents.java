package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side: manages the 3-hit gestalt attack chain.
 *
 * Chain lifecycle per hit:
 *   tick 0   : chain/hit starts, gestalt moves to guard position
 *   tick 3   : hitbox scan and damage applied (DAMAGE_TICK)
 *   tick 0-8 : one input press can be buffered for next hit
 *   tick 8   : if buffer → advance to next hit; else → return to IDLE (TIMEOUT_TICK)
 *
 * After hit 3 damage (tick 3 of HIT_3): 20-tick cooldown before a new chain can start.
 */
public class GestaltAttackEvents {

    // --- Timing constants ---
    private static final int DAMAGE_TICK   = 3;
    private static final int TIMEOUT_TICK  = 32;
    private static final int CHAIN_COOLDOWN = 45;

    // --- Damage table: index = strength (1-5), value = base damage per hit ---
    private static final float[] BASE_DAMAGE_BY_STRENGTH = { 0f, 0.5f, 1.5f, 2.5f, 4.5f, 5.5f };

    // --- Speed table: index = speed (1-5), ticks after DAMAGE_TICK before buffered hit can advance ---
    private static final int[] ADVANCE_DELAY_BY_SPEED = { 0, 26, 20, 15, 10, 7 };

    // --- Hitbox: twice normal melee reach, vanilla-width sweep ---
    private static final double ATTACK_REACH      = 3.0; // blocks in look direction
    private static final double HITBOX_HALF_WIDTH = 1.0; // blocks each side perpendicular
    private static final double HITBOX_HALF_HEIGHT = 1.25;

    // --- Per-player server-side chain state (not synced, lives only on server) ---
    private static final Map<UUID, ChainData> chainMap     = new HashMap<>();
    private static final Map<UUID, Long>      cooldownMap  = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API called from GestaltNetworking
    // -------------------------------------------------------------------------

    /** Called when the server receives AttackInputC2S from a client. */
    public static void handleAttackInput(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        GestaltAction action = state.getAction();
        UUID uuid = player.getUUID();

        // ── Chain already active: buffer input ──────────────────────────────
        if (action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2 || action == GestaltAction.HIT_3) {
            ChainData chain = chainMap.get(uuid);
            if (chain != null && chain.tick < TIMEOUT_TICK) {
                chain.inputBuffered = true;
            }
            return;
        }

        // ── Cannot start chain ───────────────────────────────────────────────
        if (action == GestaltAction.GUARD || action == GestaltAction.LEDGE_GRAB) return;
        long now = player.getServer().getTickCount();
        if (isOnCooldown(uuid, now)) return;

        // ── Start HIT_1 ──────────────────────────────────────────────────────
        chainMap.put(uuid, new ChainData());
        state.setAction(GestaltAction.HIT_1);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.HIT_1);
    }

    /** Cancel any active chain for a player (e.g. on unsummon). */
    public static void cancelChain(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (chainMap.remove(uuid) != null) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setAction(GestaltAction.IDLE);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /** Cancel vanilla entity attacks while the gestalt is summoned — chain deals damage instead. */
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isSummoned()) {
            event.setCanceled(true);
        }
    }

    /** Advance the attack chain each server tick. */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            UUID uuid = player.getUUID();

            // Clean up chain if gestalt was unsummoned externally
            if (!state.isSummoned()) {
                chainMap.remove(uuid);
                continue;
            }

            GestaltAction action = state.getAction();
            boolean hitActive = action == GestaltAction.HIT_1
                    || action == GestaltAction.HIT_2
                    || action == GestaltAction.HIT_3;
            if (!hitActive) continue;

            ChainData chain = chainMap.get(uuid);
            if (chain == null) {
                // Stale action — reset
                state.setAction(GestaltAction.IDLE);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
                continue;
            }

            chain.tick++;

            // ── Damage scan at tick 3 ─────────────────────────────────────────
            if (chain.tick == DAMAGE_TICK) {
                applyDamage(player, state);
                if (action == GestaltAction.HIT_3) {
                    // Start chain cooldown: it runs concurrently with the remaining hit window
                    cooldownMap.put(uuid, now + CHAIN_COOLDOWN);
                }
            }

            // ── Early advancement: speed determines the earliest the next hit can fire ──
            if (chain.inputBuffered && action != GestaltAction.HIT_3) {
                GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
                int speed = (stats != null) ? stats.speed() : 1;
                int clampedSpeed = Math.max(1, Math.min(speed, ADVANCE_DELAY_BY_SPEED.length - 1));
                int minAdvanceTick = DAMAGE_TICK + ADVANCE_DELAY_BY_SPEED[clampedSpeed];
                if (chain.tick >= minAdvanceTick) {
                    GestaltAction next = (action == GestaltAction.HIT_1) ? GestaltAction.HIT_2 : GestaltAction.HIT_3;
                    chainMap.put(uuid, new ChainData());
                    state.setAction(next);
                    player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                    GestaltNetworking.syncAttackActionToTracking(player, next);
                    continue;
                }
            }

            // ── Timeout: expire chain if no early advance triggered ────────────
            if (chain.tick >= TIMEOUT_TICK) {
                chainMap.remove(uuid);
                state.setAction(GestaltAction.IDLE);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isOnCooldown(UUID uuid, long currentTick) {
        Long expires = cooldownMap.get(uuid);
        if (expires == null) return false;
        if (currentTick >= expires) {
            cooldownMap.remove(uuid);
            return false;
        }
        return true;
    }

    private static void applyDamage(ServerPlayer player, PlayerGestaltState state) {
        Vec3 eyePos  = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos  = eyePos.add(lookVec.scale(ATTACK_REACH));

        AABB sweepBox = new AABB(eyePos, endPos).inflate(HITBOX_HALF_WIDTH, HITBOX_HALF_HEIGHT, HITBOX_HALF_WIDTH);

        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                sweepBox,
                e -> e != player && e.isAlive()
        );

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int strength = (stats != null) ? stats.strength() : 1;
        int clampedStrength = Math.max(1, Math.min(strength, BASE_DAMAGE_BY_STRENGTH.length - 1));
        float damage = BASE_DAMAGE_BY_STRENGTH[clampedStrength] + state.getGestaltLevel() * 0.5f;

        for (LivingEntity target : targets) {
            target.hurt(GestaltDamageTypes.gestalt(player.level(), player), damage);
            // knockback(x, z) subtracts the direction, so pass player→target reversed to push away
            double dx = player.getX() - target.getX();
            double dz = player.getZ() - target.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                target.knockback(0.4, dx / len, dz / len);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner data class
    // -------------------------------------------------------------------------

    private static class ChainData {
        int     tick          = 0;
        boolean inputBuffered = false;
    }
}
