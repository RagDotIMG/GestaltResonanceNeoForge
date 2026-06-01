package net.ragdot.gestaltresonance.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.common.entity.TimePhaseBodyDoubleEntity;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class TimePhaseBodyDoubleRenderer
        extends LivingEntityRenderer<TimePhaseBodyDoubleEntity, HumanoidModel<TimePhaseBodyDoubleEntity>> {

    public TimePhaseBodyDoubleRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(TimePhaseBodyDoubleEntity entity) {
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
}
