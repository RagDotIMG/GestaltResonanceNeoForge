package net.ragdot.gestaltresonance.common.power.amen_break;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltAttackEvents;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

/**
 * Amen Break — Queen Killer (Power 1G).
 *
 * Activation: Guard + Z. Drops guard, deducts 20 gestalt-XP and 2.0 exhaustion, transitions
 * gestalt action to {@link GestaltAction#POWER_1G_WINDUP} for 40 ticks. At tick 40, sweeps the
 * standard hit-chain hitbox and damages the closest live entity for 3× the standard hit
 * damage formula. The hit entity is "marked" for a delayed non-destructive explosion 50 ticks
 * later. If the entity dies before the timer expires, the explosion fires at its death
 * position. If the player takes any post-mitigation damage during the windup, the ability
 * aborts (no XP refund, cooldown still applies). 55-tick cooldown is set on activation.
 */
public final class AmenBreakPower1G {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_1, GestaltPowerModifier.GUARD);

    /** Called once at mod startup to wire the activator into the registry. */
    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower1G::activate);
    }

    // ── Activation ────────────────────────────────────────────────────────────

    public static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Guard chord requires actually guarding right now. (Modifier was already matched
        // for dispatch — but state may have changed between client press and server handle.)
        if (!state.isSummoned()) return;
        if (!state.isAwakened()) return;
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[0][2]) return;
        if (!state.isGuarding()) return;
        if (state.getAction() != GestaltAction.GUARD) return;

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) return;
        if (state.getTotalGestaltXp() < GestaltCosts.POWER_1G_XP_COST) return;

        // Drop guard and pay costs (may de-level if within-level XP is insufficient)
        state.clearGuard(); // sets currentAction = IDLE
        state.spendGestaltXp(GestaltCosts.POWER_1G_XP_COST);
        player.causeFoodExhaustion(GestaltCosts.POWER_1G_EXHAUSTION);

        // Begin windup
        state.setAction(GestaltAction.POWER_1G_WINDUP);
        state.setPowerWindupStartTick(currentTick);
        state.setPowerCooldown(KEY.slot(), KEY.modifier(), currentTick + GestaltCosts.POWER_1G_COOLDOWN_TICKS);

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        // Sync to clients: guard off, action change, XP update, cooldown HUD
        GestaltNetworking.syncGuardToTracking(player, false);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.POWER_1G_WINDUP);
        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.POWER_1G_COOLDOWN_TICKS);

        GestaltResonance.LOGGER.debug("AmenBreak Queen Killer 1G activated for {}", player.getName().getString());
    }

    // ── Per-tick logic ────────────────────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (state.getAction() == GestaltAction.POWER_1G_WINDUP) {
            tickWindup(player, state);
            // Re-read; tickWindup may have mutated state
            state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        }

        if (state.getMarkedEntityId() >= 0) {
            tickMark(player, state);
        }
    }

    private static void tickWindup(ServerPlayer player, PlayerGestaltState state) {
        long currentTick = player.getServer().getTickCount();
        long elapsed = currentTick - state.getPowerWindupStartTick();
        if (elapsed < GestaltCosts.POWER_1G_ANIMATION_TICKS) return;

        // Windup complete: find target and possibly hit
        LivingEntity target = findClosestTargetInFront(player);
        if (target != null) {
            onHit(player, target, state);
        }

        // Always return to IDLE on windup completion
        PlayerGestaltState fresh = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        fresh.setAction(GestaltAction.IDLE);
        fresh.setPowerWindupStartTick(-1L);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), fresh);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
    }

    private static void tickMark(ServerPlayer player, PlayerGestaltState state) {
        Entity entity = player.level().getEntity(state.getMarkedEntityId());

        // If the entity vanished or died, fire at the last known position
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            Vec3 fallback = state.getMarkedEntityLastPos();
            if (fallback != null) {
                detonate(player, fallback);
            }
            state.clearMark();
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            return;
        }

        // Track current position in case the entity dies mid-tick before the next pass
        state.setMarkedEntityLastPos(living.position());

        int remaining = state.getMarkedEntityTicksRemaining() - 1;
        if (remaining <= 0) {
            detonate(player, living.position());
            state.clearMark();
        } else {
            state.setMarkedEntityTicksRemaining(remaining);
        }
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
    }

    // ── Hit + mark ────────────────────────────────────────────────────────────

    private static void onHit(ServerPlayer player, LivingEntity target, PlayerGestaltState state) {
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int strength = stats != null ? stats.strength() : 1;
        int clamped = Math.max(1, Math.min(strength, GestaltAttackEvents.BASE_DAMAGE_BY_STRENGTH.length - 1));
        float baseDamage = GestaltAttackEvents.BASE_DAMAGE_BY_STRENGTH[clamped]
                + state.getGestaltLevel() * 0.5f;
        float finalDamage = baseDamage * GestaltCosts.POWER_1G_DAMAGE_MULTIPLIER;

        target.hurt(GestaltDamageTypes.gestalt(player.level(), player), finalDamage);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                net.minecraft.sounds.SoundEvents.MACE_SMASH_AIR,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

        // Mark for delayed explosion. If the hit killed the target, tickMark will detect
        // !isAlive() next tick and detonate at the last-known (death) position.
        state.setMarkedEntityId(target.getId());
        state.setMarkedEntityTicksRemaining(GestaltCosts.POWER_1G_EXPLOSION_DELAY);
        state.setMarkedEntityLastPos(target.position());
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
    }

    private static void detonate(ServerPlayer player, Vec3 center) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        int level = state.getGestaltLevel();
        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.POWER_1G_EXPLOSION_BASE_RADIUS, level);
        float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.POWER_1G_EXPLOSION_BASE_DAMAGE, level);
        GestaltExplosionUtil.detonate(
                player.level(), center, radius, damage,
                GestaltDamageTypes.gestalt(player.level(), player),
                null);
    }

    // ── Target selection (closest live entity in the standard hitbox sweep) ──

    @Nullable
    private static LivingEntity findClosestTargetInFront(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(GestaltAttackEvents.ATTACK_REACH));
        AABB sweep = new AABB(eye, end).inflate(
                GestaltAttackEvents.HITBOX_HALF_WIDTH,
                GestaltAttackEvents.HITBOX_HALF_HEIGHT,
                GestaltAttackEvents.HITBOX_HALF_WIDTH);

        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class, sweep,
                e -> e != player && e.isAlive() && !e.isInvulnerable());
        if (targets.isEmpty()) return null;
        // Closest to the player's eye position
        return targets.stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(eye)))
                .orElse(null);
    }

    // ── Abort on damage taken during windup ──────────────────────────────────

    /** Single registered instance — listens for {@link LivingDamageEvent} on NeoForge.EVENT_BUS. */
    public static final AmenBreakPower1G EVENT_LISTENER = new AmenBreakPower1G();

    private AmenBreakPower1G() {}

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.getAction() != GestaltAction.POWER_1G_WINDUP) return;
        // Only abort on damage that actually landed (positive new damage)
        if (event.getNewDamage() <= 0f) return;

        state.setAction(GestaltAction.IDLE);
        state.setPowerWindupStartTick(-1L);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncAttackActionToTracking(player, GestaltAction.IDLE);
        GestaltResonance.LOGGER.debug("AmenBreak Queen Killer 1G aborted (took damage) for {}", player.getName().getString());
        // Cooldown stays set — no refund.
    }
}
