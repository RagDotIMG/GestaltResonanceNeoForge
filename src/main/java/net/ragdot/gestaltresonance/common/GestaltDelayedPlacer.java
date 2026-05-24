package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.ragdot.gestaltresonance.common.block.PopDripBlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Queues block placements and removals to fire on future server ticks, each playing
 * BONE_MEAL_USE on landing/removal. Used by PopVine and PopDrip for cascading grow/wilt effects.
 *
 * Call {@link #tick(MinecraftServer)} from the server tick event.
 */
public final class GestaltDelayedPlacer {

    private record Entry(
            ResourceKey<Level> dim,
            BlockPos pos,
            BlockState state,
            long fireTick,
            @Nullable Direction vineWallFacing,
            @Nullable UUID ownerUuid,
            int gestaltLevel
    ) {}

    private record RemovalEntry(ResourceKey<Level> dim, BlockPos pos, long fireTick) {}

    private static final List<Entry> pending         = new ArrayList<>();
    private static final List<RemovalEntry> removals = new ArrayList<>();

    /**
     * Schedule a block placement. At fireTick the position is validated, the block is placed,
     * and BONE_MEAL_USE plays.
     *
     * @param vineFacing if non-null, skips placement if the backing wall is no longer solid
     * @param delayTicks ticks from now; 0 fires on the very next tick
     */
    public static void schedule(ServerLevel level, BlockPos pos, BlockState state,
                                @Nullable Direction vineFacing, long delayTicks) {
        scheduleWithOwner(level, pos, state, vineFacing, delayTicks, null, 0);
    }

    /**
     * Like {@link #schedule} but also initialises a {@link PopDripBlockEntity} after placement
     * with the given owner and gestalt level.
     */
    public static void scheduleWithOwner(ServerLevel level, BlockPos pos, BlockState state,
                                         @Nullable Direction vineFacing, long delayTicks,
                                         @Nullable UUID ownerUuid, int gestaltLevel) {
        long fireTick = level.getServer().getTickCount() + delayTicks;
        pending.add(new Entry(level.dimension(), pos, state, fireTick, vineFacing, ownerUuid, gestaltLevel));
    }

    /**
     * Schedule block removal at {@code pos} with a delay. Plays BONE_MEAL_USE when the block is removed.
     */
    public static void scheduleRemoval(ServerLevel level, BlockPos pos, long delayTicks) {
        long fireTick = level.getServer().getTickCount() + delayTicks;
        removals.add(new RemovalEntry(level.dimension(), pos, fireTick));
    }

    /** Called each server tick from {@code GestaltResonance.onServerTick}. */
    public static void tick(MinecraftServer server) {
        if (pending.isEmpty() && removals.isEmpty()) return;
        long now = server.getTickCount();

        pending.removeIf(entry -> {
            if (entry.fireTick() > now) return false;
            ServerLevel level = server.getLevel(entry.dim());
            if (level == null) return true;
            BlockState current = level.getBlockState(entry.pos());
            if (!current.isAir() && !current.is(BlockTags.REPLACEABLE)) return true;
            if (entry.vineWallFacing() != null
                    && !level.getBlockState(entry.pos().relative(entry.vineWallFacing())).isFaceSturdy(level, entry.pos().relative(entry.vineWallFacing()), entry.vineWallFacing().getOpposite())) {
                return true;
            }
            if (!current.isAir()) level.removeBlock(entry.pos(), false);
            level.setBlock(entry.pos(), entry.state(), Block.UPDATE_ALL);
            if (entry.ownerUuid() != null
                    && level.getBlockEntity(entry.pos()) instanceof PopDripBlockEntity be) {
                be.init(entry.ownerUuid(), entry.gestaltLevel());
            }
            level.playSound(null, entry.pos(), SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS,
                    1.0f, 0.9f + level.random.nextFloat() * 0.2f);
            return true;
        });

        removals.removeIf(entry -> {
            if (entry.fireTick() > now) return false;
            ServerLevel level = server.getLevel(entry.dim());
            if (level == null) return true;
            if (level.getBlockState(entry.pos()).isAir()) return true;
            level.removeBlock(entry.pos(), false);
            level.playSound(null, entry.pos(), SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS,
                    0.8f, 0.9f + level.random.nextFloat() * 0.2f);
            return true;
        });
    }

    private GestaltDelayedPlacer() {}
}
