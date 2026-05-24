package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltIllusionEvents;
import net.ragdot.gestaltresonance.common.GestaltParticles;
import net.ragdot.gestaltresonance.common.PopSproutTracker;
import net.ragdot.gestaltresonance.common.block.PopSproutBlock;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Amen Break Power 2B decoy entity.
 * Looks like the owner player, redirects mob aggro, walks toward pop sprouts, and
 * explodes at the end of its 200-tick lifetime.
 */
public class SpawnIllusionEntity extends PathfinderMob {

    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_OWNER_UUID_STR =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.STRING);

    @Nullable private UUID ownerUuid;
    private Vec3 forwardDirection = new Vec3(0, 0, 1);
    private int ageTicks = 0;

    public SpawnIllusionEntity(EntityType<? extends SpawnIllusionEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    public void setOwnerData(UUID ownerUuid, Vec3 lookDirection) {
        this.ownerUuid = ownerUuid;
        this.forwardDirection = lookDirection.normalize();
        entityData.set(DATA_OWNER_UUID_STR, ownerUuid.toString());
    }

    @Nullable
    public UUID getOwnerUuid() {
        String s = entityData.get(DATA_OWNER_UUID_STR);
        if (s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public int getAgeTicks() {
        return entityData.get(DATA_AGE);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MoveToPopSproutGoal(this));
        goalSelector.addGoal(2, new WalkForwardGoal(this));
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ageTicks++;
        entityData.set(DATA_AGE, ageTicks);

        if (ageTicks % 5 == 0 && level() instanceof ServerLevel sl) {
            for (int i = 0; i < 2; i++) {
                double px = getX() + (random.nextDouble() - 0.5) * getBbWidth();
                double py = getY() + random.nextDouble() * getBbHeight();
                double pz = getZ() + (random.nextDouble() - 0.5) * getBbWidth();
                sl.sendParticles(GestaltParticles.GESTALT_ILLUSION.get(), px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        // Void check
        if (getY() < level().getMinBuildHeight() - 16) {
            GestaltIllusionEvents.expire(this, false);
            return;
        }

        // Redirect mob aggro every 20 ticks
        if (ageTicks % 20 == 0 && ownerUuid != null) {
            Player owner = level().getPlayerByUUID(ownerUuid);
            if (owner != null) {
                AABB range = getBoundingBox().inflate(24.0);
                level().getEntitiesOfClass(Mob.class, range,
                        mob -> mob.getTarget() == owner || mob.getTarget() == null)
                        .forEach(mob -> mob.setTarget(this));
            }
        }

        // Expire at lifetime
        if (ageTicks >= GestaltCosts.ILLUSION_LIFETIME) {
            GestaltIllusionEvents.expire(this, true);
        }
    }

    // ── Damage / targeting ────────────────────────────────────────────────────

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_OWNER_UUID_STR, "");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("OwnerUUID", ownerUuid);
        tag.putDouble("FwdX", forwardDirection.x);
        tag.putDouble("FwdY", forwardDirection.y);
        tag.putDouble("FwdZ", forwardDirection.z);
        tag.putInt("AgeTicks", ageTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerUuid = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        if (ownerUuid != null) entityData.set(DATA_OWNER_UUID_STR, ownerUuid.toString());
        forwardDirection = new Vec3(
                tag.getDouble("FwdX"), tag.getDouble("FwdY"), tag.getDouble("FwdZ"));
        ageTicks = tag.getInt("AgeTicks");
    }

    // ── Inner goals ───────────────────────────────────────────────────────────

    private static final class MoveToPopSproutGoal extends Goal {

        private final SpawnIllusionEntity illusion;
        @Nullable private BlockPos target;

        MoveToPopSproutGoal(SpawnIllusionEntity illusion) {
            this.illusion = illusion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private @Nullable BlockPos findTarget() {
            if (!(illusion.level() instanceof ServerLevel sl)) return null;
            return PopSproutTracker.get(sl.getServer())
                    .findNewestInRange(sl, illusion.position(),
                            GestaltCosts.ILLUSION_POPSPROUT_SCAN_RADIUS);
        }

        @Override
        public boolean canUse() {
            target = findTarget();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null) return false;
            // Check if targeted block still exists
            if (!(illusion.level().getBlockState(target).getBlock() instanceof PopSproutBlock)) {
                target = findTarget();
            } else {
                // Switch to a newer sprout if one appeared
                BlockPos newest = findTarget();
                if (newest != null && !newest.equals(target)) target = newest;
            }
            return target != null;
        }

        @Override
        public void tick() {
            if (target == null) return;
            if (illusion.blockPosition().closerThan(target, 1.5)) {
                illusion.getNavigation().stop();
            } else {
                illusion.getNavigation().moveTo(
                        target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.4);
            }
        }
    }

    private static final class WalkForwardGoal extends Goal {

        private final SpawnIllusionEntity illusion;

        WalkForwardGoal(SpawnIllusionEntity illusion) {
            this.illusion = illusion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 dest = illusion.position().add(illusion.forwardDirection.scale(20.0));
            illusion.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0);
        }
    }
}
