package net.ragdot.gestaltresonance.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;

/**
 * Base for all pop-pod placed blocks.
 * Shared behaviour: explode when adjacent lava/fire is detected, or when hit with flint-and-steel.
 * Explosions at this layer use flat base values (no level scaling — owner info not stored here).
 */
public abstract class AbstractPopBlock extends Block {

    protected AbstractPopBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
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

    /** Anonymous explosion — base values only, no gestalt-level scaling. */
    protected void triggerExplosion(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        Vec3 center = Vec3.atCenterOf(pos);
        level.removeBlock(pos, false);
        GestaltExplosionUtil.detonate(
                level, center,
                GestaltCosts.POWER_1B_EXPLOSION_BASE_RADIUS,
                GestaltCosts.POWER_1B_EXPLOSION_BASE_DAMAGE,
                level.damageSources().source(GestaltDamageTypes.GESTALT, null, null),
                null);
    }
}
