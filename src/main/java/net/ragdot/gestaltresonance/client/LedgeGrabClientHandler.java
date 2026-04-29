package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.StartLedgeGrabC2S;
import net.ragdot.gestaltresonance.common.network.StopLedgeGrabC2S;

import javax.annotation.Nullable;

/**
 * Client-side ledge grab intent detection.
 * Scans for ledge candidates when SPACE is pressed while airborne,
 * sends C2S packets to start/stop ledge grab.
 *
 * SPACE edge detection: ledge grab can ONLY start on a fresh SPACE press
 * while airborne. Holding SPACE from the initial ground jump never triggers it.
 */
public class LedgeGrabClientHandler {


    private static boolean wasSpaceDown = false;
    private static boolean sentStartPacket = false;
    // True if the current SPACE hold originated while the player was on the ground (i.e. a jump)
    private static boolean pressStartedOnGround = false;

    /**
     * Called every client tick from GestaltKeybinds.
     * Checks if SPACE is held and conditions are met for ledge grab.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean spaceDown = mc.options.keyJump.isDown();
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        

        // If currently ledge-grabbing, freeze all client-side movement and handle space release
        if (state.isLedgeGrabbing()) {
            player.input.leftImpulse = 0;
            player.input.forwardImpulse = 0;
            player.input.up = false;
            player.input.down = false;
            player.input.left = false;
            player.input.right = false;
            player.input.jumping = false;
            player.input.shiftKeyDown = false;
            player.setDeltaMovement(Vec3.ZERO);

            // Check if space was just released -> send stop packet
            if (!spaceDown && wasSpaceDown) {
                PacketDistributor.sendToServer(new StopLedgeGrabC2S());
                sentStartPacket = false;
                pressStartedOnGround = false;
            }
            wasSpaceDown = spaceDown;
            return;
        }

        // Track fresh press: if space just went down, record whether player is on ground
        if (spaceDown && !wasSpaceDown) {
            pressStartedOnGround = player.onGround();
        }

        // If space released, reset tracking
        if (!spaceDown) {
            sentStartPacket = false;
            pressStartedOnGround = false;
            wasSpaceDown = false;
            return;
        }

        // --- SPACE is down below this point ---

        // If currently grabbing, just hold (release handled above)
        if (sentStartPacket && state.isLedgeGrabbing()) {
            wasSpaceDown = spaceDown;
            return;
        }

        // If we sent start but server hasn't confirmed yet, wait
        if (sentStartPacket && !state.isLedgeGrabbing()) {
            wasSpaceDown = spaceDown;
            return;
        }

        // Only attempt ledge grab on a FRESH press while airborne
        // (not the same press that started a ground jump)
        boolean freshAirbornePress = !wasSpaceDown && !pressStartedOnGround;

        if (!freshAirbornePress || sentStartPacket) {
            wasSpaceDown = spaceDown;
            return;
        }

        // Preconditions: gestalt summoned, airborne, not in water
        if (!state.isSummoned()) {
            wasSpaceDown = spaceDown;
            return;
        }
        if (player.onGround() || player.isInWater() || player.isSwimming()) {
            wasSpaceDown = spaceDown;
            return;
        }

        // Must be falling or near-zero vertical speed (not ascending fast from a jump)
        double vy = player.getDeltaMovement().y;
        if (vy > 0.1) {
            wasSpaceDown = spaceDown;
            return;
        }

        // Scan for a valid ledge candidate
        LedgeCandidate candidate = findLedgeCandidate(player, mc.level);
        if (candidate != null) {
            PacketDistributor.sendToServer(new StartLedgeGrabC2S(candidate.pos, candidate.face));
            sentStartPacket = true;
        }

        wasSpaceDown = spaceDown;
    }

    /**
     * Scan blocks in front of the player (based on look direction) for a valid ledge.
     * Returns the best candidate or null.
     */
    @Nullable
    private static LedgeCandidate findLedgeCandidate(LocalPlayer player, Level level) {
        Vec3 pos = player.position();
        double eyeY = player.getEyeY();

        // Get the horizontal look direction (quantized to the 4 cardinal directions)
        float yaw = player.getYRot();
        Direction lookDir = Direction.fromYRot(yaw);

        // Scan up to 2 blocks in front, checking for a solid block with air above
        for (int dist = 1; dist <= 2; dist++) {
            BlockPos checkPos = player.blockPosition().relative(lookDir, dist);

            // Check at eye level and above (up to ~1 block above player)
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos ledgePos = checkPos.offset(0, dy, 0);

                // Adjust: we want the ledge block to be roughly at chest/eye height
                double ledgeTopY = ledgePos.getY() + 1.0;
                if (eyeY > ledgeTopY + 0.5 || eyeY < ledgeTopY - 3.5) continue;

                BlockState blockState = level.getBlockState(ledgePos);
                if (blockState.isAir()) continue;
                if (blockState.getCollisionShape(level, ledgePos).isEmpty()) continue;

                // Block above must be air/passable
                BlockPos above = ledgePos.above();
                if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) continue;

                // Two above must also be clear
                BlockPos twoAbove = above.above();
                if (!level.getBlockState(twoAbove).getCollisionShape(level, twoAbove).isEmpty()) continue;

                // The face toward the player (opposite of look direction)
                Direction face = lookDir.getOpposite();

                // Horizontal distance sanity check
                double ledgeCenterX = ledgePos.getX() + 0.5;
                double ledgeCenterZ = ledgePos.getZ() + 0.5;
                double hDist = Math.sqrt(Math.pow(pos.x - ledgeCenterX, 2) + Math.pow(pos.z - ledgeCenterZ, 2));
                if (hDist > 3.0) continue;

                return new LedgeCandidate(ledgePos, face);
            }
        }

        return null;
    }

    private record LedgeCandidate(BlockPos pos, Direction face) {}
}
