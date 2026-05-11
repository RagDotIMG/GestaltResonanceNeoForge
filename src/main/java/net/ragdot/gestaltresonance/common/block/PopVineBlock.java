package net.ragdot.gestaltresonance.common.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

/**
 * Climbable wall panel planted by PopPodEntity on a wall impact.
 * FACING indicates the direction toward the supporting wall block.
 * Three blocks tall; each placed individually by the projectile resolution logic.
 */
public class PopVineBlock extends AbstractPopBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Panel is 2 pixels thick, flush against the FACING side
    private static final Map<Direction, VoxelShape> SHAPES = Util.make(new EnumMap<>(Direction.class), m -> {
        m.put(Direction.NORTH, Block.box(0, 0, 0, 16, 16, 2));
        m.put(Direction.SOUTH, Block.box(0, 0, 14, 16, 16, 16));
        m.put(Direction.EAST,  Block.box(14, 0, 0, 16, 16, 16));
        m.put(Direction.WEST,  Block.box(0, 0, 0, 2, 16, 16));
    });

    public PopVineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }
}
