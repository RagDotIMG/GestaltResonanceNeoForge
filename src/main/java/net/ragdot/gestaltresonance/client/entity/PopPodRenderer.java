package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.PopPodEntity;

import com.mojang.math.Axis;

@OnlyIn(Dist.CLIENT)
public class PopPodRenderer extends EntityRenderer<PopPodEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GestaltResonance.MODID, "textures/entity/poppod_texture.png");

    public PopPodRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.1f;
    }

    @Override
    public void render(PopPodEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        poseStack.pushPose();

        float lerpYaw   = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float lerpPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // Rotate to match travel direction, same axis order as ArrowRenderer
        poseStack.mulPose(Axis.YP.rotationDegrees(lerpYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-lerpPitch));

        poseStack.scale(0.5f, 0.5f, 0.5f);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        // Both quads extend along the travel axis (+Z in local space after rotations),
        // forming a vertical "+" cross visible from any horizontal angle — like an arrow.

        // Quad 1: XZ plane (horizontal wing, faces ±Y)
        consumer.addVertex(pose.pose(), -0.5f, 0f, -0.5f)
                .setColor(255, 255, 255, 255).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(),  0.5f, 0f, -0.5f)
                .setColor(255, 255, 255, 255).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(),  0.5f, 0f,  0.5f)
                .setColor(255, 255, 255, 255).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(), -0.5f, 0f,  0.5f)
                .setColor(255, 255, 255, 255).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);

        // Quad 2: YZ plane (vertical wing, faces ±X)
        consumer.addVertex(pose.pose(), 0f, -0.5f, -0.5f)
                .setColor(255, 255, 255, 255).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(), 0f, -0.5f,  0.5f)
                .setColor(255, 255, 255, 255).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(), 0f,  0.5f,  0.5f)
                .setColor(255, 255, 255, 255).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(), 0f,  0.5f, -0.5f)
                .setColor(255, 255, 255, 255).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PopPodEntity entity) {
        return TEXTURE;
    }
}
