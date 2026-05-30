package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import javax.annotation.Nullable;

/**
 * Server-authoritative wall slide logic.
 *
 * Trigger conditions (checked each tick):
 *   - Gestalt summoned + action IDLE
 *   - Airborne + falling (dy < 0) + not in water
 *   - Sneak held
 *   - Solid 2-block-tall wall face within DETECTION_RANGE in any cardinal direction
 *   - Not ledge grabbing
 *   - canAttachToWall = true
 *
 * Speed ramp:
 *   - 0 – CONSTANT_PHASE_END blocks: constant INITIAL_SPEED
 *   - CONSTANT_PHASE_END – ACCEL_END blocks: exponential ramp to MAX_SPEED
 *   - AUTO-DETACH at MAX_DISTANCE; prevents re-attach until player lands
 */
public class WallSlideLogic {

    private static final double DETECTION_RANGE    = 1.5;
    private static final double INITIAL_SPEED      = 0.07;
    private static final double MAX_SPEED          = 0.8;
    private static final double CONSTANT_PHASE_END = 2.5;
    private static final double ACCEL_END          = 10.0;
    private static final double MAX_DISTANCE       = 11.0;

    private static final Direction[] HORIZONTAL_DIRS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public static void tickPlayer(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Reset canAttachToWall on landing
        if (player.onGround() && !state.canAttachToWall()) {
            state.setCanAttachToWall(true);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        }

        if (state.isWallSliding()) {
            tickSlide(player, state);
            return;
        }

        // Initiation checks
        if (!state.isSummoned()) return;
        if (state.getAction() != GestaltAction.IDLE) return;
        if (player.onGround() || player.isInWater() || player.isSwimming()) return;
        if (player.onClimbable()) return;
        if (state.isLedgeGrabbing()) return;
        if (!state.canAttachToWall()) return;
        if (player.getDeltaMovement().y >= 0) return;
        if (!player.isShiftKeyDown()) return;

        WallCandidate candidate = findWallCandidate(player);
        if (candidate == null) return;

        state.startWallSlide(candidate.face);
        player.setNoGravity(true);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncWallSlideToTracking(player, true, candidate.face);
        GestaltResonance.LOGGER.debug("Wall slide started for {} against face {}", player.getName().getString(), candidate.face);
    }

    private static void tickSlide(ServerPlayer player, PlayerGestaltState state) {
        // Abort: gestalt desummoned
        if (!state.isSummoned()) { abortSlide(player, state, true); return; }
        // Abort: sneak released
        if (!player.isShiftKeyDown()) { abortSlide(player, state, true); return; }
        // Abort: in water or on a climbable block
        if (player.isInWater() || player.isSwimming()) { abortSlide(player, state, true); return; }
        if (player.onClimbable()) { abortSlide(player, state, true); return; }
        // Abort: on ground
        if (player.onGround()) { abortSlide(player, state, true); return; }

        Direction face = state.getWallSlideFace();
        if (face == null) { abortSlide(player, state, true); return; }

        Level level = player.level();
        Direction wallDir = face.getOpposite();
        BlockPos wallFeet = player.blockPosition().relative(wallDir, 1);
        BlockPos wallHead = wallFeet.above();
        boolean feetSolid = isSolid(level, wallFeet);
        boolean headSolid = isSolid(level, wallHead);

        if (!feetSolid && !headSolid) {
            // Wall removed
            abortSlide(player, state, true);
            return;
        }

        double distance = state.getWallSlideDistance();

        // Auto-detach at max distance
        if (distance >= MAX_DISTANCE) {
            GestaltResonance.LOGGER.debug("Wall slide max distance reached for {}", player.getName().getString());
            player.setNoGravity(false);
            state.setCanAttachToWall(false);
            state.clearWallSlide();
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncWallSlideToTracking(player, false, null);
            return;
        }

        double speed = computeSpeed(distance);
        state.setWallSlideDistance(distance + speed);

        // Snap player horizontally against the wall
        float halfWidth = player.getBbWidth() / 2.0f;
        Vec3 pos = player.position();
        BlockPos refBlock = feetSolid ? wallFeet : wallHead;
        double snapX = pos.x, snapZ = pos.z;

        if (wallDir.getAxis() == Direction.Axis.X) {
            // Wall is east or west; face in X-direction
            double wallFaceX = refBlock.getX() + 0.5 - wallDir.getStepX() * 0.5;
            snapX = wallFaceX - wallDir.getStepX() * (halfWidth + 0.001);
        } else {
            // Wall is north or south; face in Z-direction
            double wallFaceZ = refBlock.getZ() + 0.5 - wallDir.getStepZ() * 0.5;
            snapZ = wallFaceZ - wallDir.getStepZ() * (halfWidth + 0.001);
        }

        player.setPos(snapX, pos.y, snapZ);
        player.setDeltaMovement(0.0, -speed, 0.0);
        player.fallDistance = 0;
        player.hurtMarked = true;
        player.xxa = 0;
        player.zza = 0;
        player.yya = 0;
        player.setSpeed(0);

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
    }

    private static void abortSlide(ServerPlayer player, PlayerGestaltState state, boolean allowReattach) {
        GestaltResonance.LOGGER.debug("Wall slide aborted for {}", player.getName().getString());
        player.setNoGravity(false);
        if (!allowReattach) state.setCanAttachToWall(false);
        state.clearWallSlide();
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncWallSlideToTracking(player, false, null);
    }

    @Nullable
    private static WallCandidate findWallCandidate(ServerPlayer player) {
        Level level = player.level();
        float halfWidth = player.getBbWidth() / 2.0f;
        Vec3 pos = player.position();

        for (Direction dir : HORIZONTAL_DIRS) {
            BlockPos wallFeet = player.blockPosition().relative(dir, 1);
            BlockPos wallHead = wallFeet.above();
            if (!isSolid(level, wallFeet) || !isSolid(level, wallHead)) continue;

            double gap;
            if (dir.getAxis() == Direction.Axis.X) {
                // Face of wall toward player (low-X for EAST dir, high-X for WEST dir)
                double wallFaceX = wallFeet.getX() + 0.5 - dir.getStepX() * 0.5;
                gap = Math.abs(wallFaceX - (pos.x + dir.getStepX() * halfWidth));
            } else {
                double wallFaceZ = wallFeet.getZ() + 0.5 - dir.getStepZ() * 0.5;
                gap = Math.abs(wallFaceZ - (pos.z + dir.getStepZ() * halfWidth));
            }

            if (gap <= DETECTION_RANGE) {
                return new WallCandidate(dir.getOpposite());
            }
        }
        return null;
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty() && !state.is(BlockTags.CLIMBABLE);
    }

    /**
     * Speed ramp: constant INITIAL_SPEED for the first CONSTANT_PHASE_END blocks,
     * then an exponential ramp to MAX_SPEED at ACCEL_END blocks.
     */
    private static double computeSpeed(double distance) {
        if (distance < CONSTANT_PHASE_END) {
            return INITIAL_SPEED;
        } else if (distance < ACCEL_END) {
            double t = (distance - CONSTANT_PHASE_END) / (ACCEL_END - CONSTANT_PHASE_END);
            return INITIAL_SPEED * Math.pow(MAX_SPEED / INITIAL_SPEED, t);
        } else {
            return MAX_SPEED;
        }
    }

    private record WallCandidate(Direction face) {}
}
