package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    private static final EntityDataAccessor<Boolean> DATA_BODY_DOUBLE_MODE =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.BOOLEAN);
    // Equipment slots for body double rendering (MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD)
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_MAINHAND =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_OFFHAND =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_FEET =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_LEGS =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_CHEST =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_HEAD =
            SynchedEntityData.defineId(SpawnIllusionEntity.class, EntityDataSerializers.ITEM_STACK);

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

    // ── Body double mode ──────────────────────────────────────────────────────

    public void setBodyDoubleMode(boolean v) { entityData.set(DATA_BODY_DOUBLE_MODE, v); }
    public boolean isBodyDoubleMode() { return entityData.get(DATA_BODY_DOUBLE_MODE); }

    public void copyEquipmentFrom(Player player) {
        entityData.set(DATA_SLOT_MAINHAND, player.getItemBySlot(EquipmentSlot.MAINHAND).copy());
        entityData.set(DATA_SLOT_OFFHAND,  player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
        entityData.set(DATA_SLOT_FEET,     player.getItemBySlot(EquipmentSlot.FEET).copy());
        entityData.set(DATA_SLOT_LEGS,     player.getItemBySlot(EquipmentSlot.LEGS).copy());
        entityData.set(DATA_SLOT_CHEST,    player.getItemBySlot(EquipmentSlot.CHEST).copy());
        entityData.set(DATA_SLOT_HEAD,     player.getItemBySlot(EquipmentSlot.HEAD).copy());
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (!isBodyDoubleMode()) return ItemStack.EMPTY;
        return switch (slot) {
            case MAINHAND -> entityData.get(DATA_SLOT_MAINHAND);
            case OFFHAND  -> entityData.get(DATA_SLOT_OFFHAND);
            case FEET     -> entityData.get(DATA_SLOT_FEET);
            case LEGS     -> entityData.get(DATA_SLOT_LEGS);
            case CHEST    -> entityData.get(DATA_SLOT_CHEST);
            case HEAD     -> entityData.get(DATA_SLOT_HEAD);
            default -> ItemStack.EMPTY;
        };
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

        boolean bodyDouble = isBodyDoubleMode();

        if (!bodyDouble && ageTicks % 5 == 0 && level() instanceof ServerLevel sl) {
            for (int i = 0; i < 2; i++) {
                double px = getX() + (random.nextDouble() - 0.5) * getBbWidth();
                double py = getY() + random.nextDouble() * getBbHeight();
                double pz = getZ() + (random.nextDouble() - 0.5) * getBbWidth();
                sl.sendParticles(GestaltParticles.GESTALT_ILLUSION.get(), px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        // Void check
        if (getY() < level().getMinBuildHeight() - 16) {
            if (bodyDouble) { this.discard(); } else { GestaltIllusionEvents.expire(this, false); }
            return;
        }

        // Redirect mob aggro every 20 ticks (both illusion and body double)
        if (ageTicks % 20 == 0 && ownerUuid != null) {
            Player owner = level().getPlayerByUUID(ownerUuid);
            if (owner != null) {
                AABB range = getBoundingBox().inflate(24.0);
                level().getEntitiesOfClass(Mob.class, range,
                        mob -> mob.getTarget() == owner || mob.getTarget() == null)
                        .forEach(mob -> mob.setTarget(this));
            }
        }

        if (!bodyDouble) {
            // Expire at lifetime (illusion mode only; body double lifetime controlled by Phase Court)
            if (ageTicks >= GestaltCosts.ILLUSION_LIFETIME) {
                GestaltIllusionEvents.expire(this, true);
            }
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
        builder.define(DATA_BODY_DOUBLE_MODE, false);
        builder.define(DATA_SLOT_MAINHAND, ItemStack.EMPTY);
        builder.define(DATA_SLOT_OFFHAND,  ItemStack.EMPTY);
        builder.define(DATA_SLOT_FEET,     ItemStack.EMPTY);
        builder.define(DATA_SLOT_LEGS,     ItemStack.EMPTY);
        builder.define(DATA_SLOT_CHEST,    ItemStack.EMPTY);
        builder.define(DATA_SLOT_HEAD,     ItemStack.EMPTY);
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

    /** Remove all spawn illusions owned by the given UUID from this level. */
    public static void dismissExistingIllusions(Level level, UUID ownerUuid) {
        if (level.isClientSide) return;
        ((ServerLevel) level).getEntities().getAll().forEach(e -> {
            if (e instanceof SpawnIllusionEntity sie && ownerUuid.equals(sie.getOwnerUuid()))
                sie.discard();
        });
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
        public void start() {
            requestPath();
        }

        @Override
        public void tick() {
            if (!illusion.getNavigation().isInProgress()) {
                requestPath();
            }
        }

        private void requestPath() {
            Vec3 dest = illusion.position().add(illusion.forwardDirection.scale(20.0));
            illusion.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0);
        }
    }
}
