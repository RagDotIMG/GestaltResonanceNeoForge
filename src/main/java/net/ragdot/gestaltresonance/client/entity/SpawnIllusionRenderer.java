package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class SpawnIllusionRenderer
        extends LivingEntityRenderer<SpawnIllusionEntity, HumanoidModel<SpawnIllusionEntity>> {

    public SpawnIllusionRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public void render(SpawnIllusionEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        float alpha = computeAlpha(entity.getAgeTicks());
        RenderSystem.setShaderColor(0.26f, 0.07f, 0.52f, alpha);
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected RenderType getRenderType(SpawnIllusionEntity entity, boolean visible,
                                       boolean spectator, boolean outline) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    public ResourceLocation getTextureLocation(SpawnIllusionEntity entity) {
        UUID uuid = entity.getOwnerUuid();
        if (uuid != null) {
            var mc = Minecraft.getInstance();
            var connection = mc.getConnection();
            if (connection != null) {
                var info = connection.getPlayerInfo(uuid);
                if (info != null) return info.getSkin().texture();
            }
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    private static float computeAlpha(int age) {
        if (age < GestaltCosts.ILLUSION_FADE_START) {
            return GestaltCosts.ILLUSION_BASE_OPACITY;
        } else if (age < GestaltCosts.ILLUSION_FADE_START + GestaltCosts.ILLUSION_FADE_DURATION) {
            float t = (age - GestaltCosts.ILLUSION_FADE_START) / (float) GestaltCosts.ILLUSION_FADE_DURATION;
            return GestaltCosts.ILLUSION_BASE_OPACITY
                    - t * (GestaltCosts.ILLUSION_BASE_OPACITY - GestaltCosts.ILLUSION_FADE_OPACITY);
        } else {
            return GestaltCosts.ILLUSION_FADE_OPACITY;
        }
    }
}
