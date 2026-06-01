package net.ragdot.gestaltresonance.common.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Tracks hostile entities that have been hit by a TearProjectileEntity and applies
 * 1 HP of drowning damage every second (20 ticks) until the effect expires.
 *
 * Damage is lethal and bypasses armor (DamageSource#drown).
 * Stacks by extending the expiry to the later of existing vs. new.
 */
public final class DrowningDamageTracker {

    // entity UUID → absolute server tick at which the drowning effect expires
    private static final Map<UUID, Long> TRACKED = new HashMap<>();

    /**
     * Schedule drowning damage for an entity.
     *
     * @param entityId  target entity UUID
     * @param expiryTick absolute server tick at which the effect ends (use
     *                   {@code server.getTickCount() + durationTicks + 1})
     */
    public static void apply(UUID entityId, long expiryTick) {
        TRACKED.merge(entityId, expiryTick, Math::max);
    }

    /** Called every server tick from GestaltResonance.onServerTick. */
    public static void tick(MinecraftServer server) {
        long currentTick = server.getTickCount();
        boolean applyDamage = currentTick % 20 == 0;

        Iterator<Map.Entry<UUID, Long>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();

            if (currentTick >= entry.getValue()) {
                it.remove();
                continue;
            }

            if (!applyDamage) continue;

            LivingEntity entity = findEntity(server, entry.getKey());
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            entity.hurt(entity.level().damageSources().drown(), 1.0f);
        }
    }

    @Nullable
    private static LivingEntity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    private DrowningDamageTracker() {}
}
