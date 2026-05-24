package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower2B;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of active {@link SpawnIllusionEntity} instances and the
 * mini-ghost window granted to the player on a teleport swap.
 */
public class GestaltIllusionEvents {

    /** Singleton registered via NeoForge.EVENT_BUS. */
    public static final GestaltIllusionEvents INSTANCE = new GestaltIllusionEvents();

    private final Map<UUID, SpawnIllusionEntity> illusionMap   = new HashMap<>();
    private final Map<UUID, Integer>             miniGhostTicks = new HashMap<>();

    private GestaltIllusionEvents() {}

    // ── Public API ────────────────────────────────────────────────────────────

    @Nullable
    public SpawnIllusionEntity getIllusion(UUID ownerUuid) {
        SpawnIllusionEntity e = illusionMap.get(ownerUuid);
        if (e != null && !e.isAlive()) {
            illusionMap.remove(ownerUuid);
            return null;
        }
        return e;
    }

    public void registerIllusion(UUID ownerUuid, SpawnIllusionEntity entity) {
        illusionMap.put(ownerUuid, entity);
    }

    public void unregisterIllusion(UUID ownerUuid) {
        illusionMap.remove(ownerUuid);
    }

    /** Grant the player N ticks of Phase-Out-style ghost invulnerability. */
    public void startMiniGhost(ServerPlayer player, int ticks) {
        GhostPlayerHandler.setGhostState(player, true);
        miniGhostTicks.put(player.getUUID(), ticks);
    }

    /**
     * Called by {@link SpawnIllusionEntity#tick()} (lifetime/void) and by
     * {@link net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower2B} (teleport swap).
     *
     * @param explode true → detonate at illusion position; false → silent despawn
     */
    public static void expire(SpawnIllusionEntity illusion, boolean explode) {
        UUID ownerUuid = illusion.getOwnerUuid();
        INSTANCE.unregisterIllusion(ownerUuid != null ? ownerUuid : new UUID(0, 0));

        if (explode && illusion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Vec3 pos = illusion.position();
            int gestaltLevel = 1;
            if (ownerUuid != null) {
                ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
                if (owner != null) {
                    gestaltLevel = owner.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get())
                            .getGestaltLevel();
                }
            }
            float radius = GestaltExplosionUtil.scaledRadius(
                    GestaltCosts.ILLUSION_EXPLOSION_BASE_RADIUS, gestaltLevel);
            float damage = GestaltExplosionUtil.scaledDamage(
                    GestaltCosts.ILLUSION_EXPLOSION_BASE_DAMAGE, gestaltLevel);
            net.minecraft.world.damagesource.DamageSource src;
            if (ownerUuid != null) {
                ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
                src = owner != null
                        ? GestaltDamageTypes.gestalt(sl, owner)
                        : sl.damageSources().source(GestaltDamageTypes.GESTALT, null, null);
            } else {
                src = sl.damageSources().source(GestaltDamageTypes.GESTALT, null, null);
            }
            GestaltExplosionUtil.detonate(sl, pos, radius, damage, src, null);
        }

        // Apply cooldown to owner
        if (ownerUuid != null && illusion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                long expiryTick = sl.getServer().getTickCount() + GestaltCosts.ILLUSION_COOLDOWN;
                PlayerGestaltState state = owner.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                state.setPowerCooldown(GestaltPowerSlot.POWER_2, GestaltPowerModifier.NONE, expiryTick);
                owner.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                GestaltNetworking.syncCooldownToPlayer(owner, GestaltCosts.ILLUSION_COOLDOWN);
            }
        }

        // Clear targets on nearby mobs so they don't path toward the now-dead entity
        if (illusion.level() instanceof net.minecraft.server.level.ServerLevel) {
            net.minecraft.world.phys.AABB range = illusion.getBoundingBox().inflate(32.0);
            illusion.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, range,
                    mob -> mob.getTarget() == illusion)
                    .forEach(mob -> mob.setTarget(null));
        }

        illusion.discard();
    }

    // ── Event listeners ───────────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (UUID uuid : new ArrayList<>(miniGhostTicks.keySet())) {
            int remaining = miniGhostTicks.merge(uuid, -1, Integer::sum);
            if (remaining <= 0) {
                miniGhostTicks.remove(uuid);
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
                    // Only end ghost state if Phase Out isn't also holding it open
                    if (!state.isPhaseOutActive()) {
                        GhostPlayerHandler.setGhostState(player, false);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SpawnIllusionEntity illusion = getIllusion(player.getUUID());
        if (illusion != null) expire(illusion, false);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SpawnIllusionEntity illusion = getIllusion(player.getUUID());
        if (illusion != null) expire(illusion, false);
    }
}
