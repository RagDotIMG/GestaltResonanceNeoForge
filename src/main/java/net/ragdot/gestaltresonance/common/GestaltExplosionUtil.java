package net.ragdot.gestaltresonance.common;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Utility for non-destructive gestalt explosions: damage with distance falloff and
 * line-of-sight exposure. No block destruction, knockback, or fire spread.
 *
 * Typical call site:
 *   GestaltExplosionUtil.detonate(level, center,
 *       GestaltExplosionUtil.scaledRadius(4.0f, gestaltLevel),
 *       GestaltExplosionUtil.scaledDamage(6.0f, gestaltLevel),
 *       GestaltDamageTypes.gestalt(level, player), null);
 */
public final class GestaltExplosionUtil {

    /**
     * Detonates a non-destructive explosion at {@code center}.
     *
     * @param level     the world (works on both logical sides; particles only on ServerLevel)
     * @param center    explosion origin
     * @param radius    sphere radius in blocks
     * @param damage    maximum damage dealt to an entity at the center with full LOS
     * @param source    damage source — the source entity is excluded from damage
     * @param particles if non-null, spawned at the center (server-side only)
     */
    public static void detonate(Level level, Vec3 center, float radius, float damage,
                                DamageSource source, @Nullable ParticleOptions particles) {
        if (radius <= 0 || damage <= 0) return;

        Entity attacker = source.getEntity();

        AABB box = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != attacker)) {
            // Measure to the entity's midpoint for a sensible falloff feel
            Vec3 mid = living.position().add(0, living.getBbHeight() * 0.5, 0);
            double dist = mid.distanceTo(center);
            if (dist > radius) continue;

            // LOS exposure: 0.0 = fully occluded behind geometry, 1.0 = fully exposed
            float exposure = Explosion.getSeenPercent(center, living);
            if (exposure <= 0f) continue;

            // Linear falloff from full damage at the center to near-zero at the edge
            float falloff = 1.0f - (float) (dist / radius);
            float finalDamage = damage * falloff * exposure;
            if (finalDamage <= 0f) continue;

            living.hurt(source, finalDamage);
        }

        // Sound — matches the pitch randomisation used by vanilla Explosion
        level.playSound(
                null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS,
                4.0f,
                (1.0f + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f) * 0.7f
        );

        // Particles (ServerLevel only; caller passes null to skip)
        if (particles != null && level instanceof ServerLevel serverLevel) {
            double spread = radius * 0.35;
            serverLevel.sendParticles(particles,
                    center.x, center.y, center.z,
                    40, spread, spread, spread, 0.05);
        }
    }

    /**
     * Damage for an explosion ability scaled by gestalt level.
     * Mirrors the chain-hit formula: base + (level × 0.5).
     *
     * @param base         base damage at level 1
     * @param gestaltLevel current gestalt level (1–15)
     */
    public static float scaledDamage(float base, int gestaltLevel) {
        return base + gestaltLevel * 0.5f;
    }

    /**
     * Radius for an explosion ability scaled by gestalt level.
     * Grows 0.15 blocks per level (e.g. base 4 → 4.15 at level 1, 6.25 at level 15).
     *
     * @param base         base radius at level 1
     * @param gestaltLevel current gestalt level (1–15)
     */
    public static float scaledRadius(float base, int gestaltLevel) {
        return base + gestaltLevel * 0.15f;
    }

    private GestaltExplosionUtil() {}
}
