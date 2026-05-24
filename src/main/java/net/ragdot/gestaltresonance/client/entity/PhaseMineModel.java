package net.ragdot.gestaltresonance.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.PhaseMineEntity;

@OnlyIn(Dist.CLIENT)
public class PhaseMineModel extends EntityModel<PhaseMineEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "phase_mine"), "main");

    private final ModelPart bb_main;

    public PhaseMineModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main",
                CubeListBuilder.create()
                        .texOffs(8, 6).addBox(-1.0F, -0.8F, -1.0F, 2.0F, 0.0F, 2.0F, new CubeDeformation(0.2F))
                        .texOffs(0, 0).addBox(-2.0F, -0.8F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(-0.1F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        bb_main.addOrReplaceChild("Petal8_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -0.6F, -2.2F, -2.4077F, 0.5701F, -2.6908F));

        bb_main.addOrReplaceChild("Petal7_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -0.6F, 2.0F, 0.6782F, -0.6376F, -0.4082F));

        bb_main.addOrReplaceChild("Petal6_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, -0.6F, 0.0F, -3.0917F, -0.8294F, -2.6236F));

        bb_main.addOrReplaceChild("Petal5_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.0F, -0.6F, 0.0F, 0.0938F, 0.7978F, -0.617F));

        bb_main.addOrReplaceChild("Petal4_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.0F, -0.6F, 2.0F, 0.331F, 0.089F, -0.2488F));

        bb_main.addOrReplaceChild("Petal3_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, -0.6F, 2.0F, 1.6401F, -1.2589F, -1.3124F));

        bb_main.addOrReplaceChild("Petal2_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, -0.6F, -2.0F, -2.9193F, 0.0173F, -2.8633F));

        bb_main.addOrReplaceChild("Petal1_r1",
                CubeListBuilder.create().texOffs(-4, 6).addBox(-1.0F, 0.0F, -1.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.0F, -0.6F, -2.0F, -1.467F, 1.2442F, -1.8491F));

        return LayerDefinition.create(meshdefinition, 16, 16);
    }

    @Override
    public void setupAnim(PhaseMineEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               int color) {
        bb_main.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
