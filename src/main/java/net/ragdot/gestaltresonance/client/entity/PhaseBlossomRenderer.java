package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.PhaseBlossomEntity;

@OnlyIn(Dist.CLIENT)
public class PhaseBlossomRenderer extends EntityRenderer<PhaseBlossomEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GestaltResonance.MODID, "textures/block/phaseblossom_texture.png");

    private final PhaseMineModel model;

    public PhaseBlossomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new PhaseMineModel(ctx.bakeLayer(PhaseMineModel.LAYER));
        this.shadowRadius = 0.2f;
    }

    @Override
    public void render(PhaseBlossomEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();

        Direction facing = entity.getFacing();
        switch (facing) {
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case EAST  -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST  -> poseStack.mulPose(Axis.ZP.rotationDegrees(90));
            default    -> {}
        }

        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.5f, 0.0f);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PhaseBlossomEntity entity) {
        return TEXTURE;
    }
}
