package net.ragdot.gestaltresonance.common.entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.SpillwaysLightManager;

public class TearProjectileEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_URGENCY =
            SynchedEntityData.defineId(TearProjectileEntity.class, EntityDataSerializers.INT);

    private static final Map<Block, Block> CONCRETE_MAP = Map.ofEntries(
            Map.entry(Blocks.WHITE_CONCRETE_POWDER,      Blocks.WHITE_CONCRETE),
            Map.entry(Blocks.ORANGE_CONCRETE_POWDER,     Blocks.ORANGE_CONCRETE),
            Map.entry(Blocks.MAGENTA_CONCRETE_POWDER,    Blocks.MAGENTA_CONCRETE),
            Map.entry(Blocks.LIGHT_BLUE_CONCRETE_POWDER, Blocks.LIGHT_BLUE_CONCRETE),
            Map.entry(Blocks.YELLOW_CONCRETE_POWDER,     Blocks.YELLOW_CONCRETE),
            Map.entry(Blocks.LIME_CONCRETE_POWDER,       Blocks.LIME_CONCRETE),
            Map.entry(Blocks.PINK_CONCRETE_POWDER,       Blocks.PINK_CONCRETE),
            Map.entry(Blocks.GRAY_CONCRETE_POWDER,       Blocks.GRAY_CONCRETE),
            Map.entry(Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.LIGHT_GRAY_CONCRETE),
            Map.entry(Blocks.CYAN_CONCRETE_POWDER,       Blocks.CYAN_CONCRETE),
            Map.entry(Blocks.PURPLE_CONCRETE_POWDER,     Blocks.PURPLE_CONCRETE),
            Map.entry(Blocks.BLUE_CONCRETE_POWDER,       Blocks.BLUE_CONCRETE),
            Map.entry(Blocks.BROWN_CONCRETE_POWDER,      Blocks.BROWN_CONCRETE),
            Map.entry(Blocks.GREEN_CONCRETE_POWDER,      Blocks.GREEN_CONCRETE),
            Map.entry(Blocks.RED_CONCRETE_POWDER,        Blocks.RED_CONCRETE),
            Map.entry(Blocks.BLACK_CONCRETE_POWDER,      Blocks.BLACK_CONCRETE)
    );

    private static final Map<Block, Block> MOSSY_MAP = Map.ofEntries(
            Map.entry(Blocks.COBBLESTONE,        Blocks.MOSSY_COBBLESTONE),
            Map.entry(Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS),
            Map.entry(Blocks.COBBLESTONE_SLAB,   Blocks.MOSSY_COBBLESTONE_SLAB),
            Map.entry(Blocks.COBBLESTONE_WALL,   Blocks.MOSSY_COBBLESTONE_WALL),
            Map.entry(Blocks.STONE_BRICKS,       Blocks.MOSSY_STONE_BRICKS),
            Map.entry(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS),
            Map.entry(Blocks.STONE_BRICK_SLAB,   Blocks.MOSSY_STONE_BRICK_SLAB),
            Map.entry(Blocks.STONE_BRICK_WALL,   Blocks.MOSSY_STONE_BRICK_WALL)
    );

    // Server-only state — not synced
    @Nullable private UUID ownerUUID = null;
    private Vec3 destination = Vec3.ZERO;
    private int lifetimeTicks = 0;
    @Nullable private LivingEntity cachedTarget = null;
    private boolean falling = false;
    private boolean locked = false;
    @Nullable private BlockPos tearLightPos = null;

    // Required by EntityType.Builder
    public TearProjectileEntity(EntityType<? extends TearProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // Spawn constructor — used by SpillwaysPower1B
    public TearProjectileEntity(EntityType<? extends TearProjectileEntity> type, Level level,
                                Vec3 destination, int urgency) {
        super(type, level);
        this.noPhysics = true;
        this.destination = destination;
        this.entityData.set(DATA_URGENCY, urgency);
    }

    public void setOwner(@Nullable Entity entity) {
        this.ownerUUID = entity != null ? entity.getUUID() : null;
    }

    public boolean isOwnedBy(UUID uuid) {
        return uuid.equals(ownerUUID);
    }

    public boolean isLocked() {
        return locked;
    }

    public void lock() {
        this.locked = true;
        this.lifetimeTicks = 0;
        setDeltaMovement(Vec3.ZERO);
    }

    public static void dismissAllOwned(ServerLevel level, UUID ownerUUID) {
        List<TearProjectileEntity> tears = new java.util.ArrayList<>(
                level.getEntities(GestaltEntities.TEAR_PROJECTILE.get(), e -> e.isOwnedBy(ownerUUID)));
        for (TearProjectileEntity tear : tears) {
            tear.discardClean(level);
        }
    }

    @Nullable
    private Entity getOwnerEntity() {
        if (ownerUUID == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(ownerUUID);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_URGENCY, 1);
    }

    public int getUrgency() {
        return entityData.get(DATA_URGENCY);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // Client applies last-synced velocity every tick for smooth interpolation
        if (level().isClientSide()) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }

        if (!(level() instanceof ServerLevel serverLevel)) return;

        lifetimeTicks++;

        if (falling) {
            tickFalling(serverLevel);
            if (!isRemoved()) updateTearLight(serverLevel);
            return;
        }

        // Locked: hover in place, no targeting or interactions, extended lifetime
        if (locked) {
            if (lifetimeTicks > GestaltCosts.TEARS_FOR_FEARS_LOCKED_MAX_LIFETIME) {
                discardClean(serverLevel);
                return;
            }
            double bob = Math.sin(lifetimeTicks * 0.2) * 0.01;
            setDeltaMovement(0, bob, 0);
            move(MoverType.SELF, getDeltaMovement());
            if (!isRemoved()) updateTearLight(serverLevel);
            return;
        }

        if (lifetimeTicks > GestaltCosts.TEARS_FOR_FEARS_MAX_LIFETIME) {
            falling = true;
            return;
        }

        float lifeProgress = Math.min(1.0f, (float) lifetimeTicks / GestaltCosts.TEARS_FOR_FEARS_MAX_LIFETIME);
        float speed = GestaltCosts.TEARS_FOR_FEARS_BASE_SPEED
                + (GestaltCosts.TEARS_FOR_FEARS_MAX_SPEED - GestaltCosts.TEARS_FOR_FEARS_BASE_SPEED) * lifeProgress;
        if (isInWater() || serverLevel.isRainingAt(blockPosition())) {
            speed *= GestaltCosts.TEARS_FOR_FEARS_WATER_SPEED_MULT;
        }

        double bob = Math.sin(lifetimeTicks * 0.2) * 0.01;

        // Fire blocks have absolute top priority — rechecked every tick
        Vec3 firePos = findNearestFireBlock(serverLevel);
        if (firePos != null) {
            lerpMovement(firePos, speed * 1.3f, 0.16, bob);
            if (isRemoved()) return;
            checkEntityContact(serverLevel);
            if (isRemoved()) return;
            checkBlockContact(serverLevel);
            return;
        }

        // Refresh cached entity target when lost, invalidated, or target is already drowning
        if (cachedTarget == null || !cachedTarget.isAlive() || isExcluded(cachedTarget)
                || (isHostile(cachedTarget) && DrowningDamageTracker.isTracked(cachedTarget.getUUID()))) {
            cachedTarget = findBestTarget(serverLevel);
        }

        if (cachedTarget != null) {
            Vec3 eyePos = cachedTarget.getEyePosition();
            if (eyePos.distanceTo(position()) < 0.5) {
                checkEntityContact(serverLevel);
                return;
            }
            lerpMovement(eyePos, speed, 0.14, bob);
        } else {
            // No target — drift slowly toward resting destination
            lerpMovement(destination, speed * 0.36f, 0.05, bob);
        }

        if (isRemoved()) return;
        checkEntityContact(serverLevel);
        if (isRemoved()) return;
        checkBlockContact(serverLevel);
        if (!isRemoved()) updateTearLight(serverLevel);
    }

    private void tickFalling(ServerLevel level) {
        double bob = Math.sin(lifetimeTicks * 0.2) * 0.01;
        Vec3 current = getDeltaMovement();
        setDeltaMovement(
            current.x * 0.95,
            current.y + (-0.02 - current.y) * 0.05 + bob,
            current.z * 0.95
        );
        move(MoverType.SELF, getDeltaMovement());
        if (isRemoved()) return;
        checkEntityContact(level);
        if (isRemoved()) return;
        checkBlockContact(level);
        if (isRemoved()) return;
        // Discard when landing on any solid surface
        if (!level.getBlockState(blockPosition().below()).isAir()) {
            discardClean(level);
        }
    }

    private void lerpMovement(Vec3 target, float speed, double lerpFactor, double bob) {
        Vec3 diff = target.subtract(position());
        Vec3 current = getDeltaMovement();
        if (diff.lengthSqr() > 0.25) {
            Vec3 desired = diff.normalize().scale(speed);
            setDeltaMovement(
                current.x + (desired.x - current.x) * lerpFactor,
                current.y + (desired.y - current.y) * lerpFactor + bob,
                current.z + (desired.z - current.z) * lerpFactor
            );
        } else {
            setDeltaMovement(0, bob, 0);
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    // ── Targeting ─────────────────────────────────────────────────────────────

    @Nullable
    private LivingEntity findBestTarget(ServerLevel level) {
        AABB scanBox = getBoundingBox().inflate(GestaltCosts.TEARS_FOR_FEARS_SCAN_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, scanBox);

        // Priority 1: on fire
        for (LivingEntity e : candidates) {
            if (!isExcluded(e) && e.getRemainingFireTicks() > 0) return e;
        }

        // Priority 2: injured passive
        for (LivingEntity e : candidates) {
            if (!isExcluded(e) && !isHostile(e) && e.getHealth() < e.getMaxHealth()) return e;
        }

        // Priority 3: hostile (skip ones already drowning from another bubble)
        for (LivingEntity e : candidates) {
            if (!isExcluded(e) && isHostile(e) && !DrowningDamageTracker.isTracked(e.getUUID())) return e;
        }

        return null;
    }

    @Nullable
    private Vec3 findNearestFireBlock(ServerLevel level) {
        BlockPos center = blockPosition();
        int r = (int) GestaltCosts.TEARS_FOR_FEARS_SCAN_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (isFireBlock(level.getBlockState(p))) return Vec3.atCenterOf(p);
                }
            }
        }
        return null;
    }

    private static boolean isFireBlock(BlockState state) {
        return state.is(BlockTags.FIRE)
                || state.getBlock() instanceof BaseFireBlock
                || (state.getBlock() instanceof CampfireBlock
                    && state.hasProperty(BlockStateProperties.LIT)
                    && state.getValue(BlockStateProperties.LIT));
    }

    private static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }

    private boolean isExcluded(Entity entity) {
        if (ownerUUID != null && entity.getUUID().equals(ownerUUID)) return true;
        if (entity instanceof Player p) return p.isCreative() || p.isSpectator();
        return false;
    }


    // ── Entity contact ────────────────────────────────────────────────────────

    private void checkEntityContact(ServerLevel level) {
        AABB contactBox = getBoundingBox().inflate(0.1);
        int urgency = getUrgency();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, contactBox)) {
            if (isExcluded(entity)) continue;

            boolean wasOnFire = entity.getRemainingFireTicks() > 0;
            entity.clearFire();

            if (isHostile(entity)) {
                applyHostileEffect(entity, level, urgency);
                discardClean(level);
                return;
            }

            // Passive: only triggers if entity was on fire or injured
            if (wasOnFire || entity.getHealth() < entity.getMaxHealth()) {
                applyPassiveEffect(entity, urgency);
                discardClean(level);
                return;
            }
            // Otherwise pass through — no discard
        }
    }

    private void applyHostileEffect(LivingEntity entity, ServerLevel level, int urgency) {
        long expiry = level.getServer().getTickCount()
                + (long) GestaltCosts.tearsDrownDurationTicks(urgency) + 1L;
        DrowningDamageTracker.apply(entity.getUUID(), expiry);

        if (urgency > 1) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    GestaltCosts.tearsSlownessDurationTicks(urgency),
                    GestaltCosts.tearsSlownessAmplifier(urgency)
            ));
        }
    }

    private void applyPassiveEffect(LivingEntity entity, int urgency) {
        entity.heal(GestaltCosts.TEARS_FOR_FEARS_PASSIVE_HEAL_BASE
                + urgency * GestaltCosts.TEARS_FOR_FEARS_PASSIVE_HEAL_PER_URGENCY);
        entity.addEffect(new MobEffectInstance(
                MobEffects.WATER_BREATHING,
                GestaltCosts.tearsWaterBreathingTicks(urgency),
                0
        ));
    }

    // ── Block contact ─────────────────────────────────────────────────────────

    private void checkBlockContact(ServerLevel level) {
        tryBlockContactAt(level, blockPosition());
    }

    // Returns true if a relevant block type was found (entity may or may not have been discarded)
    private boolean tryBlockContactAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);

        // 1. Lava
        if (fluid.is(FluidTags.LAVA)) {
            level.setBlock(pos,
                    (fluid.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(),
                    Block.UPDATE_ALL);
            discardClean(level);
            return true;
        }

        // 2. Fire / lit campfire
        if (isFireBlock(state)) {
            handleFireBlock(level, pos);
            return true;
        }

        // 3. Sponge
        if (state.is(Blocks.SPONGE)) {
            level.setBlock(pos, Blocks.WET_SPONGE.defaultBlockState(), Block.UPDATE_ALL);
            discardClean(level);
            return true;
        }
        if (state.is(Blocks.WET_SPONGE)) {
            discardClean(level);
            return true;
        }

        // 4. Concrete powder → concrete
        Block concrete = CONCRETE_MAP.get(state.getBlock());
        if (concrete != null) {
            level.setBlock(pos, concrete.defaultBlockState(), Block.UPDATE_ALL);
            discardClean(level);
            return true;
        }

        // 5. Dirt / farmland — 3×3 hydrate + bonemeal; always dissolve on contact
        if (state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND)) {
            handleDirt(level, pos);
            discardClean(level);
            return true;
        }

        // 6. Obsidian → crying obsidian (5% roll)
        if (state.is(Blocks.OBSIDIAN)) {
            if (level.random.nextFloat() < 0.05f) {
                level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                discardClean(level);
            }
            return true;
        }

        // 7. Mossy conversion (5% roll)
        Block mossy = MOSSY_MAP.get(state.getBlock());
        if (mossy != null) {
            if (level.random.nextFloat() < 0.05f) {
                level.setBlock(pos, copySharedProperties(state, mossy.defaultBlockState()), Block.UPDATE_ALL);
                discardClean(level);
            }
            return true;
        }

        return false; // pass through
    }

    private void handleFireBlock(ServerLevel level, BlockPos hitPos) {
        boolean affectedAny = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = hitPos.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.is(BlockTags.FIRE) || s.getBlock() instanceof BaseFireBlock) {
                        level.removeBlock(p, false);
                        affectedAny = true;
                    } else if (s.getBlock() instanceof CampfireBlock
                            && s.hasProperty(BlockStateProperties.LIT)
                            && s.getValue(BlockStateProperties.LIT)) {
                        level.setBlock(p, s.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
                        affectedAny = true;
                    }
                }
            }
        }
        if (affectedAny) discardClean(level);
    }

    private void handleDirt(ServerLevel level, BlockPos hitPos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = hitPos.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);

                if (s.is(Blocks.FARMLAND)) {
                    level.setBlock(p, s.setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_ALL);
                }

                BlockPos above = p.above();
                BlockState aboveState = level.getBlockState(above);
                if (aboveState.getBlock() instanceof BonemealableBlock bm
                        && bm.isValidBonemealTarget(level, above, aboveState)) {
                    bm.performBonemeal(level, level.random, above, aboveState);
                } else if (s.getBlock() instanceof BonemealableBlock selfBm
                        && selfBm.isValidBonemealTarget(level, p, s)) {
                    selfBm.performBonemeal(level, level.random, p, s);
                }
            }
        }
    }

    // ── Block state property copy ─────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copySharedProperties(BlockState from, BlockState to) {
        for (Property<?> prop : from.getProperties()) {
            if (to.hasProperty(prop)) {
                to = applyProperty(from, to, (Property) prop);
            }
        }
        return to;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState from, BlockState to, Property<T> prop) {
        return to.setValue(prop, from.getValue(prop));
    }

    // ── Dynamic lighting ──────────────────────────────────────────────────────

    private void updateTearLight(ServerLevel level) {
        BlockPos currentPos = blockPosition();
        if (currentPos.equals(tearLightPos)) return;

        if (tearLightPos != null) {
            if (level.getBlockState(tearLightPos).is(Blocks.LIGHT)) {
                level.setBlock(tearLightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            tearLightPos = null;
            SpillwaysLightManager.unregisterTearLight(getUUID());
        }

        if (level.getBlockState(currentPos).isAir()) {
            level.setBlock(currentPos,
                Blocks.LIGHT.defaultBlockState()
                    .setValue(BlockStateProperties.LEVEL, GestaltCosts.TEARS_LIGHT_LEVEL),
                Block.UPDATE_CLIENTS);
            tearLightPos = currentPos;
            if (ownerUUID != null) {
                SpillwaysLightManager.registerTearLight(ownerUUID, getUUID(), level.dimension(), currentPos);
            }
        }
    }

    private void discardClean(ServerLevel level) {
        if (tearLightPos != null) {
            if (level.getBlockState(tearLightPos).is(Blocks.LIGHT)) {
                level.setBlock(tearLightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            tearLightPos = null;
            SpillwaysLightManager.unregisterTearLight(getUUID());
        }
        discard();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        tag.putInt("urgency", getUrgency());
        tag.putDouble("destX", destination.x);
        tag.putDouble("destY", destination.y);
        tag.putDouble("destZ", destination.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        ownerUUID = tag.hasUUID("ownerUUID") ? tag.getUUID("ownerUUID") : null;
        entityData.set(DATA_URGENCY, tag.getInt("urgency"));
        destination = new Vec3(tag.getDouble("destX"), tag.getDouble("destY"), tag.getDouble("destZ"));
    }
}
