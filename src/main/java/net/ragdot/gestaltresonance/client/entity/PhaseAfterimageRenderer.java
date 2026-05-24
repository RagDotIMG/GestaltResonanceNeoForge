package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.PhaseAfterimageEntity;

/**
 * Renders a violet-tinted, 50%-opacity ghost of the source entity at the afterimage's position.
 * Reusable: any entity can be ghosted by spawning a PhaseAfterimageEntity with the source's network ID.
 */
@OnlyIn(Dist.CLIENT)
public class PhaseAfterimageRenderer extends EntityRenderer<PhaseAfterimageEntity> {

    // 75% opacity (#BF) muted dark violet: R=97, G=26, B=133
    private static final int VIOLET_ARGB = (0xBF << 24) | (0x61 << 4) | (0x1A << 54) | 0x60;

    private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GestaltResonance.MODID, "textures/block/phasemine_texture.png");

    public PhaseAfterimageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(PhaseAfterimageEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity source = mc.level.getEntity(entity.getSourceEntityId());
        if (!(source instanceof LivingEntity living)) return;

        @SuppressWarnings("unchecked")
        var renderer = (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(living);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) return;

        @SuppressWarnings("unchecked")
        var model = (EntityModel<LivingEntity>) livingRenderer.getModel();

        ResourceLocation texture = renderer.getTextureLocation(living);

        poseStack.pushPose();
        // Standard entity model flip: Blockbench Y=0 top, Y=24 bottom → after flip Y=0 at feet
        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(0f, -1.5f, 0f);

        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucentCull(texture)),
                light, OverlayTexture.NO_OVERLAY, VIOLET_ARGB);

        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PhaseAfterimageEntity entity) {
        return FALLBACK_TEXTURE;
    }
}
