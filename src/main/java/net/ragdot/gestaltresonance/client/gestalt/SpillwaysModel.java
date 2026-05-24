package net.ragdot.gestaltresonance.client.gestalt;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Model for the Spillways gestalt.
 * Made with Blockbench 5.0.7.
 */
public class SpillwaysModel extends GestaltModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "spillways"), "main");

    private final ModelPart root;

    public SpillwaysModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    protected AnimationDefinition introAnimation() {
        return SpillwaysAnimations.INTRO;
    }

    @Override
    protected AnimationDefinition idleAnimation() {
        return SpillwaysAnimations.IDLE;
    }

    @Override
    protected AnimationDefinition grabAnimation() {
        return SpillwaysAnimations.GRAB;
    }

    @Override
    protected AnimationDefinition guardAnimation() {
        return SpillwaysAnimations.GUARD;
    }

    @Override
    protected AnimationDefinition throwAnimation() {
        return SpillwaysAnimations.THROW;
    }

    @Override
    protected AnimationDefinition windupAnimation() {
        return SpillwaysAnimations.WINDUP;
    }

    @Override
    protected AnimationDefinition hit1Animation() {
        return SpillwaysAnimations.PUNCH;
    }

    @Override
    protected AnimationDefinition hit2Animation() {
        return SpillwaysAnimations.PUNCH;
    }

    @Override
    protected AnimationDefinition hit3Animation() {
        return SpillwaysAnimations.PUNCH;
    }

    @Override
    protected AnimationDefinition miningAnimation() {
        return SpillwaysAnimations.MINING;
    }

    @Override
    protected AnimationDefinition swimAnimation() {
        return SpillwaysAnimations.SWIM;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Spillways_model = partdefinition.addOrReplaceChild("Spillways_model", CubeListBuilder.create(), PartPose.offset(0.0F, 18.0F, 0.1F));

        PartDefinition body_up = Spillways_model.addOrReplaceChild("body_up", CubeListBuilder.create().texOffs(0, 25).addBox(-1.5F, -4.025F, -1.0875F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, -9.075F, -0.1125F));

        body_up.addOrReplaceChild("cloth_f_r1", CubeListBuilder.create().texOffs(34, 13).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(1.077F, 0.1821F, -0.0363F, 2.3408F, -1.5619F, -2.599F));

        body_up.addOrReplaceChild("cloth_f_r2", CubeListBuilder.create().texOffs(34, 13).mirror().addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offsetAndRotation(-1.0694F, 0.1775F, -0.0363F, 2.3408F, 1.5619F, 2.599F));

        PartDefinition torso_up = body_up.addOrReplaceChild("torso_up", CubeListBuilder.create().texOffs(20, 1).addBox(-0.5F, -5.45F, -0.625F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F))
                .texOffs(0, 18).addBox(-2.0F, -3.65F, -1.625F, 4.0F, 4.0F, 3.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, -4.075F, 0.0375F));

        torso_up.addOrReplaceChild("boob_r1", CubeListBuilder.create().texOffs(14, 20).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.9052F, -0.6712F, -0.5672F, 0.0F, 0.0F));

        PartDefinition head = torso_up.addOrReplaceChild("head", CubeListBuilder.create().texOffs(26, 0).addBox(-2.0F, -4.1F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.1F))
                .texOffs(26, 8).addBox(-2.0F, -2.5F, -1.9F, 4.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.05F, -0.025F));

        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(0, 0).addBox(-2.9901F, -3.1581F, -2.8943F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.0099F, -3.9419F, -0.0057F));

        hat.addOrReplaceChild("edge_w_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5098F, 0.7666F, 0.1566F, 0.0F, 1.5708F, -1.1345F));

        hat.addOrReplaceChild("edge_e_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4264F, 0.676F, 0.0625F, 0.0F, -1.5708F, 1.1345F));

        hat.addOrReplaceChild("edge_s_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0367F, 0.676F, 3.5705F, 1.1345F, 0.0F, 0.0F));

        hat.addOrReplaceChild("edge_n_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0603F, 0.7666F, -3.3928F, -2.0071F, 0.0F, -3.1416F));

        hat.addOrReplaceChild("edge_w_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.6407F, 0.704F, 0.1566F, 0.0F, 1.5708F, -1.0908F));

        hat.addOrReplaceChild("edge_e_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5612F, 0.6153F, 0.0625F, 0.0F, -1.5708F, 1.0908F));

        hat.addOrReplaceChild("edge_s_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0367F, 0.6153F, 3.7053F, 1.0908F, 0.0F, 0.0F));

        hat.addOrReplaceChild("edge_n_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0603F, 0.704F, -3.5237F, -2.0508F, 0.0F, -3.1416F));

        hat.addOrReplaceChild("side_L_r1", CubeListBuilder.create().texOffs(0, 39).addBox(-7.5F, -2.5F, 0.0F, 15.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0393F, -0.6581F, 0.0748F, 0.0F, 0.7854F, 0.0F));

        hat.addOrReplaceChild("side_R_r1", CubeListBuilder.create().texOffs(0, 39).mirror().addBox(-7.5F, -2.5F, 0.0F, 15.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.0314F, -0.6581F, 0.0658F, -3.1416F, 0.7854F, 3.1416F));

        PartDefinition hat2 = hat.addOrReplaceChild("hat2", CubeListBuilder.create().texOffs(0, 10).addBox(-1.99F, -4.0824F, -1.8977F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.0001F, -3.0757F, 0.0034F));

        hat2.addOrReplaceChild("side_L_r2", CubeListBuilder.create().texOffs(21, 39).addBox(-4.5F, -2.0F, 0.0F, 9.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0294F, -2.0824F, 0.0815F, 0.0F, 0.7854F, 0.0F));

        hat2.addOrReplaceChild("side_R_r2", CubeListBuilder.create().texOffs(21, 39).mirror().addBox(-4.5F, -2.0F, 0.0F, 9.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.0305F, -2.0824F, 0.0617F, 0.0F, -0.7854F, 0.0F));

        PartDefinition hat3 = hat2.addOrReplaceChild("hat3", CubeListBuilder.create().texOffs(16, 12).addBox(-0.9799F, -4.1375F, -0.8951F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.01F, -3.945F, -0.0027F));

        hat3.addOrReplaceChild("side_R_r3", CubeListBuilder.create().texOffs(8, 34).mirror().addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0699F, -2.6375F, 0.1563F, -3.1416F, 0.7854F, 3.1416F));

        hat3.addOrReplaceChild("side_L_r3", CubeListBuilder.create().texOffs(8, 34).addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1109F, -2.6375F, 0.0141F, 0.0F, 0.7854F, 0.0F));

        PartDefinition hat_end = hat3.addOrReplaceChild("hat_end", CubeListBuilder.create(), PartPose.offset(0.0211F, -3.0875F, 0.0049F));

        hat_end.addOrReplaceChild("main5_r1", CubeListBuilder.create().texOffs(0, 26).addBox(0.0F, -3.0F, -3.5F, 0.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.001F, -3.05F, 0.101F, 0.0F, -1.5708F, 0.0F));

        PartDefinition arm_R = torso_up.addOrReplaceChild("arm_R", CubeListBuilder.create().texOffs(18, 25).mirror().addBox(-1.85F, -0.7555F, -1.0419F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(31, 27).mirror().addBox(-1.4F, 3.1445F, -0.5419F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.3F)).mirror(false), PartPose.offsetAndRotation(-2.05F, -2.7686F, 0.1219F, -0.2531F, 0.0F, 0.0F));

        arm_R.addOrReplaceChild("shoulder_r1", CubeListBuilder.create().texOffs(34, 8).mirror().addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offsetAndRotation(-1.1542F, 0.4536F, -0.0419F, 0.0F, 0.0F, -0.1745F));

        PartDefinition arm_low_R = arm_R.addOrReplaceChild("arm_low_R", CubeListBuilder.create().texOffs(30, 21).mirror().addBox(-0.5F, -0.0118F, -0.4918F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offset(-0.9F, 6.2064F, -0.0501F));

        arm_low_R.addOrReplaceChild("hand_R", CubeListBuilder.create().texOffs(36, 21).mirror().addBox(-0.5F, -0.0119F, -0.4918F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offset(0.0F, 3.8F, 0.0F));

        PartDefinition arm_L = torso_up.addOrReplaceChild("arm_L", CubeListBuilder.create().texOffs(18, 25).addBox(-0.15F, -0.7304F, -1.0387F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(31, 27).addBox(0.4F, 3.1696F, -0.5387F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(2.05F, -2.7936F, 0.1251F, -0.2531F, 0.0F, 0.0F));

        arm_L.addOrReplaceChild("shoulder_r2", CubeListBuilder.create().texOffs(34, 8).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(1.1542F, 0.4787F, -0.0387F, 0.0F, 0.0F, 0.1745F));

        PartDefinition arm_low_L = arm_L.addOrReplaceChild("arm_low_L", CubeListBuilder.create().texOffs(30, 21).addBox(-0.5F, -0.0118F, -0.4918F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.offset(0.9F, 6.2314F, -0.0469F));

        arm_low_L.addOrReplaceChild("hand_L", CubeListBuilder.create().texOffs(36, 21).addBox(-0.5F, -0.0119F, -0.4918F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, 3.8F, 0.0F));

        PartDefinition body_low = Spillways_model.addOrReplaceChild("body_low", CubeListBuilder.create().texOffs(10, 26).addBox(-1.0F, -0.3333F, -1.0667F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.09F)), PartPose.offset(0.0F, -9.0667F, -0.1333F));

        PartDefinition cloth_tail = body_low.addOrReplaceChild("cloth_tail", CubeListBuilder.create(), PartPose.offset(0.0129F, -0.0758F, 0.9256F));

        cloth_tail.addOrReplaceChild("cloth_b_r1", CubeListBuilder.create().texOffs(35, 18).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0293F, 2.0299F, 0.2908F, 0.0928F, -0.0924F, 0.7811F));

        PartDefinition leg_L = body_low.addOrReplaceChild("leg_L", CubeListBuilder.create().texOffs(25, 10).addBox(0.2F, -1.4F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offset(1.0F, 1.9667F, 0.0333F));

        leg_L.addOrReplaceChild("hip_r1", CubeListBuilder.create().texOffs(34, 8).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(1.549F, -0.3607F, 0.0F, 0.0F, 0.0F, 0.1745F));

        PartDefinition leg_low_L = leg_L.addOrReplaceChild("leg_low_L", CubeListBuilder.create().texOffs(30, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.4F)), PartPose.offset(1.1F, 5.8F, 0.0F));

        leg_low_L.addOrReplaceChild("foot_L", CubeListBuilder.create().texOffs(36, 21).addBox(-0.5F, -0.1F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.1F, 3.3F, 0.0F));

        PartDefinition leg_R = body_low.addOrReplaceChild("leg_R", CubeListBuilder.create().texOffs(25, 10).mirror().addBox(-2.2F, -1.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offset(-1.0F, 2.0667F, 0.0333F));

        leg_R.addOrReplaceChild("hip_r2", CubeListBuilder.create().texOffs(34, 8).mirror().addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offsetAndRotation(-1.549F, -0.4607F, 0.0F, 0.0F, 0.0F, -0.1745F));

        PartDefinition leg_low_R = leg_R.addOrReplaceChild("leg_low_R", CubeListBuilder.create().texOffs(30, 21).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.4F)).mirror(false), PartPose.offset(-1.1F, 5.7F, 0.0F));

        leg_low_R.addOrReplaceChild("foot_R", CubeListBuilder.create().texOffs(36, 21).mirror().addBox(-0.5F, -0.1F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offset(0.1F, 3.3F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}
