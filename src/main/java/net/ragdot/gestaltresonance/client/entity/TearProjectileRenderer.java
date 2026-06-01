package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
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
import net.ragdot.gestaltresonance.common.entity.TearProjectileEntity;

@OnlyIn(Dist.CLIENT)
public class TearProjectileRenderer extends EntityRenderer<TearProjectileEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GestaltResonance.MODID, "textures/entity/tears_for_fears.png");
    private static final ResourceLocation EFFECT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GestaltResonance.MODID, "textures/entity/tears_for_fears_effect.png");

    private static final int   BLUR_PASSES          = 6;
    private static final float BLUR_OFFSET           = 0.03f;
    private static final float BLUR_ALPHA_MULT       = 0.20f;
    private static final float EFFECT_BLUR_OFFSET    = 0.02f;
    private static final float EFFECT_BLUR_ALPHA_MULT = 0.18f;

    public TearProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(TearProjectileEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        float scale = 0.8f + 0.08f * Mth.sin(age * 0.2f);
        poseStack.scale(scale, scale, scale);

        // Base sprite at full opacity
        renderQuad(poseStack, buffer.getBuffer(RenderType.entityTranslucentCull(TEXTURE)),
                   light, 255, 0f, 0f, 0.001f, 1.0f, 1.0f);

        // Faux blur halo: 6 low-alpha copies offset in a ring
        int blurAlpha = (int)(255 * BLUR_ALPHA_MULT);
        VertexConsumer baseConsumer = buffer.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        for (int i = 0; i < BLUR_PASSES; i++) {
            float a = (float)(i * (Math.PI * 2.0) / BLUR_PASSES);
            renderQuad(poseStack, baseConsumer, light, blurAlpha,
                       BLUR_OFFSET * Mth.cos(a), BLUR_OFFSET * Mth.sin(a),
                       0.0008f + i * 0.00008f, 1.03f, 1.03f);
        }

        // Overlay passes: 6 layers alternating between two textures with sinusoidal drift
        float alphaPct = (0.70f + 0.20f * Mth.sin(age * 0.4f + 1.1f)) * 0.95f;
        int alphaOverlay = (int)(255 * Mth.clamp(alphaPct, 0f, 1f));
        int phase = ((int)(age / 8.0f)) & 1;

        for (int layerIdx = 0; layerIdx < 6; layerIdx++) {
            boolean useEffect = (((layerIdx + phase) & 1) == 0);
            ResourceLocation tex = useEffect ? EFFECT_TEXTURE : TEXTURE;
            float offX = (0.015f + 0.004f * layerIdx) * Mth.sin(age * (0.32f + 0.03f * layerIdx) + layerIdx);
            float offY = (0.015f + 0.004f * layerIdx) * Mth.cos(age * (0.26f + 0.02f * layerIdx) + layerIdx * 1.7f);

            VertexConsumer oc = buffer.getBuffer(RenderType.entityTranslucentCull(tex));
            renderQuad(poseStack, oc, light, alphaOverlay, offX, offY,
                       0.002f + layerIdx * 0.00012f, 1.02f, 0.98f);

            int overlayBlurAlpha = (int)(alphaOverlay * EFFECT_BLUR_ALPHA_MULT);
            for (int i = 0; i < BLUR_PASSES; i++) {
                float a2 = (float)(i * (Math.PI * 2.0) / BLUR_PASSES);
                renderQuad(poseStack, oc, light, overlayBlurAlpha,
                           offX + EFFECT_BLUR_OFFSET * Mth.cos(a2),
                           offY + EFFECT_BLUR_OFFSET * Mth.sin(a2),
                           0.0006f + i * 0.00006f, 1.015f, 1.015f);
            }
        }

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    private static void renderQuad(PoseStack poseStack, VertexConsumer c, int light, int alpha,
                                    float offX, float offY, float offZ, float scaleX, float scaleY) {
        poseStack.pushPose();
        poseStack.translate(offX, offY, offZ);
        poseStack.scale(scaleX, scaleY, 1.0f);
        PoseStack.Pose pose = poseStack.last();
        c.addVertex(pose.pose(), -0.5f, -0.5f, 0f).setColor(255, 255, 255, alpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        c.addVertex(pose.pose(),  0.5f, -0.5f, 0f).setColor(255, 255, 255, alpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        c.addVertex(pose.pose(),  0.5f,  0.5f, 0f).setColor(255, 255, 255, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        c.addVertex(pose.pose(), -0.5f,  0.5f, 0f).setColor(255, 255, 255, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(TearProjectileEntity entity) {
        return TEXTURE;
    }
}
