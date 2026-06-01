package net.ragdot.gestaltresonance.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.PopSproutTracker;
import net.ragdot.gestaltresonance.common.block.PopSproutBlock;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Body double for Time Phase (3S). AI-driven: walks in the player's initial facing direction,
 * preferring nearby PopSprout blocks. Movement is overridden by a walk target when Time Skip fires.
 */
public class TimePhaseBodyDoubleEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> DATA_OWNER_UUID_STR =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_MAINHAND =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_OFFHAND =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_FEET =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_LEGS =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_CHEST =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SLOT_HEAD =
            SynchedEntityData.defineId(TimePhaseBodyDoubleEntity.class, EntityDataSerializers.ITEM_STACK);

    @Nullable private UUID ownerUuid;

    // Server-side only — initial facing yaw at spawn, walk target set by Time Skip
    float initialYaw = 0f;
    boolean hasWalkTarget = false;
    @Nullable Vec3 walkTarget = null;

    private static final ResourceLocation DESTINATION_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_destination_speed");

    public TimePhaseBodyDoubleEntity(EntityType<? extends TimePhaseBodyDoubleEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public void setInitialYaw(float yaw) {
        this.initialYaw = yaw;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yRotO = yaw;
    }

    public void setWalkTarget(Vec3 target) {
        this.walkTarget = target;
        this.hasWalkTarget = true;
    }

    /**
     * Override MOVEMENT_SPEED so the body double covers `distance` blocks in `ticks` ticks.
     * Applied as a transient ADD_VALUE modifier so the base attribute is unchanged.
     */
    public void applyDestinationSpeed(double distance, int ticks) {
        AttributeInstance attr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null || ticks <= 0) return;
        attr.removeModifier(DESTINATION_SPEED_ID);
        // Each MOVEMENT_SPEED point ≈ 1 block/tick walking; aim slightly hot so pathfinding overhead doesn't cause underrun.
        double target = Math.max(0.05, (distance / ticks) * 1.15);
        double delta = target - attr.getBaseValue();
        if (delta <= 0) return;
        attr.addOrUpdateTransientModifier(new AttributeModifier(
                DESTINATION_SPEED_ID, delta, AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WalkToSetTargetGoal());
        this.goalSelector.addGoal(2, new SeekPopSproutGoal());
        this.goalSelector.addGoal(3, new WalkForwardGoal());
    }

    // ── Inner goals ───────────────────────────────────────────────────────────

    /** Walks to a target position set externally (e.g. from Time Skip). */
    private class WalkToSetTargetGoal extends Goal {
        WalkToSetTargetGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() { return hasWalkTarget && walkTarget != null; }

        @Override
        public boolean canContinueToUse() { return hasWalkTarget && !getNavigation().isDone(); }

        @Override
        public void start() {
            if (walkTarget != null)
                getNavigation().moveTo(walkTarget.x, walkTarget.y, walkTarget.z, 1.0);
        }

        @Override
        public void tick() {
            if (walkTarget != null && getNavigation().isDone())
                getNavigation().moveTo(walkTarget.x, walkTarget.y, walkTarget.z, 1.0);
        }
    }

    /** Finds the nearest PopSprout block in range and navigates toward it. */
    private class SeekPopSproutGoal extends Goal {
        @Nullable private BlockPos target;
        private int searchCooldown = 0;

        SeekPopSproutGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (hasWalkTarget) return false;
            if (level().isClientSide()) return false;
            if (--searchCooldown > 0) return false;
            searchCooldown = 20;
            if (!(level() instanceof ServerLevel sl)) return false;
            MinecraftServer server = sl.getServer();
            if (server == null) return false;
            target = PopSproutTracker.get(server).findNewestInRange(sl, position(), 32.0);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (hasWalkTarget) return false;
            return target != null && level().getBlockState(target).getBlock() instanceof PopSproutBlock;
        }

        @Override
        public void start() {
            if (target != null)
                getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }

        @Override
        public void tick() {
            if (target != null && getNavigation().isDone())
                getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }

        @Override
        public void stop() { target = null; }
    }

    /** Continuously walks in the direction the player was facing at activation. */
    private class WalkForwardGoal extends Goal {
        private int recalcTimer = 0;

        WalkForwardGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() { return !hasWalkTarget && !level().isClientSide(); }

        @Override
        public boolean canContinueToUse() { return !hasWalkTarget; }

        @Override
        public void start() { recalcTarget(); }

        @Override
        public void tick() {
            if (++recalcTimer >= 40 || getNavigation().isDone()) {
                recalcTimer = 0;
                recalcTarget();
            }
        }

        private void recalcTarget() {
            double rad = Math.toRadians(initialYaw);
            double dx = -Math.sin(rad) * 12;
            double dz =  Math.cos(rad) * 12;
            Vec3 dest = position().add(dx, 0, dz);
            getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0);
        }
    }

    // ── Equipment / owner ─────────────────────────────────────────────────────

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        entityData.set(DATA_OWNER_UUID_STR, uuid.toString());
    }

    @Nullable
    public UUID getOwnerUuid() {
        String s = entityData.get(DATA_OWNER_UUID_STR);
        if (s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

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

    // ── Misc overrides ────────────────────────────────────────────────────────

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    // ── Synced data ───────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID_STR, "");
        builder.define(DATA_SLOT_MAINHAND, ItemStack.EMPTY);
        builder.define(DATA_SLOT_OFFHAND,  ItemStack.EMPTY);
        builder.define(DATA_SLOT_FEET,     ItemStack.EMPTY);
        builder.define(DATA_SLOT_LEGS,     ItemStack.EMPTY);
        builder.define(DATA_SLOT_CHEST,    ItemStack.EMPTY);
        builder.define(DATA_SLOT_HEAD,     ItemStack.EMPTY);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("OwnerUUID", ownerUuid);
        tag.putFloat("InitialYaw", initialYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUuid = tag.getUUID("OwnerUUID");
            entityData.set(DATA_OWNER_UUID_STR, ownerUuid.toString());
        }
        if (tag.contains("InitialYaw")) initialYaw = tag.getFloat("InitialYaw");
    }

    /** Remove all Time Phase body doubles owned by the given UUID from this level. */
    public static void dismissExistingDoubles(Level level, UUID ownerUuid) {
        if (level.isClientSide) return;
        ((ServerLevel) level).getEntities().getAll().forEach(e -> {
            if (e instanceof TimePhaseBodyDoubleEntity tpbd && ownerUuid.equals(tpbd.getOwnerUuid()))
                tpbd.discard();
        });
    }
}
