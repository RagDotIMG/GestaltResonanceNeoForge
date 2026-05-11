package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.ragdot.gestaltresonance.client.gestalt.GestaltModel;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

/**
 * Renders the Gestalt model in first person during ledge grab.
 * Normally the player render layer is not visible in first person because the player entity
 * is not rendered. This handler draws the gestalt in world space when the local player
 * is in first person and ledge grabbing.
 */
public class GestaltFirstPersonRenderer {

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


    private static long windupStartGameTime = -1L;

    private static GestaltModel model;

    public static void setModel(GestaltModel m) {
        model = m;
    }

    /** Exposed so the management screen can render the same model in a GUI context. May be null before player join. */
    public static GestaltModel getModel() {
        return model;
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
        if (mc.level == null || mc.player == null) return;

        float partialTickAll = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camAll = event.getCamera().getPosition();
        PoseStack poseStackAll = event.getPoseStack();

        // Throw: render the gestalt left behind at the throw origin in world space.
        // Runs regardless of camera mode and for every throwing player visible in the level.
        for (Player p : mc.level.players()) {
            if (!(p instanceof AbstractClientPlayer acp)) continue;
            PlayerGestaltState pState = acp.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!pState.isSummoned() || pState.getAction() != GestaltAction.THROW) continue;

            double ox = pState.getThrowOriginX();
            double oy = pState.getThrowOriginY();
            double oz = pState.getThrowOriginZ();
            float oyaw = pState.getThrowOriginYaw();

            poseStackAll.pushPose();
            poseStackAll.translate(ox - camAll.x, oy - camAll.y, oz - camAll.z);
            poseStackAll.mulPose(com.mojang.math.Axis.YN.rotationDegrees(oyaw + 180.0F));
            poseStackAll.translate(0.0, 1.5, 0.0);
            poseStackAll.scale(-1.0F, -1.0F, 1.0F);

            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(GestaltPlayerLayer.textureFor(pState)));
            int lt = net.minecraft.client.renderer.LightTexture.pack(15, 15);
            model.setupAnim(acp, 0, 0, acp.tickCount + partialTickAll, 0, 0);
            model.renderToBuffer(poseStackAll, vc, lt, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            buf.endBatch();
            poseStackAll.popPose();
        }

        // Charged strike: while traveling, render the gestalt at a lerped position toward the target.
        // While in HIT_3 (post-arrival) with a charged-strike target id, render at the target.
        for (Player p : mc.level.players()) {
            if (!(p instanceof AbstractClientPlayer acp)) continue;
            PlayerGestaltState pState = acp.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (!pState.isSummoned()) continue;
            int targetId = pState.getChargedStrikeTargetEntityId();
            if (targetId < 0) continue;
            GestaltAction pAction = pState.getAction();
            boolean traveling = pAction == GestaltAction.CHARGED_STRIKE_TRAVEL;
            boolean strikeAtTarget = pAction == GestaltAction.HIT_3;
            if (!traveling && !strikeAtTarget) continue;

            net.minecraft.world.entity.Entity rawTarget = mc.level.getEntity(targetId);
            if (rawTarget == null) continue;
            double tx = Mth.lerp(partialTickAll, rawTarget.xOld, rawTarget.getX());
            double ty = Mth.lerp(partialTickAll, rawTarget.yOld, rawTarget.getY());
            double tz = Mth.lerp(partialTickAll, rawTarget.zOld, rawTarget.getZ());

            double lx = pState.getChargedStrikeLaunchX();
            double ly = pState.getChargedStrikeLaunchY();
            double lz = pState.getChargedStrikeLaunchZ();

            double fx, fy, fz;
            if (strikeAtTarget) {
                // Render directly at the target.
                fx = tx; fy = ty; fz = tz;
            } else {
                // Travel: smooth t = (traveled + speedPerTick * partialTick) / dist(launch, target_now).
                int tier = pState.getChargedStrikeSpeedTier();
                double speedPerTick = (tier >= 1 && tier < GestaltCosts.CHARGED_STRIKE_TRAVEL_SPEED_BY_SPD.length)
                        ? GestaltCosts.CHARGED_STRIKE_TRAVEL_SPEED_BY_SPD[tier] : 0.0;
                if (speedPerTick < 0) speedPerTick = 0; // SPD 5 sentinel — never used here (instant skips travel)
                double currentDist = Math.sqrt((tx - lx) * (tx - lx) + (ty - ly) * (ty - ly) + (tz - lz) * (tz - lz));
                if (currentDist <= 0.0001) currentDist = 0.0001;
                double t = Math.min(1.0, (pState.getChargedStrikeTraveled() + speedPerTick * partialTickAll) / currentDist);
                fx = lx + (tx - lx) * t;
                fy = ly + (ty - ly) * t;
                fz = lz + (tz - lz) * t;
            }

            // Face the target horizontally (yaw only).
            double dxToTarget = tx - fx;
            double dzToTarget = tz - fz;
            float gestaltYaw = (float) (Math.atan2(dzToTarget, dxToTarget) * (180.0 / Math.PI)) - 90f;

            poseStackAll.pushPose();
            poseStackAll.translate(fx - camAll.x, fy - camAll.y, fz - camAll.z);
            poseStackAll.mulPose(com.mojang.math.Axis.YN.rotationDegrees(gestaltYaw + 180.0F));
            poseStackAll.translate(0.0, 1.5, 0.0);
            poseStackAll.scale(-1.0F, -1.0F, 1.0F);

            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(GestaltPlayerLayer.textureFor(pState)));
            int lt = net.minecraft.client.renderer.LightTexture.pack(15, 15);
            model.setupAnim(acp, 0, 0, acp.tickCount + partialTickAll, 0, 0);
            model.renderToBuffer(poseStackAll, vc, lt, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            buf.endBatch();
            poseStackAll.popPose();
        }

