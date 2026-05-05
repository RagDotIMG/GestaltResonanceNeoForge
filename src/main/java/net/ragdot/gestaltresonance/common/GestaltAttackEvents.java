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
    private static final int CHAIN_COOLDOWN = GestaltCosts.CHAIN_COOLDOWN_TICKS;

    // --- Damage table: index = strength (1-5), value = base damage per hit ---
    public static final float[] BASE_DAMAGE_BY_STRENGTH = { 0f, 0.5f, 1.5f, 2.5f, 4.5f, 5.5f };

    // --- Speed table: index = speed (1-5), ticks after DAMAGE_TICK before buffered hit can advance ---
    private static final int[] ADVANCE_DELAY_BY_SPEED = { 0, 26, 20, 15, 10, 7 };

    // --- Hitbox: twice normal melee reach, vanilla-width sweep ---
    public static final double ATTACK_REACH      = 3.0; // blocks in look direction
    public static final double HITBOX_HALF_WIDTH = 1.0; // blocks each side perpendicular
    public static final double HITBOX_HALF_HEIGHT = 1.25;

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
        if (action == GestaltAction.GUARD || action == GestaltAction.LEDGE_GRAB || action == GestaltAction.THROW
                || action == GestaltAction.CHARGED_STRIKE_WINDUP || action == GestaltAction.CHARGED_STRIKE_TRAVEL) return;
        long now = player.getServer().getTickCount();
        if (isOnCooldown(uuid, now)) return;

        // ── Start HIT_1 ──────────────────────────────────────────────────────
        chainMap.put(uuid, new ChainData());
        state.setAction(GestaltAction.HIT_1);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.HIT_1);
    }

    /** Cancel any active chain for a player (e.g. on unsummon). Also cancels any charged strike. */
    public static void cancelChain(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean removedChain = chainMap.remove(uuid) != null;
        GestaltChargedStrikeEvents.cancelStrike(player);
        if (removedChain) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.setAction(GestaltAction.IDLE);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
        }
    }

    /** Like {@link #cancelChain} but does not modify the action or broadcast. Used by charged strike init. */
    public static void cancelChainSilently(ServerPlayer player) {
        chainMap.remove(player.getUUID());
    }

    /** Public cooldown query, shared with the charged-strike system. */
    public static boolean isOnSharedCooldown(UUID uuid, long currentTick) {
        return isOnCooldown(uuid, currentTick);
    }

    /** Write into the shared cooldown map. Used by charged-strike outcomes. */
    public static void setSharedCooldown(UUID uuid, long expiryTick) {
        cooldownMap.put(uuid, expiryTick);
    }

    /** Create a fresh ChainData for the charged strike's HIT_3 phase so the existing tick loop drives the damage. */
    public static void startStrikeChainData(UUID uuid) {
        chainMap.put(uuid, new ChainData());
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
                boolean wasChargedStrike = applyDamage(player, state);
                if (action == GestaltAction.HIT_3) {
                    // Charged strike sets its own (longer) cooldown inside applyDamage; otherwise apply chain cooldown.
                    if (!wasChargedStrike) {
                        cooldownMap.put(uuid, now + CHAIN_COOLDOWN);
                        GestaltNetworking.syncCooldownToPlayer(player, CHAIN_COOLDOWN);
                    }
                }
            }

            // ── Charged-strike HIT_3 ends as soon as the strike animation finishes ──
            // (avoids lingering on the target for the full 32-tick chain timeout)
            if (action == GestaltAction.HIT_3
                    && state.getChargedStrikeTargetEntityId() >= 0
                    && chain.tick >= GestaltCosts.CHARGED_STRIKE_HIT3_DURATION_TICKS) {
                chainMap.remove(uuid);
                state.clearChargedStrikeData();
                state.setAction(GestaltAction.IDLE);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
                continue;
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
                state.clearChargedStrikeData();
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

    /**
     * Apply HIT_n damage. Returns true if this was a charged-strike HIT_3 (the caller skips the
     * normal chain cooldown because charged strike sets its own).
     */
    private static boolean applyDamage(ServerPlayer player, PlayerGestaltState state) {
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int strength = (stats != null) ? stats.strength() : 1;
        int clampedStrength = Math.max(1, Math.min(strength, BASE_DAMAGE_BY_STRENGTH.length - 1));
        float baseDamage = BASE_DAMAGE_BY_STRENGTH[clampedStrength] + state.getGestaltLevel() * 0.5f;

        // Desperate struggle damage boost
        if (stats != null && state.isDesperateStruggle(stats)) {
            baseDamage *= GestaltCosts.DESPERATE_STRUGGLE_DAMAGE_MULTIPLIER;
        }

        // ── Charged-strike branch: single-target, double damage and knockback ──
        int strikeTargetId = (state.getAction() == GestaltAction.HIT_3)
                ? GestaltChargedStrikeEvents.consumeStrikePending(player.getUUID())
                : -1;
        if (strikeTargetId >= 0) {
            net.minecraft.world.entity.Entity raw = player.level().getEntity(strikeTargetId);
            boolean hitLanded = false;
            if (raw instanceof LivingEntity target && target.isAlive()) {
                float damage = baseDamage * GestaltCosts.CHARGED_STRIKE_DAMAGE_MULTIPLIER;
                target.hurt(GestaltDamageTypes.gestalt(player.level(), player), damage);
                double dx = player.getX() - target.getX();
                double dz = player.getZ() - target.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0) {
                    target.knockback(0.4 * GestaltCosts.CHARGED_STRIKE_KNOCKBACK_MULTIPLIER, dx / len, dz / len);
                }
                GestaltNetworking.broadcastHitParticles(player,
                        (float) target.getX(), (float) (target.getY() + target.getBbHeight() * 0.5f),
                        (float) target.getZ(), (byte) 3);
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.sounds.SoundEvents.MACE_SMASH_AIR,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                hitLanded = true;
            }
            // Apply hit-side cost + cooldown regardless of whether the target was still resolvable.
            player.causeFoodExhaustion(GestaltCosts.CHARGED_STRIKE_EXHAUSTION);
            long now = player.getServer().getTickCount();
            cooldownMap.put(player.getUUID(), now + GestaltCosts.CHARGED_STRIKE_HIT_COOLDOWN_TICKS);
            GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.CHARGED_STRIKE_HIT_COOLDOWN_TICKS);
            if (hitLanded) {
                GestaltResonanceEvents.applyResonance(player, GestaltCosts.GAIN_CHARGED_STRIKE_HIT);
            }
            return true;
        }

        // ── Standard chain hit: AOE sweep ──
        Vec3 eyePos  = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos  = eyePos.add(lookVec.scale(ATTACK_REACH));

        AABB sweepBox = new AABB(eyePos, endPos).inflate(HITBOX_HALF_WIDTH, HITBOX_HALF_HEIGHT, HITBOX_HALF_WIDTH);

        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                sweepBox,
                e -> e != player && e.isAlive()
        );

        GestaltAction hitAction = state.getAction();
        byte hitNum = switch (hitAction) {
            case HIT_2 -> (byte) 2;
            case HIT_3 -> (byte) 3;
            default    -> (byte) 1;
        };
        for (LivingEntity target : targets) {
            target.hurt(GestaltDamageTypes.gestalt(player.level(), player), baseDamage);
            // knockback(x, z) subtracts the direction, so pass player→target reversed to push away
            double dx = player.getX() - target.getX();
            double dz = player.getZ() - target.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                target.knockback(0.4, dx / len, dz / len);
            }
            GestaltNetworking.broadcastHitParticles(player,
                    (float) target.getX(), (float) (target.getY() + target.getBbHeight() * 0.5f),
                    (float) target.getZ(), hitNum);
        }

        // Play hit sound once per swing when anything connected
        if (!targets.isEmpty()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // Combo resonance gain (only when at least one target hit)
        if (!targets.isEmpty()) {
            GestaltAction action = state.getAction();
            int hitResonance = GestaltCosts.GAIN_COMBO_HIT_BASE;
            if (action == GestaltAction.HIT_2) {
                hitResonance += GestaltCosts.GAIN_COMBO_HIT2_BONUS;
            } else if (action == GestaltAction.HIT_3) {
                hitResonance += GestaltCosts.GAIN_COMBO_HIT3_BONUS;
                if (player.getHealth() < GestaltCosts.GAIN_CHAIN_FINISHER_HP_THRESHOLD) {
                    hitResonance += GestaltCosts.GAIN_CHAIN_FINISHER_LOW_HP;
                }
            }
            GestaltResonanceEvents.applyResonance(player, hitResonance);
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Inner data class
    // -------------------------------------------------------------------------

    private static class ChainData {
        int     tick          = 0;
        boolean inputBuffered = false;
    }
}
