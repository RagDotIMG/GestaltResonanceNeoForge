package net.ragdot.gestaltresonance.common.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltCosts;

public class PrimedBlockEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(PrimedBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.defineId(PrimedBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> DATA_SUPPRESS_FLASH = SynchedEntityData.defineId(PrimedBlockEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private LivingEntity owner;

    public PrimedBlockEntity(EntityType<? extends PrimedBlockEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public PrimedBlockEntity(Level level, double x, double y, double z, @Nullable LivingEntity owner, BlockState blockState) {
        this(GestaltEntities.PRIMED_BLOCK.get(), level);
        this.setPos(x, y, z);
        double d0 = level.random.nextDouble() * (Math.PI * 2);
        this.setDeltaMovement(-Math.sin(d0) * 0.02, 0.2, -Math.cos(d0) * 0.02);
        this.setFuse(GestaltCosts.POWER_1S_FUSE_TICKS);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.owner = owner;
        this.setBlockState(blockState);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE_ID, GestaltCosts.POWER_1S_FUSE_TICKS);
        builder.define(DATA_BLOCK_STATE_ID, Blocks.TNT.defaultBlockState());
        builder.define(DATA_SUPPRESS_FLASH, false);
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -this.getDefaultGravity(), 0.0));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }

        int i = this.getFuse() - 1;
        this.setFuse(i);
        if (i <= 0) {
            this.discard();
            if (!this.level().isClientSide) {
                this.explode();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    public void setSuppressFlash(boolean val) {
        this.entityData.set(DATA_SUPPRESS_FLASH, val);
    }

    public boolean isSuppressFlash() {
        return this.entityData.get(DATA_SUPPRESS_FLASH);
    }

    protected void explode() {
        if (this.isSuppressFlash()) {
            if (!this.level().isClientSide) explodeCustom((ServerLevel) this.level());
        } else {
            this.level().explode(this, this.getX(), this.getY(0.0625), this.getZ(), GestaltCosts.POWER_1S_EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
        }
    }

    private void explodeCustom(ServerLevel level) {
        Vec3 center = new Vec3(this.getX(), this.getY(0.0625), this.getZ());
        float radius = GestaltCosts.POWER_1S_BREAK_CORE_EXPLOSION_POWER;

        // Break blocks in sphere — skip indestructible (destroySpeed < 0 == bedrock etc.)
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    BlockPos bp = origin.offset(dx, dy, dz);
                    BlockState bs = level.getBlockState(bp);
                    if (!bs.isAir() && bs.getDestroySpeed(level, bp) >= 0.0f) {
                        level.removeBlock(bp, false);
                    }
                }
            }
        }

        // Entity damage + custom particles/sound — no vanilla explosion packet, no flash
        DamageSource src = (this.owner instanceof ServerPlayer sp)
                ? GestaltDamageTypes.gestalt(level, sp)
                : level.damageSources().explosion(null, this.owner);
        GestaltExplosionUtil.detonate(level, center, radius,
                GestaltCosts.POWER_1S_EXPLOSION_BASE_DAMAGE, src, null, this.owner);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("Fuse", (short)this.getFuse());
        tag.put("BlockState", NbtUtils.writeBlockState(this.getBlockState()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setFuse(tag.getShort("Fuse"));
        if (tag.contains("BlockState", 10)) {
            this.setBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState")));
        }
    }

    @Nullable
    public LivingEntity getOwner() {
        return this.owner;
    }

    public void setFuse(int fuse) {
        this.entityData.set(DATA_FUSE_ID, fuse);
    }

    public int getFuse() {
        return this.entityData.get(DATA_FUSE_ID);
    }

    public void setBlockState(BlockState state) {
        this.entityData.set(DATA_BLOCK_STATE_ID, state);
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_BLOCK_STATE_ID);
    }
}
