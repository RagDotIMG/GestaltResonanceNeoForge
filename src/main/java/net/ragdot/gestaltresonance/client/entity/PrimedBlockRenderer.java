package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.ragdot.gestaltresonance.common.entity.PrimedBlockEntity;

public class PrimedBlockRenderer extends EntityRenderer<PrimedBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public PrimedBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PrimedBlockEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0);
        int i = entity.getFuse();
        if ((float)i - partialTicks + 1.0F < 10.0F) {
            float f = 1.0F - ((float)i - partialTicks + 1.0F) / 10.0F;
            f = Mth.clamp(f, 0.0F, 1.0F);
            f *= f;
            f *= f;
            float f1 = 1.0F + f * 0.3F;
            poseStack.scale(f1, f1, f1);
        }

        poseStack.rotateAround(Axis.YP.rotationDegrees(-90.0F), 0.0F, 1.0F, 0.0F);
        poseStack.translate(-0.5, -0.5, 0.5);
        poseStack.rotateAround(Axis.YP.rotationDegrees(90.0F), 0.0F, 1.0F, 0.0F);
        TntMinecartRenderer.renderWhiteSolidBlock(this.blockRenderer, entity.getBlockState(), poseStack, buffer, packedLight, i / 5 % 2 == 0);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimedBlockEntity entity) {
        return net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }
}
