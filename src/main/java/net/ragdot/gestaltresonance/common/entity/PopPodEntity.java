package net.ragdot.gestaltresonance.common.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltDelayedPlacer;
import net.ragdot.gestaltresonance.common.GestaltBlocks;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.PopSproutTracker;
import net.ragdot.gestaltresonance.common.block.AbstractPopBlock;
import net.ragdot.gestaltresonance.common.block.PopDripBlock;
import net.ragdot.gestaltresonance.common.block.PopSproutBlock;
import net.ragdot.gestaltresonance.common.block.PopSproutBlockEntity;
import net.ragdot.gestaltresonance.common.block.PopVineBlock;

/**
 * Thrown projectile launched by PopPodPower1B.
 *
 * Hit resolution chain (onHitEntity):
 *   owner player               → pass through, no effect
 *   non-LivingEntity           → discard silently
 *   LivingEntity               → scaled explosion, discard
 *
 * Hit resolution chain (onHitBlock):
 *   1. Fluid at hit position:  lava → explosion; water source → PopPad above; flowing → discard
 *   2. Existing pop-block hit: PopSprout → trigger its explosion; others → destroy, no replace
 *   3. Non-full-solid block    → discard silently
 *   4. Face direction:         UP → PopSprout; sides → PopVine; DOWN → PopDrip
 *
 * Tick-based fluid detection catches entry into fluid that bypasses onHitBlock (fluids have
 * no collision shape so the block-hit raycaster skips them).
 */
public class PopPodEntity extends ThrowableProjectile {

    public PopPodEntity(EntityType<? extends PopPodEntity> type, Level level) {
        super(type, level);
    }

