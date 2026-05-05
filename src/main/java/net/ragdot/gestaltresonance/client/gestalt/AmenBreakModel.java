package net.ragdot.gestaltresonance.client.gestalt;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Model for the Amen Break gestalt.
 * <p>
 * Made with Blockbench 5.0.7.
 */
public class AmenBreakModel extends GestaltModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "amen_break"), "main");

    private final ModelPart root;

    public AmenBreakModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    protected AnimationDefinition introAnimation() {
        return AmenBreakAnimations.INTRO;
    }

    @Override
    protected AnimationDefinition idleAnimation() {
        return AmenBreakAnimations.IDLE;
    }

    @Override
    protected AnimationDefinition guardAnimation() {
        return AmenBreakAnimations.GUARD;
    }

    @Override
    protected AnimationDefinition throwAnimation() {
        return AmenBreakAnimations.THROW;
    }

    @Override
    protected AnimationDefinition grabAnimation() {
        return AmenBreakAnimations.GRAB;
    }

    @Override
    protected AnimationDefinition miningAnimation() {
        return AmenBreakAnimations.MINING;
    }

    @Override
    protected AnimationDefinition hit1Animation() { return AmenBreakAnimations.HIT1; }

    @Override
    protected AnimationDefinition hit2Animation() { return AmenBreakAnimations.HIT2; }

    @Override
    protected AnimationDefinition hit3Animation() { return AmenBreakAnimations.HIT3; }

    @Override
    protected AnimationDefinition windupAnimation() { return AmenBreakAnimations.WINDUP; }

    @Override
    protected AnimationDefinition swimAnimation() { return AmenBreakAnimations.SWIM; }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition AmenBreak = partdefinition.addOrReplaceChild("AmenBreak", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 16.1F, 0.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition BodyT = AmenBreak.addOrReplaceChild("BodyT", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -10.8F, -1.4F, 0.0873F, 0.0F, 0.0F));

        BodyT.addOrReplaceChild("BodyMid_r1", CubeListBuilder.create().texOffs(50, 14).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.9F, 0.8F, -0.1309F, 0.0F, 0.0F));

        PartDefinition Torso = BodyT.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0133F, -3.3445F, 0.3314F, 0.0873F, 0.0F, 0.0F));

        Torso.addOrReplaceChild("Collar_r1", CubeListBuilder.create().texOffs(17, 36).addBox(-3.8014F, -0.8937F, -3.2778F, 7.0F, 3.0F, 7.0F, new CubeDeformation(0.08F)), PartPose.offsetAndRotation(0.0147F, -8.3619F, 2.5464F, -0.6981F, 0.637F, -0.4712F));

        Torso.addOrReplaceChild("BodyTop_r1", CubeListBuilder.create().texOffs(21, 24).addBox(-3.0F, -8.0F, -1.0F, 6.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0133F, -0.2555F, -0.9314F, -0.2618F, 0.0F, 0.0F));

        PartDefinition Head = Torso.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offset(-0.1581F, -9.4099F, 3.3165F));

        Head.addOrReplaceChild("Beak_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0923F, -0.6203F, -0.9095F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1435F, 0.7268F, 5.0754F, -0.6013F, 0.6719F, -0.4126F));

        Head.addOrReplaceChild("FeatherPetal3L_r1", CubeListBuilder.create().texOffs(42, 51).addBox(-3.5907F, -3.5273F, -0.0681F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8683F, -2.4862F, -2.6479F, -0.4359F, -0.4197F, -1.6839F));

        Head.addOrReplaceChild("FeatherPetal3R_r1", CubeListBuilder.create().texOffs(42, 51).mirror().addBox(-2.3288F, -3.6428F, -0.0823F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.211F, -2.4862F, -2.6479F, -0.4359F, 0.4197F, 1.6839F));

        Head.addOrReplaceChild("FeatherPetal2L_r1", CubeListBuilder.create().texOffs(42, 51).addBox(-3.665F, -3.6292F, 0.1276F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.2683F, -5.4862F, -1.2479F, -0.5294F, 0.1812F, -0.7218F));

        Head.addOrReplaceChild("FeatherPetal2R_r1", CubeListBuilder.create().texOffs(42, 51).mirror().addBox(-2.1962F, -3.6086F, 0.1102F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.611F, -5.4862F, -1.2479F, -0.5294F, -0.1812F, 0.7218F));

        Head.addOrReplaceChild("FeatherPetal1L_r1", CubeListBuilder.create().texOffs(42, 51).addBox(-3.3939F, -3.906F, 0.0334F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.1683F, -7.4862F, -1.2479F, -0.3136F, 0.4293F, 0.0674F));

        Head.addOrReplaceChild("FeatherPetal1R_r1", CubeListBuilder.create().texOffs(42, 51).mirror().addBox(-2.5215F, -3.7927F, 0.0295F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.511F, -7.4862F, -1.2479F, -0.3136F, -0.4293F, -0.0674F));

        Head.addOrReplaceChild("FeatherPetalM_r1", CubeListBuilder.create().texOffs(42, 51).addBox(-3.7705F, -3.6292F, 0.203F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3317F, -9.2862F, -2.6479F, -0.3231F, 0.272F, 0.7494F));

        Head.addOrReplaceChild("EYEL_r1", CubeListBuilder.create().texOffs(27, 3).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-3.2516F, -2.4553F, 3.1793F, -0.4863F, 0.7208F, -0.3483F));

        Head.addOrReplaceChild("EYER_r1", CubeListBuilder.create().texOffs(27, 0).mirror().addBox(-1.0F, -1.5F, -1.9F, 2.0F, 3.0F, 3.0F, new CubeDeformation(-0.2F)).mirror(false), PartPose.offsetAndRotation(3.2589F, -2.3536F, 3.5403F, -0.5126F, -0.7234F, 0.3422F));

        Head.addOrReplaceChild("eyeback2_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -1.9F, 0.6F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.8874F, -4.0842F, 2.5437F, -0.4809F, 0.7227F, -0.336F));

        Head.addOrReplaceChild("eyeback1_r1", CubeListBuilder.create().texOffs(0, 10).addBox(-0.9F, -1.7F, -2.1F, 0.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4552F, -4.0889F, 2.1377F, -0.4701F, 0.6989F, -0.3455F));

        Head.addOrReplaceChild("Head_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.3516F, -3.0331F, -4.2227F, 9.0F, 7.0F, 9.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-0.1188F, -3.6279F, 1.3973F, -0.4947F, 0.711F, -0.3486F));

        PartDefinition ArmRight = Torso.addOrReplaceChild("ArmRight", CubeListBuilder.create(), PartPose.offsetAndRotation(2.6096F, -5.7289F, 1.3061F, -0.075F, 0.026F, 0.1577F));

        ArmRight.addOrReplaceChild("ArmRT_r1", CubeListBuilder.create().texOffs(12, 25).mirror().addBox(-1.0F, -2.0F, -2.0F, 1.0F, 11.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(1.7273F, 1.3046F, 0.8606F, -0.1719F, -0.0302F, -0.3901F));

        PartDefinition LowerArmRight = ArmRight.addOrReplaceChild("LowerArmRight", CubeListBuilder.create(), PartPose.offsetAndRotation(4.4818F, 9.0913F, -0.6821F, 0.2141F, 0.2129F, -0.3945F));

        LowerArmRight.addOrReplaceChild("PodR3_r1", CubeListBuilder.create().texOffs(40, 7).mirror().addBox(-0.9078F, -0.9205F, -1.072F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)).mirror(false), PartPose.offsetAndRotation(0.0547F, 3.8381F, -3.2039F, -0.4028F, -0.0803F, 0.0342F));

        LowerArmRight.addOrReplaceChild("PodR2_r1", CubeListBuilder.create().texOffs(40, 7).mirror().addBox(-0.991F, -0.9357F, -1.076F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)).mirror(false), PartPose.offsetAndRotation(-0.1453F, 5.8381F, -2.4039F, -0.3979F, 0.0544F, 0.1287F));

        LowerArmRight.addOrReplaceChild("PodR1_r1", CubeListBuilder.create().texOffs(40, 7).mirror().addBox(-0.9962F, -0.8695F, -1.1515F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)).mirror(false), PartPose.offsetAndRotation(-0.4453F, 7.8381F, -1.4039F, -0.4094F, -0.0285F, 0.1546F));

        LowerArmRight.addOrReplaceChild("FingersRight_r1", CubeListBuilder.create().texOffs(34, 13).mirror().addBox(0.1F, -2.5F, -3.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.0971F, 12.3152F, 4.3033F, 0.5149F, 0.0152F, 0.0086F));

        LowerArmRight.addOrReplaceChild("ArmRL_r1", CubeListBuilder.create().texOffs(0, 23).mirror().addBox(-0.5F, -8.0F, -2.0F, 1.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.1325F, 8.0524F, 1.1331F, 0.3902F, -0.0393F, 0.1249F));

        PartDefinition ArmLeft = Torso.addOrReplaceChild("ArmLeft", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5665F, -6.0991F, 1.7137F, -0.0436F, 0.0436F, -0.2163F));

        ArmLeft.addOrReplaceChild("ArmLT_r1", CubeListBuilder.create().texOffs(12, 25).addBox(0.0F, -2.0F, -2.0F, 1.0F, 11.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.8283F, 1.3884F, 0.4494F, -0.2122F, 0.0487F, 0.4754F));

        PartDefinition LowerArmLeft = ArmLeft.addOrReplaceChild("LowerArmLeft", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.3473F, 9.1386F, -1.4966F, 0.1129F, -0.1802F, 0.5423F));

        LowerArmLeft.addOrReplaceChild("PodL3_r1", CubeListBuilder.create().texOffs(40, 7).addBox(-0.3285F, -0.7357F, -0.9605F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.6864F, 7.5608F, -2.2691F, -1.8668F, -0.0274F, -0.0823F));

        LowerArmLeft.addOrReplaceChild("PodL2_r1", CubeListBuilder.create().texOffs(40, 7).addBox(-0.302F, -0.9026F, -0.9776F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.1864F, 3.5608F, -4.0691F, -1.8838F, -0.0269F, -0.083F));

        LowerArmLeft.addOrReplaceChild("PodL1_r1", CubeListBuilder.create().texOffs(40, 7).addBox(-0.0156F, -0.9002F, -0.9915F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.1864F, 5.5608F, -3.269F, -1.8619F, -0.0029F, -0.1251F));

        LowerArmLeft.addOrReplaceChild("FingersLeft_r1", CubeListBuilder.create().texOffs(34, 13).addBox(-1.0F, -2.0F, -3.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9291F, 12.15F, 4.0669F, 0.4408F, -0.0215F, -0.0294F));

        LowerArmLeft.addOrReplaceChild("ArmLL_r1", CubeListBuilder.create().texOffs(0, 23).addBox(0.0F, -2.0F, -3.0F, 1.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2136F, 2.2608F, -0.169F, 0.3902F, 0.0393F, -0.1249F));

        PartDefinition BodyB = AmenBreak.addOrReplaceChild("BodyB", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -11.1F, -1.3F, -0.0873F, 0.0F, 0.0F));

        BodyB.addOrReplaceChild("BodyLow_r1", CubeListBuilder.create().texOffs(56, 2).addBox(-1.0F, 2.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, -0.4F, 0.1745F, 0.0F, 0.0F));

        PartDefinition LegRight = BodyB.addOrReplaceChild("LegRight", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9755F, 4.5999F, 0.8146F, -0.3189F, -0.1897F, 0.057F));

        LegRight.addOrReplaceChild("LegRight_r1", CubeListBuilder.create().texOffs(54, 25).mirror().addBox(-1.0F, -3.5F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(1.2803F, 3.1647F, 1.1985F, 0.4363F, 0.2182F, 0.0F));

        PartDefinition LegRightLow = LegRight.addOrReplaceChild("LegRightLow", CubeListBuilder.create(), PartPose.offsetAndRotation(1.8795F, 5.7629F, 3.9837F, 0.0F, -0.0349F, 0.0F));

        LegRightLow.addOrReplaceChild("ToesRight_r1", CubeListBuilder.create().texOffs(44, 1).mirror().addBox(-0.8963F, -1.9011F, -0.1669F, 2.0F, 2.0F, 3.0F, new CubeDeformation(-0.1F)).mirror(false), PartPose.offsetAndRotation(-0.2277F, 6.2212F, -0.7975F, -0.1223F, 0.2096F, 0.0314F));

        LegRightLow.addOrReplaceChild("LegRightLow_r1", CubeListBuilder.create().texOffs(44, 26).mirror().addBox(-0.94F, 0.0607F, -2.0371F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.0369F, -0.1204F, 0.0291F, 0.1691F, 0.2224F, -0.0055F));

        PartDefinition LegLeft = BodyB.addOrReplaceChild("LegLeft", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.9606F, 4.6115F, 0.7737F, -0.2692F, 0.1805F, -0.0545F));

        LegLeft.addOrReplaceChild("LegLeft_r1", CubeListBuilder.create().texOffs(54, 25).addBox(-1.0F, -3.5F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.2168F, 3.2537F, 1.0618F, 0.3927F, -0.2182F, 0.0F));

        PartDefinition LegLeftLow = LegLeft.addOrReplaceChild("LegLeftLow", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.7472F, 5.9122F, 3.7535F, 0.2955F, 0.0088F, 0.0799F));

        LegLeftLow.addOrReplaceChild("ToesLeft_r1", CubeListBuilder.create().texOffs(44, 1).addBox(-1.0865F, -1.9267F, 0.0377F, 2.0F, 2.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.7904F, 5.5067F, -3.0923F, -0.4834F, -0.2404F, -0.0199F));

        LegLeftLow.addOrReplaceChild("LegLeftLow_r1", CubeListBuilder.create().texOffs(44, 26).mirror().addBox(-0.9993F, 0.0181F, -2.0937F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.1086F, 0.0261F, 0.0734F, -0.1858F, -0.2245F, -0.0101F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}
