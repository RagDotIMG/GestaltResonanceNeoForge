package net.ragdot.gestaltresonance.common.block;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;

/**
 * Proximity mine planted by PopPodEntity on floor impacts.
 * Explodes when a non-player, non-owned-pet LivingEntity enters the block.
 * Owner UUID stored in block entity for damage attribution and pet exclusion.
 */
public class PopSproutBlock extends BushBlock implements EntityBlock {

    public static final MapCodec<PopSproutBlock> CODEC = simpleCodec(PopSproutBlock::new);

    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 8, 10);

    public PopSproutBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PopSproutBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ── Trigger ───────────────────────────────────────────────────────────────

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (!(entity instanceof LivingEntity)) return;
        if (entity instanceof Player) return;

        // Pet exclusion: owned tameables belonging to the sprout's owner are safe
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            UUID petOwner = tamable.getOwnerUUID();
            if (petOwner != null && level.getBlockEntity(pos) instanceof PopSproutBlockEntity be) {
                if (petOwner.equals(be.getOwnerUuid())) return;
            }
        }

        if (level.getBlockEntity(pos) instanceof PopSproutBlockEntity be) {
            be.detonate((ServerLevel) level, pos);
        } else {
            // Fallback: no block entity data available
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            level.removeBlock(pos, false);
            GestaltExplosionUtil.detonate(level, center,
                    GestaltCosts.POWER_1B_EXPLOSION_BASE_RADIUS,
                    GestaltCosts.POWER_1B_EXPLOSION_BASE_DAMAGE,
                    level.damageSources().source(GestaltDamageTypes.GESTALT, null, null), null);
        }
    }

    // ── Shared pop-block behaviors ────────────────────────────────────────────

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        // BushBlock: breaks if block below is no longer valid
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // Fire/lava adjacency → explosion (only if block still exists after super)
        if (level.isClientSide || !level.getBlockState(pos).is(this)) return;
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.is(Blocks.FIRE) || neighbor.is(Blocks.SOUL_FIRE)
                || neighbor.getFluidState().is(FluidTags.LAVA)) {
            triggerExplosion(level, pos);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            if (!level.isClientSide) {
                triggerExplosion(level, pos);
                if (player instanceof ServerPlayer sp) {
                    stack.hurtAndBreak(1, sp, LivingEntity.getSlotForHand(hand));
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private void triggerExplosion(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof PopSproutBlockEntity be) {
            be.detonate((ServerLevel) level, pos);
        } else {
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            level.removeBlock(pos, false);
            GestaltExplosionUtil.detonate(level, center,
                    GestaltCosts.POWER_1B_EXPLOSION_BASE_RADIUS,
                    GestaltCosts.POWER_1B_EXPLOSION_BASE_DAMAGE,
                    level.damageSources().source(GestaltDamageTypes.GESTALT, null, null), null);
        }
    }

    // ── BlockEntity ───────────────────────────────────────────────────────────

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PopSproutBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return null; // detection is entityInside-based, no ticking needed
    }
}
