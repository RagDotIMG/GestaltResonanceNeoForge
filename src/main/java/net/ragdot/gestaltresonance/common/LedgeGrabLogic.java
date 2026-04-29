package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ragdot.gestaltresonance.GestaltResonance;

import javax.annotation.Nullable;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

/**
 * Server-authoritative ledge grab logic.
 * Validates ledge candidates, ticks hanging state, and handles mantle boost.
 */
public class LedgeGrabLogic {

    private static final int MAX_GRAB_TICKS = 200; // 10 seconds
    private static final int MANTLE_TICKS = 8;
    private static final double HANG_OFFSET_FROM_WALL = 1.3;
    private static final double HANG_Y_BELOW_LEDGE_TOP = 2.3;
    private static final double MAX_DISTANCE_FROM_LEDGE = 3.0;
    private static final double MANTLE_UP_SPEED = 0.55;
    private static final double MANTLE_FORWARD_SPEED = 0.25;
    private static final double COLLISION_PUSH_STEP = 0.1;
    private static final int MAX_COLLISION_PUSH_STEPS = 10;

    /**
     * Validate a ledge grab candidate on the server.
     * Returns the computed anchor position if valid, null otherwise.
     */
    public static Vec3 validateLedge(ServerPlayer player, BlockPos ledgePos, Direction face) {
        Level level = player.level();

        // Gestalt must be summoned
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return null;

        // Player must be airborne, not in water
        if (player.onGround() || player.isInWater() || player.isSwimming()) return null;

        // The ledge block must be solid on the face toward the player
        BlockState blockState = level.getBlockState(ledgePos);
        if (blockState.isAir()) return null;
        VoxelShape shape = blockState.getCollisionShape(level, ledgePos);
        if (shape.isEmpty()) return null;

        // The block above the ledge must be passable (where the player will mantle to)
        BlockPos aboveLedge = ledgePos.above();
        BlockState aboveState = level.getBlockState(aboveLedge);
        if (!aboveState.getCollisionShape(level, aboveLedge).isEmpty()) return null;

        // Two blocks above ledge must also be clear (player height)
        BlockPos twoAbove = aboveLedge.above();
        BlockState twoAboveState = level.getBlockState(twoAbove);
        if (!twoAboveState.getCollisionShape(level, twoAbove).isEmpty()) return null;

        // face must be horizontal
        if (face.getAxis() == Direction.Axis.Y) return null;

        // Player must be within reasonable distance
        double ledgeTopY = ledgePos.getY() + 1.5;
        double playerEyeY = player.getEyeY();
        // Allow grabbing ledges that are up to ~2 blocks above the player vertically
        // (player eye can be up to ~3.5 below the ledge top), and up to ~1 block above eye level.
        if (playerEyeY > ledgeTopY + 1.5) return null;
        if (playerEyeY < ledgeTopY - 3.5) return null;

        // Horizontal distance check
        Vec3 playerPos = player.position();
        double ledgeCenterX = ledgePos.getX() + 0.5;
        double ledgeCenterZ = ledgePos.getZ() + 0.5;
        double hDist = Math.sqrt(Math.pow(playerPos.x - ledgeCenterX, 2) + Math.pow(playerPos.z - ledgeCenterZ, 2));
        if (hDist > MAX_DISTANCE_FROM_LEDGE) return null;

        // Compute anchor: slightly away from the wall face, Y at ledge top minus offset
        double anchorX = ledgeCenterX + face.getStepX() * HANG_OFFSET_FROM_WALL;
        double anchorY = ledgeTopY - HANG_Y_BELOW_LEDGE_TOP;
        double anchorZ = ledgeCenterZ + face.getStepZ() * HANG_OFFSET_FROM_WALL;

        Vec3 anchor = new Vec3(anchorX, anchorY, anchorZ);

        // Collision safety: ensure player AABB at anchor doesn't collide
        anchor = adjustAnchorForCollision(player, anchor, face, level);
        if (anchor == null) return null;

        return anchor;
    }

