package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity;
import net.ragdot.gestaltresonance.common.network.SoulProjectionActionC2S;

/**
 * Client-side input gating during soul projection:
 *  - Cancel block break, place, and item-use interactions.
 *  - Right-click on an ItemEntity → send PICKUP to the server.
 *  - Right-click on the player's own body double passes through (server handles CLEAN exit).
 *  - In MovementInputUpdateEvent, zero radial-outward movement when at the range boundary
 *    so the player slides along the sphere instead of fighting the server's hard clamp.
 */
public final class SoulProjectionClientInput {

    private SoulProjectionClientInput() {}

    private static boolean isLocalPlayerProjecting() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        return state.isSoulProjecting();
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isLocalPlayerProjecting()) return;
        event.setCanceled(true);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isLocalPlayerProjecting()) return;
        event.setCanceled(true);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isLocalPlayerProjecting()) return;
        event.setCanceled(true);
    }

    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!isLocalPlayerProjecting()) return;

        Minecraft mc = Minecraft.getInstance();
        // If the cursor is on an ItemEntity within ~3 blocks, request a pickup.
        if (mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof ItemEntity item
                && mc.player != null
                && mc.player.distanceToSqr(item) <= 9.0) {
            PacketDistributor.sendToServer(new SoulProjectionActionC2S(
                    SoulProjectionActionC2S.ACTION_PICKUP, item.getId()));
        }
    }

    /** Don't cancel right-click on own body double — server's EntityInteract listener does CLEAN exit. */
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!isLocalPlayerProjecting()) return;
        if (event.getTarget() instanceof BodyDoubleEntity bd
                && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getUUID().equals(bd.getOwnerUuid())) {
            return; // pass through — server handles CLEAN exit
        }
        // Allow ItemEntity right-click for pickup (handled in onRightClickEmpty too, but defensively here)
        if (event.getTarget() instanceof ItemEntity item) {
            PacketDistributor.sendToServer(new SoulProjectionActionC2S(
                    SoulProjectionActionC2S.ACTION_PICKUP, item.getId()));
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Predictive range clamp. When the player's intended next-tick position would push them
     * past the projection sphere, zero out the radial-outward component of forward / strafe
     * impulse. Tangential motion is preserved so they can still circle the body double.
     */
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isLocalPlayerProjecting()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        Vec3 anchor = state.getSoulProjectionAnchor();
        if (anchor == null) return;
        float maxRange = state.getSoulProjectionMaxRange();
        if (maxRange <= 0f) return;

        Vec3 toPlayer = mc.player.position().subtract(anchor);
        // Only clamp once we're near the boundary (≥ 90% of max). Inside, motion is unrestricted.
        double distSq = toPlayer.lengthSqr();
        double thresholdSq = (maxRange * 0.9) * (maxRange * 0.9);
        if (distSq < thresholdSq) return;

        Vec3 normal = toPlayer.normalize();

        // Convert input to world-space horizontal direction (look-relative)
        var input = event.getInput();
        float fwd = input.forwardImpulse;
        float strafe = input.leftImpulse;
        if (fwd == 0f && strafe == 0f) return;

        float yawRad = (float) Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // World vector: forward axis = (-sin, 0, cos); left axis = (cos, 0, sin)
        double wx = -sin * fwd + cos * strafe;
        double wz =  cos * fwd + sin * strafe;
        Vec3 worldMove = new Vec3(wx, 0, wz);

        double outward = worldMove.dot(new Vec3(normal.x, 0, normal.z));
        if (outward <= 0) return; // moving inward / tangential, fine

        // Strip the radial-outward component
        Vec3 stripped = worldMove.subtract(new Vec3(normal.x, 0, normal.z).scale(outward));

        // Convert back to forward/strafe impulse via inverse rotation
        double newFwd = -sin * stripped.x + cos * stripped.z;
        double newStrafe = cos * stripped.x + sin * stripped.z;
        input.forwardImpulse = (float) newFwd;
        input.leftImpulse = (float) newStrafe;
    }
}
