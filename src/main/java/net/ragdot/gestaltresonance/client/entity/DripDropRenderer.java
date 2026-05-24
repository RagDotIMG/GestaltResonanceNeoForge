package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.DripDropEntity;

@OnlyIn(Dist.CLIENT)
public class DripDropRenderer extends EntityRenderer<DripDropEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GestaltResonance.MODID, "textures/entity/popdripdrop.png");

    public DripDropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.05f;
    }

    @Override
    public void render(DripDropEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.scale(0.3f, 0.3f, 0.3f);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        // Two axis-aligned quads forming a "+" cross visible from all directions.

        // Quad 1: XY plane (faces ±Z)
        consumer.addVertex(pose.pose(), -0.5f, -0.5f, 0f)
                .setColor(255, 255, 255, 255).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(),  0.5f, -0.5f, 0f)
                .setColor(255, 255, 255, 255).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(),  0.5f,  0.5f, 0f)
                .setColor(255, 255, 255, 255).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(), -0.5f,  0.5f, 0f)
                .setColor(255, 255, 255, 255).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);

        // Quad 2: ZY plane (faces ±X)
        consumer.addVertex(pose.pose(), 0f, -0.5f, -0.5f)
                .setColor(255, 255, 255, 255).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(), 0f, -0.5f,  0.5f)
                .setColor(255, 255, 255, 255).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(), 0f,  0.5f,  0.5f)
                .setColor(255, 255, 255, 255).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(), 0f,  0.5f, -0.5f)
                .setColor(255, 255, 255, 255).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DripDropEntity entity) {
        return TEXTURE;
    }
}
