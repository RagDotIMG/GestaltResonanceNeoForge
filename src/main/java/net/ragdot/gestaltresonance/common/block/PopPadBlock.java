package net.ragdot.gestaltresonance.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

/**
 * Flat bounce pad planted by PopPodEntity when it enters a water source block.
 * Survives only on top of water sources; breaks if water below is removed.
 * Launches the player using the gestalt throw trajectory on contact. Single use.
 */
public class PopPadBlock extends BushBlock {

    public static final MapCodec<PopPadBlock> CODEC = simpleCodec(PopPadBlock::new);

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1, 15);

    public PopPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PopPadBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos.below());
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ── Bounce ────────────────────────────────────────────────────────────────

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (!(entity instanceof ServerPlayer player)) return;

        PlayerGestaltState pState = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        Vec3 velocity = GestaltThrowEvents.computeThrowVelocity(player, pState);
        player.setDeltaMovement(velocity);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), velocity));
        level.destroyBlock(pos, false);
    }

    // ── Shared pop-block behaviors ────────────────────────────────────────────

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        // BushBlock: breaks if water below is removed
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
        Vec3 center = Vec3.atCenterOf(pos);
        level.removeBlock(pos, false);
        GestaltExplosionUtil.detonate(level, center,
                GestaltCosts.POWER_1B_EXPLOSION_BASE_RADIUS,
                GestaltCosts.POWER_1B_EXPLOSION_BASE_DAMAGE,
                level.damageSources().source(GestaltDamageTypes.GESTALT, null, null), null);
    }
}
