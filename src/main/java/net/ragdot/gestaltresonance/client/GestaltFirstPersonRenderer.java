package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.client.gestalt.AmenBreakModel;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

/**
 * Renders the Gestalt model in first person during ledge grab.
 * Normally the player render layer is not visible in first person because the player entity
 * is not rendered. This handler draws the gestalt in world space when the local player
 * is in first person and ledge grabbing.
 */
public class GestaltFirstPersonRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "textures/gestalt/amen_break.png");

    // Ledge-grab: right side of the wall face
    private static final float OFFSET_X = 0.6F;
    private static final float OFFSET_Y = 0.0F;
    private static final float LEDGE_GRAB_OFFSET_Y = -0.3F;
    private static final float LEDGE_GRAB_OFFSET_Z = -0.1F;

    // Guard: left-front relative to head direction (world-space rotated by head yaw).
    // modelX=+0.5 → player's left, modelZ=+1.0 → player's front.
    private static final float GUARD_OFFSET_X = 0.5F;
    private static final float GUARD_OFFSET_Z = 1.0F;

    // Y offset when position is anchored to the camera (eye) rather than player feet.
    // Camera is ~1.62 above feet; tune this to place the gestalt at a comfortable height.
    private static final float CAM_OFFSET_Y = -1.1F;

    // Attack: 0.5 blocks further forward and 0.2 blocks further left than guard.
    private static final float ATTACK_OFFSET_X = 1.5F;
    private static final float ATTACK_OFFSET_Z = 1.5F;


    private static AmenBreakModel model;

    public static void setModel(AmenBreakModel m) {
        model = m;
    }

    /**
     * Convert the ledge face Direction to the yaw the gestalt should face.
     * ledgeFace is the outward normal from the wall toward the player, so the
     * gestalt must face the opposite direction (into the wall).
     */
    private static float directionToYaw(Direction face) {
        return switch (face) {
            case NORTH -> 0;
            case SOUTH -> 180;
            case WEST  -> -90;
            case EAST  -> 90;
            default    -> 0;
        };
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (model == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson() == false) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        GestaltAction action = state.getAction();
        boolean ledgeGrabbing = action == GestaltAction.LEDGE_GRAB;
        boolean guarding = action == GestaltAction.GUARD;
        boolean attacking = action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2 || action == GestaltAction.HIT_3;
        boolean mining = !ledgeGrabbing && !guarding && !attacking
                && mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult bhr
                && bhr.getType() != HitResult.Type.MISS
                && Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) <= 3.5;
        if (!ledgeGrabbing && !guarding && !attacking && !mining) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        double px = Mth.lerp(partialTick, player.xOld, player.getX());
        double py = Mth.lerp(partialTick, player.yOld, player.getY());
        double pz = Mth.lerp(partialTick, player.zOld, player.getZ());

        Vec3 cam = event.getCamera().getPosition();

        float gestaltYaw;
        double finalX, finalY, finalZ;

        if (ledgeGrabbing) {
            Direction ledgeFace = state.getLedgeFace();
            gestaltYaw = (ledgeFace != null) ? directionToYaw(ledgeFace)
                    : Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            float yawRad = gestaltYaw * Mth.DEG_TO_RAD;
            finalX = px + OFFSET_X * Mth.cos(yawRad) - LEDGE_GRAB_OFFSET_Z * Mth.sin(yawRad);
            finalY = py + LEDGE_GRAB_OFFSET_Y;
            finalZ = pz + OFFSET_X * Mth.sin(yawRad) + LEDGE_GRAB_OFFSET_Z * Mth.cos(yawRad);
        } else if (attacking) {
            gestaltYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.getYHeadRot()) + 20F;
            float headPitch = Mth.rotLerp(partialTick, player.xRotO, player.getXRot());
            float pitchRad = headPitch * Mth.DEG_TO_RAD;
            float yawRad = gestaltYaw * Mth.DEG_TO_RAD;
            float lookX = -Mth.sin(yawRad) * Mth.cos(pitchRad);
            float lookY = -Mth.sin(pitchRad);
            float lookZ =  Mth.cos(yawRad) * Mth.cos(pitchRad);
            finalX = cam.x + ATTACK_OFFSET_X * Mth.cos(yawRad) + ATTACK_OFFSET_Z * lookX;
            finalY = cam.y + CAM_OFFSET_Y + ATTACK_OFFSET_Z * lookY;
            finalZ = cam.z + ATTACK_OFFSET_X * Mth.sin(yawRad) + ATTACK_OFFSET_Z * lookZ;
        } else if (guarding) {
            gestaltYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.getYHeadRot());
            float headPitch = Mth.rotLerp(partialTick, player.xRotO, player.getXRot());
            float pitchRad = headPitch * Mth.DEG_TO_RAD;
            float yawRad = gestaltYaw * Mth.DEG_TO_RAD;
            float lookX = -Mth.sin(yawRad) * Mth.cos(pitchRad);
            float lookY = -Mth.sin(pitchRad);
            float lookZ =  Mth.cos(yawRad) * Mth.cos(pitchRad);
            finalX = cam.x + GUARD_OFFSET_X * Mth.cos(yawRad) + GUARD_OFFSET_Z * lookX;
            finalY = cam.y + CAM_OFFSET_Y + GUARD_OFFSET_Z * lookY;
            finalZ = cam.z + GUARD_OFFSET_X * Mth.sin(yawRad) + GUARD_OFFSET_Z * lookZ;
        } else {
            // Mining: lock gestalt to the mined block face.
            // Horizontal faces: position at face surface + 0.5 blocks away + 0.5 blocks left.
            // Vertical faces (floor/ceiling): fall back to head-yaw guard position.
            BlockHitResult mhr = (BlockHitResult) mc.hitResult;
            Direction face = mhr.getDirection();
            if (face.getAxis().isHorizontal()) {
                Vec3 blockCenter = Vec3.atCenterOf(mhr.getBlockPos());
                Vec3 faceNormal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
                // left = faceNormal × up (player's left when looking at the face)
                Vec3 leftDir = faceNormal.cross(new Vec3(0.0, 1.0, 0.0));
                // face surface + 0.5 away from surface + 1.5 to the left
                Vec3 gestaltPos = blockCenter.add(faceNormal.scale(1.0)).add(leftDir.scale(1.5));
                gestaltYaw = directionToYaw(face);
                finalX = gestaltPos.x + 0.5;
                finalY = gestaltPos.y - 1.3;
                finalZ = gestaltPos.z - 0.7;
            } else {
                gestaltYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.getYHeadRot());
                float yawRad = gestaltYaw * Mth.DEG_TO_RAD;
                finalX = px + GUARD_OFFSET_X * Mth.cos(yawRad) - GUARD_OFFSET_Z * Mth.sin(yawRad);
                finalY = py + OFFSET_Y;
                finalZ = pz + GUARD_OFFSET_X * Mth.sin(yawRad) + GUARD_OFFSET_Z * Mth.cos(yawRad);
            }
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        poseStack.translate(finalX - cam.x, finalY - cam.y, finalZ - cam.z);
        poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(gestaltYaw + 180.0F));
        poseStack.translate(0.0, 0.75, 0.0);  // move to model center so pitch rotates around it
        if (guarding || attacking) {
            float headPitch = Mth.rotLerp(partialTick, player.xRotO, player.getXRot());
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-headPitch));
        }
        poseStack.translate(0.0, 0.75, 0.0);
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));

        int light = net.minecraft.client.renderer.LightTexture.pack(15, 15);
        model.setupAnim(player, 0, 0, player.tickCount + partialTick, 0, 0);
        model.renderToBuffer(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 0xAAFFFFFF);

        bufferSource.endBatch();
        poseStack.popPose();
    }
}