    /**
     * Called every server tick for each player currently ledge-grabbing.
     * Maintains hang position, checks abort conditions.
     */
    public static void tickPlayer(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        // Handle mantle magnet ticks (after release)
        if (state.getMantleTicks() > 0) {
            tickMantle(player, state);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            return;
        }

        if (!state.isLedgeGrabbing()) return;

        // Abort checks
        if (!state.isSummoned()) {
            abortGrab(player, state, "gestalt desummoned");
            return;
        }

        BlockPos ledgePos = state.getLedgePos();
        Vec3 anchor = state.getAnchorPos();
        if (ledgePos == null || anchor == null) {
            abortGrab(player, state, "null ledge data");
            return;
        }

        // Check block still solid
        Level level = player.level();
        BlockState blockState = level.getBlockState(ledgePos);
        if (blockState.getCollisionShape(level, ledgePos).isEmpty()) {
            abortGrab(player, state, "ledge block removed");
            return;
        }

        // Distance check
        double dist = player.position().distanceTo(anchor);
        if (dist > MAX_DISTANCE_FROM_LEDGE) {
            abortGrab(player, state, "too far from ledge");
            return;
        }

        // Timeout
        int ticks = state.getTicksGrabbing() + 1;
        if (ticks > MAX_GRAB_TICKS) {
            abortGrab(player, state, "timeout");
            return;
        }
        state.setTicksGrabbing(ticks);

        // Collision safety: re-validate anchor each tick
        Vec3 safeAnchor = adjustAnchorForCollision(player, anchor, state.getLedgeFace(), level);
        if (safeAnchor == null) {
            GestaltResonance.LOGGER.debug("Ledge grab cancelled for {}: anchor collides with blocks", player.getName().getString());
            player.setNoGravity(false);
            state.clearLedgeGrab();
            player.setDeltaMovement(0, MANTLE_UP_SPEED, 0);
            player.fallDistance = 0;
            player.hurtMarked = true;
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncLedgeGrabToTracking(player, false, null, null);
            return;
        }

        // Hard-lock: teleport player to exact hang position every tick
        // Disable gravity to prevent vanilla physics from pushing the player
        // between ticks (e.g. lanterns or other partial blocks below the hang point)
        player.setNoGravity(true);
        player.setPos(safeAnchor.x, safeAnchor.y, safeAnchor.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.hurtMarked = true; // force velocity sync to client

        // Freeze player input: zero out any movement the client is trying to apply
        player.xxa = 0;
        player.zza = 0;
        player.yya = 0;
        player.setSpeed(0);

        // Update anchor if it was adjusted
        if (!safeAnchor.equals(anchor)) {
            state.setAnchorPos(safeAnchor);
        }

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
    }

    /**
     * Handle mantle boost after releasing ledge grab.
     */
    public static void startMantle(ServerPlayer player, PlayerGestaltState state) {
        BlockPos ledgePos = state.getLedgePos();
        Direction face = state.getLedgeFace();
        if (ledgePos == null || face == null) {
            state.clearLedgeGrab();
            return;
        }

        // Check if landing spot is blocked
        BlockPos landingPos = ledgePos.above();
        Level level = player.level();
        if (!level.getBlockState(landingPos).getCollisionShape(level, landingPos).isEmpty()) {
            // Landing blocked, just drop
            GestaltResonance.LOGGER.debug("Ledge grab mantle: landing blocked, dropping");
            state.clearLedgeGrab();
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncLedgeGrabToTracking(player, false, null, null);
            return;
        }

        // Compute mantle target (top of ledge block + 0.1 for safety)
        Vec3 mantleTarget = new Vec3(
                ledgePos.getX() + 0.5,
                ledgePos.getY() + 1.0 + 0.1,
                ledgePos.getZ() + 0.5
        );

        // Re-enable gravity before mantle boost
        player.setNoGravity(false);

        // Apply initial boost
        double boostX = face.getStepX() * -MANTLE_FORWARD_SPEED; // toward the wall (opposite of face)
        double boostZ = face.getStepZ() * -MANTLE_FORWARD_SPEED;
        player.setDeltaMovement(boostX, MANTLE_UP_SPEED, boostZ);
        player.hurtMarked = true;
        player.fallDistance = 0;

        state.clearLedgeGrab();
        state.setMantleTicks(MANTLE_TICKS);
        state.setMantleTarget(mantleTarget);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        GestaltNetworking.syncLedgeGrabToTracking(player, false, null, null);
        GestaltResonance.LOGGER.debug("Ledge grab mantle started for {}", player.getName().getString());
    }

    private static void tickMantle(ServerPlayer player, PlayerGestaltState state) {
        int remaining = state.getMantleTicks() - 1;
        state.setMantleTicks(remaining);
        player.fallDistance = 0;

        Vec3 target = state.getMantleTarget();
        if (target != null && remaining > 0) {
            // Gentle magnet pull toward landing spot
            Vec3 pos = player.position();
            double factor = 0.1;
            double dx = (target.x - pos.x) * factor;
            double dz = (target.z - pos.z) * factor;
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(vel.x + dx, vel.y, vel.z + dz);
            player.hurtMarked = true;
        }

        if (remaining <= 0) {
            state.setMantleTarget(null);
        }
    }

    /**
     * Adjusts the anchor position outward along the face normal if the player AABB would collide.
     * Returns the adjusted anchor, or null if collision cannot be resolved.
     */
    @Nullable
    private static Vec3 adjustAnchorForCollision(ServerPlayer player, Vec3 anchor, Direction face, Level level) {
        float halfWidth = player.getBbWidth() / 2.0F;
        float height = player.getBbHeight();

        for (int step = 0; step <= MAX_COLLISION_PUSH_STEPS; step++) {
            double pushDist = step * COLLISION_PUSH_STEP;
            double ax = anchor.x + face.getStepX() * pushDist;
            double ay = anchor.y;
            double az = anchor.z + face.getStepZ() * pushDist;

            AABB testBox = new AABB(
                    ax - halfWidth, ay, az - halfWidth,
                    ax + halfWidth, ay + height, az + halfWidth
            );

            if (level.noCollision(testBox)) {
                return new Vec3(ax, ay, az);
            }
        }
        return null; // could not resolve collision
    }

    private static void abortGrab(ServerPlayer player, PlayerGestaltState state, String reason) {
        GestaltResonance.LOGGER.debug("Ledge grab aborted for {}: {}", player.getName().getString(), reason);
        player.setNoGravity(false);
        state.clearLedgeGrab();
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncLedgeGrabToTracking(player, false, null, null);
    }
}