    public PopPodEntity(EntityType<? extends PopPodEntity> type, LivingEntity thrower, Level level) {
        super(type, level);
        this.setOwner(thrower);
        this.setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    // ── Fluid detection (tick) ────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!isAlive() || level().isClientSide) return;

        BlockPos pos = blockPosition();
        FluidState fluid = level().getFluidState(pos);

        if (fluid.is(FluidTags.LAVA)) {
            triggerDirectExplosion(Vec3.atCenterOf(pos), getOwnerAsPlayer());
            discard();
        } else if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
            ServerPlayer owner = getOwnerAsPlayer();
            if (owner != null) placePopPad((ServerLevel) level(), pos.above());
            discard();
        }
    }

    // ── Hit resolution ────────────────────────────────────────────────────────

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity target = result.getEntity();

        // Pass through the owning player
        if (target == getOwner()) return;

        // Discard on non-LivingEntity (minecart, boat, armor stand, etc.)
        if (!(target instanceof LivingEntity)) {
            discard();
            return;
        }

        triggerDirectExplosion(result.getLocation(), getOwnerAsPlayer());
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level();
        ServerPlayer owner = getOwnerAsPlayer();
        if (owner == null) { discard(); return; }

        BlockPos hitPos = result.getBlockPos();

        // Step 1: Fluid check (takes priority over block face logic)
        FluidState fluid = serverLevel.getFluidState(hitPos);
        if (fluid.is(FluidTags.LAVA)) {
            triggerDirectExplosion(Vec3.atCenterOf(hitPos), owner);
            discard();
            return;
        }
        if (fluid.is(FluidTags.WATER)) {
            if (fluid.isSource()) {
                placePopPad(serverLevel, hitPos.above());
            }
            discard();
            return;
        }

        // Step 2: Existing pop-block check
        BlockState hitState = serverLevel.getBlockState(hitPos);
        Block hitBlock = hitState.getBlock();
        if (hitBlock instanceof PopSproutBlock) {
            if (serverLevel.getBlockEntity(hitPos) instanceof PopSproutBlockEntity be) {
                be.detonate(serverLevel, hitPos);
            } else {
                serverLevel.removeBlock(hitPos, false);
            }
            discard();
            return;
        }
        if (hitBlock instanceof AbstractPopBlock) {
            // PopPad/PopVine/PopDrip → destroy, no replacement
            serverLevel.removeBlock(hitPos, false);
            discard();
            return;
        }

        // Step 3: Must be a full solid cube
        if (!hitState.isCollisionShapeFullBlock(serverLevel, hitPos)) {
            discard();
            return;
        }

        // Step 4: Face direction
        Direction face = result.getDirection();
        switch (face) {
            case UP -> {
                BlockPos sproutPos = hitPos.above();
                BlockState above = serverLevel.getBlockState(sproutPos);
                if (above.isAir() || above.is(BlockTags.REPLACEABLE)) {
                    if (!above.isAir()) serverLevel.removeBlock(sproutPos, false);
                    placePopSprout(serverLevel, sproutPos, owner);
                }
            }
            case DOWN -> placePopDrip(serverLevel, hitPos.below(), owner);
            default   -> placePopVine(serverLevel, hitPos.relative(face), face.getOpposite());
        }
        discard();
    }

    // ── Block placement helpers ───────────────────────────────────────────────

    private void placePopPad(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return;
        if (!level.getFluidState(pos.below()).is(FluidTags.WATER)) return;
        level.setBlock(pos, GestaltBlocks.POP_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private void placePopVine(ServerLevel level, BlockPos start, Direction facing) {
        BlockState vineState = GestaltBlocks.POP_VINE.get().defaultBlockState().setValue(PopVineBlock.FACING, facing);
        for (int i = 0; i < 3; i++) {
            BlockPos vinePos = start.below(i);
            BlockState at = level.getBlockState(vinePos);
            if (!at.isAir() && !at.is(BlockTags.REPLACEABLE)) break;
            if (!level.getBlockState(vinePos.relative(facing)).isFaceSturdy(level, vinePos.relative(facing), facing.getOpposite())) break;
            GestaltDelayedPlacer.schedule(level, vinePos, vineState, facing, (long) i * 10);
        }
    }

    private void placePopDrip(ServerLevel level, BlockPos start, ServerPlayer owner) {
        List<BlockPos> toPlace = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BlockPos p = start.below(i);
            BlockState at = level.getBlockState(p);
            if (!at.isAir() && !at.is(BlockTags.REPLACEABLE)) break;
            FluidState fs = level.getFluidState(p);
            if (fs.is(FluidTags.LAVA)) break;
            if (at.is(net.minecraft.world.level.block.Blocks.FIRE)
                    || at.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) break;
            toPlace.add(p);
        }
        int ownerLevel = owner.getData(net.ragdot.gestaltresonance.common.GestaltAttachments.PLAYER_GESTALT_STATE.get()).getGestaltLevel();
        for (int i = 0; i < toPlace.size(); i++) {
            boolean isEnd = (i == toPlace.size() - 1);
            BlockState state = GestaltBlocks.POP_DRIP.get().defaultBlockState()
                    .setValue(PopDripBlock.END, isEnd);
            if (isEnd) {
                GestaltDelayedPlacer.scheduleWithOwner(level, toPlace.get(i), state, null,
                        (long) i * 10, owner.getUUID(), ownerLevel);
            } else {
                GestaltDelayedPlacer.schedule(level, toPlace.get(i), state, null, (long) i * 10);
            }
        }
    }

    private void placePopSprout(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        PlayerGestaltState state = owner.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        PopSproutTracker.get(level.getServer())
                .addSprout(level.getServer(), owner.getUUID(), level.dimension(), pos, state.getGestaltLevel());
        level.setBlock(pos, GestaltBlocks.POP_SPROUT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PopSproutBlockEntity be) {
            be.setOwner(owner.getUUID());
        }
        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
    }

    // ── Explosion helper ──────────────────────────────────────────────────────

    private void triggerDirectExplosion(Vec3 center, @Nullable ServerPlayer owner) {
        int gestaltLevel = 1;
        if (owner != null) {
            gestaltLevel = owner.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get()).getGestaltLevel();
        }
        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.POWER_1B_EXPLOSION_BASE_RADIUS, gestaltLevel);
        float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.POWER_1B_EXPLOSION_BASE_DAMAGE, gestaltLevel);
        DamageSource src = owner != null
                ? GestaltDamageTypes.gestalt(level(), owner)
                : level().damageSources().source(GestaltDamageTypes.GESTALT, null, null);
        GestaltExplosionUtil.detonate(level(), center, radius, damage, src, null);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    @Nullable
    private ServerPlayer getOwnerAsPlayer() {
        Entity owner = getOwner();
        return owner instanceof ServerPlayer sp ? sp : null;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}
}
