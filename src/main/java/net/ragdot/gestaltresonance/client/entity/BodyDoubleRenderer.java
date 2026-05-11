package net.ragdot.gestaltresonance.client.entity;

import java.util.UUID;

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
import net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity;

public class BodyDoubleRenderer
        extends LivingEntityRenderer<BodyDoubleEntity, HumanoidModel<BodyDoubleEntity>> {

    public BodyDoubleRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        // Render the owner's armor (snapshot copied at projection time)
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        // Render the owner's held items
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(BodyDoubleEntity entity) {
        UUID uuid = entity.getOwnerUuid();
        if (uuid != null) {
            var mc = Minecraft.getInstance();
            var connection = mc.getConnection();
            if (connection != null) {
                var info = connection.getPlayerInfo(uuid);
                if (info != null) {
                    return info.getSkin().texture();
                }
            }
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }
}
