package net.ragdot.gestaltresonance.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ragdot.gestaltresonance.common.GestaltBlockEntities;

import javax.annotation.Nullable;

/**
 * Hanging ceiling vine planted by PopPodEntity on a ceiling impact.
 * Extends up to 4 blocks downward from the ceiling; END=true marks the lowest block.
 *
 * The END block hosts a {@link PopDripBlockEntity} that scans for nearby hostile mobs
 * and periodically drops {@link net.ragdot.gestaltresonance.common.entity.DripDropEntity}
 * projectiles until its drop limit is exhausted, then schedules the vine for removal.
 */
public class PopDripBlock extends AbstractPopBlock implements EntityBlock {

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

    // ── Removal propagation ───────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this) && state.getValue(END) && level instanceof ServerLevel sl) {
            // Only schedule if drops weren't exhausted (that path already queued removal)
            if (level.getBlockEntity(pos) instanceof PopDripBlockEntity be && be.hasRemainingDrops()) {
                PopDripBlockEntity.scheduleChainRemoval(sl, pos.above());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ── EntityBlock ───────────────────────────────────────────────────────────

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PopDripBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        // Only tick the END block server-side
        if (level.isClientSide || !state.getValue(END)) return null;
        if (type != GestaltBlockEntities.POP_DRIP.get()) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<PopDripBlockEntity>) PopDripBlockEntity::tick;
        return ticker;
    }
}