        if (mc.options.getCameraType().isFirstPerson() == false) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        GestaltAction action = state.getAction();
        // Strike phase (charged-strike HIT_3) renders at the target via the world-space block above; skip here.
        if (action == GestaltAction.HIT_3 && state.getChargedStrikeTargetEntityId() >= 0) return;

        boolean ledgeGrabbing = action == GestaltAction.LEDGE_GRAB;
        boolean wallSliding = action == GestaltAction.WALL_SLIDE;
        boolean guarding = action == GestaltAction.GUARD;
        boolean winding = action == GestaltAction.CHARGED_STRIKE_WINDUP;
        boolean attacking = action == GestaltAction.HIT_1 || action == GestaltAction.HIT_2 || action == GestaltAction.HIT_3
                || action == GestaltAction.POWER_1G_WINDUP;
        boolean mining = !ledgeGrabbing && !wallSliding && !guarding && !winding && !attacking
                && mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult bhr
                && bhr.getType() != HitResult.Type.MISS
                && Vec3.atCenterOf(bhr.getBlockPos()).distanceTo(player.getEyePosition()) <= GestaltCosts.mineRangeFor(state);
        if (!ledgeGrabbing && !wallSliding && !guarding && !winding && !attacking && !mining) return;

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
        } else if (wallSliding) {
            Direction wallFace = state.getWallSlideFace();
            gestaltYaw = (wallFace != null) ? directionToYaw(wallFace)
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
            finalY = cam.y + CAM_OFFSET_Y + 0.1F + ATTACK_OFFSET_Z * lookY;
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
            finalY = cam.y + CAM_OFFSET_Y + 0.1F + GUARD_OFFSET_Z * lookY;
            finalZ = cam.z + GUARD_OFFSET_X * Mth.sin(yawRad) + GUARD_OFFSET_Z * lookZ;
        } else if (winding) {
            gestaltYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.getYHeadRot());
            float headPitch = Mth.rotLerp(partialTick, player.xRotO, player.getXRot());
            float pitchRad = headPitch * Mth.DEG_TO_RAD;
            float yawRad = gestaltYaw * Mth.DEG_TO_RAD;
            float lookX = -Mth.sin(yawRad) * Mth.cos(pitchRad);
            float lookY = -Mth.sin(pitchRad);
            float lookZ =  Mth.cos(yawRad) * Mth.cos(pitchRad);
            float windZ = GUARD_OFFSET_Z + 0.1F;
            finalX = cam.x + GUARD_OFFSET_X * Mth.cos(yawRad) + windZ * lookX;
            finalY = cam.y + CAM_OFFSET_Y + 0.1F + windZ * lookY;
            finalZ = cam.z + GUARD_OFFSET_X * Mth.sin(yawRad) + windZ * lookZ;
        } else {
            // Mining: lock gestalt to the mined block face.
            // Horizontal faces: position at face surface + 0.5 blocks away + 0.5 blocks left.
            // Vertical faces (floor/ceiling): fall back to head-yaw guard position.
            BlockHitResult mhr = (BlockHitResult) mc.hitResult;
            Direction face = mhr.getDirection();
            if (face.getAxis().isHorizontal()) {
                Vec3 blockCenter = Vec3.atCenterOf(mhr.getBlockPos());
                Vec3 faceNormal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
                Vec3 leftDir = faceNormal.cross(new Vec3(0.0, 1.0, 0.0));
                Vec3 gestaltPos = blockCenter.add(faceNormal.scale(1.5)).add(leftDir.scale(0.8));
                gestaltYaw = directionToYaw(face);
                finalX = gestaltPos.x;
                finalY = gestaltPos.y - 1.3;
                finalZ = gestaltPos.z;
            } else {
                gestaltYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.getYHeadRot());
                float yawRad = gestaltYaw * Mth.DEG_TO_RAD;
                finalX = px + GUARD_OFFSET_X * Mth.cos(yawRad) - GUARD_OFFSET_Z * Mth.sin(yawRad);
                finalY = py + OFFSET_Y;
                finalZ = pz + GUARD_OFFSET_X * Mth.sin(yawRad) + GUARD_OFFSET_Z * Mth.cos(yawRad);
            }
        }

        // Fall-break impact shake: same jitter as the third-person layer.
        float[] shake = GestaltPlayerLayer.getShakeOffset(
                player.getUUID(), player.level().getGameTime(), partialTick);
        finalX += shake[0];
        finalY += shake[1];
        finalZ += shake[2];

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        poseStack.translate(finalX - cam.x, finalY - cam.y, finalZ - cam.z);
        poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(gestaltYaw + 180.0F));
        poseStack.translate(0.0, 0.75, 0.0);  // move to model center so pitch rotates around it
        if (guarding || attacking || winding) {
            float headPitch = Mth.rotLerp(partialTick, player.xRotO, player.getXRot());
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-headPitch));
        }
        poseStack.translate(0.0, 0.75, 0.0);
        // Visual-only downward shift: applied after all rotations so the pivot stays unchanged.
        // scale(-1,-1,1) flips Y, so -v here renders the model v blocks lower.
        float visualYShift = attacking ? 0.4F : guarding ? 0.5F : winding ? 0.6F : 0.0F;
        if (visualYShift != 0.0F) poseStack.translate(0.0, -visualYShift, 0.0);
        if (winding) {
            long gameTime = player.level().getGameTime();
            if (windupStartGameTime < 0) windupStartGameTime = gameTime;
            long windupElapsed = gameTime - windupStartGameTime;
            if (windupElapsed >= GestaltCosts.CHARGED_STRIKE_WINDUP_TICKS) {
                float shakePhase = (windupElapsed + partialTick) * 1.8F;
                float shakeAmt = 0.025F;
                poseStack.translate(Math.sin(shakePhase) * shakeAmt, Math.sin(shakePhase * 1.3F) * shakeAmt * 0.5F, 0.0);
            }
            poseStack.translate(-0.3, 0.0, -0.5);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-20.0F));
        } else {
            windupStartGameTime = -1L;
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(GestaltPlayerLayer.textureFor(state)));

        int light = net.minecraft.client.renderer.LightTexture.pack(15, 15);
        model.setupAnim(player, 0, 0, player.tickCount + partialTick, 0, 0);
        model.renderToBuffer(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 0xAAFFFFFF);

        bufferSource.endBatch();
        poseStack.popPose();
    }
}
