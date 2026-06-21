package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class SpawnIllusionRenderer
        extends LivingEntityRenderer<SpawnIllusionEntity, HumanoidModel<SpawnIllusionEntity>> {

    private final HumanoidModel<SpawnIllusionEntity> wideModel;
    private final HumanoidModel<SpawnIllusionEntity> slimModel;

    public SpawnIllusionRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(SpawnIllusionEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = resolveSlim(entity.getOwnerUuid(), entity.isSlim()) ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    protected RenderType getRenderType(SpawnIllusionEntity entity, boolean visible,
                                       boolean spectator, boolean outline) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    public ResourceLocation getTextureLocation(SpawnIllusionEntity entity) {
        PlayerInfo info = getPlayerInfo(entity.getOwnerUuid());
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
    }

    private static boolean resolveSlim(UUID ownerUuid, boolean fallback) {
        PlayerInfo info = getPlayerInfo(ownerUuid);
        if (info != null) return info.getSkin().model() == PlayerSkin.Model.SLIM;
        return fallback;
    }

    private static PlayerInfo getPlayerInfo(UUID uuid) {
        if (uuid == null) return null;
        var conn = Minecraft.getInstance().getConnection();
        return conn != null ? conn.getPlayerInfo(uuid) : null;
    }
}
