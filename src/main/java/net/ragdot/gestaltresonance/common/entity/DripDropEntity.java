package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Small falling projectile spawned by a PopDrip END block when a hostile mob is nearby.
 * Falls straight down; explodes on hitting a hostile entity, silently discards on block hit.
 * Ignores players completely (neither hits nor damages them).
 */
public class DripDropEntity extends ThrowableProjectile {

    @Nullable
    private UUID ownerUuid;
    private int gestaltLevel = 1;

    public DripDropEntity(EntityType<? extends DripDropEntity> type, Level level) {
        super(type, level);
    }

    /** Called by PopDripBlockEntity when spawning the drop. */
    public void setOwnerData(@Nullable UUID ownerUuid, int gestaltLevel) {
        this.ownerUuid = ownerUuid;
        this.gestaltLevel = gestaltLevel;
    }

    // ── Collision filtering ───────────────────────────────────────────────────

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof Player) return false;
        if (!(entity instanceof net.minecraft.world.entity.monster.Enemy)) return false;
        return super.canHitEntity(entity);
    }

    // ── Hit handling ──────────────────────────────────────────────────────────

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Vec3 center = result.getLocation();
        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.POPDRIP_EXPLOSION_BASE_RADIUS, gestaltLevel);
        float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.POPDRIP_EXPLOSION_BASE_DAMAGE, gestaltLevel);
        DamageSource src = buildDamageSource();
        GestaltExplosionUtil.detonate(level(), center, radius, damage, src, null);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide) {
            Vec3 center = Vec3.atCenterOf(result.getBlockPos().relative(result.getDirection()));
            float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.POPDRIP_EXPLOSION_BASE_RADIUS, gestaltLevel);
            float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.POPDRIP_EXPLOSION_BASE_DAMAGE, gestaltLevel);
            GestaltExplosionUtil.detonate(level(), center, radius, damage, buildDamageSource(), null);
        }
        discard();
    }

    // ── Damage source ─────────────────────────────────────────────────────────

    private DamageSource buildDamageSource() {
        if (ownerUuid != null && level() instanceof ServerLevel sl) {
            Player owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) return GestaltDamageTypes.gestalt(level(), owner);
        }
        return level().damageSources().source(GestaltDamageTypes.GESTALT, null, null);
    }

    // ── Boilerplate ───────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) tag.putUUID("OwnerUUID", ownerUuid);
        tag.putInt("GestaltLevel", gestaltLevel);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerUuid = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        gestaltLevel = tag.getInt("GestaltLevel");
    }
}
