package net.ragdot.gestaltresonance.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hanging ceiling vine planted by PopPodEntity on a ceiling impact.
 * Extends up to 4 blocks downward from the ceiling; END=true marks the lowest block.
 */
public class PopDripBlock extends AbstractPopBlock {

    public static final BooleanProperty END = BooleanProperty.create("end");

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public PopDripBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(END, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(END);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }
}
