// Made with Blockbench 5.0.7
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class Spillways<T extends spillways> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "spillways"), "main");
	private final ModelPart Spillways_model;
	private final ModelPart body_up;
	private final ModelPart torso_up;
	private final ModelPart head;
	private final ModelPart hat;
	private final ModelPart hat2;
	private final ModelPart hat3;
	private final ModelPart hat_end;
	private final ModelPart arm_R;
	private final ModelPart arm_low_R;
	private final ModelPart hand_R;
	private final ModelPart arm_L;
	private final ModelPart arm_low_L;
	private final ModelPart hand_L;
	private final ModelPart body_low;
	private final ModelPart cloth_tail;
	private final ModelPart leg_L;
	private final ModelPart leg_low_L;
	private final ModelPart foot_L;
	private final ModelPart leg_R;
	private final ModelPart leg_low_R;
	private final ModelPart foot_R;

	public Spillways(ModelPart root) {
		this.Spillways_model = root.getChild("Spillways_model");
		this.body_up = this.Spillways_model.getChild("body_up");
		this.torso_up = this.body_up.getChild("torso_up");
		this.head = this.torso_up.getChild("head");
		this.hat = this.head.getChild("hat");
		this.hat2 = this.hat.getChild("hat2");
		this.hat3 = this.hat2.getChild("hat3");
		this.hat_end = this.hat3.getChild("hat_end");
		this.arm_R = this.torso_up.getChild("arm_R");
		this.arm_low_R = this.arm_R.getChild("arm_low_R");
		this.hand_R = this.arm_low_R.getChild("hand_R");
		this.arm_L = this.torso_up.getChild("arm_L");
		this.arm_low_L = this.arm_L.getChild("arm_low_L");
		this.hand_L = this.arm_low_L.getChild("hand_L");
		this.body_low = this.Spillways_model.getChild("body_low");
		this.cloth_tail = this.body_low.getChild("cloth_tail");
		this.leg_L = this.body_low.getChild("leg_L");
		this.leg_low_L = this.leg_L.getChild("leg_low_L");
		this.foot_L = this.leg_low_L.getChild("foot_L");
		this.leg_R = this.body_low.getChild("leg_R");
		this.leg_low_R = this.leg_R.getChild("leg_low_R");
		this.foot_R = this.leg_low_R.getChild("foot_R");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition Spillways_model = partdefinition.addOrReplaceChild("Spillways_model", CubeListBuilder.create(), PartPose.offset(0.0F, 18.0F, 0.1F));

		PartDefinition body_up = Spillways_model.addOrReplaceChild("body_up", CubeListBuilder.create().texOffs(0, 25).addBox(-1.5F, -4.025F, -1.0875F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, -9.075F, -0.1125F));

		PartDefinition cloth_f_r1 = body_up.addOrReplaceChild("cloth_f_r1", CubeListBuilder.create().texOffs(34, 13).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(1.077F, 0.1821F, -0.0363F, 2.3408F, -1.5619F, -2.599F));

		PartDefinition cloth_f_r2 = body_up.addOrReplaceChild("cloth_f_r2", CubeListBuilder.create().texOffs(34, 13).mirror().addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offsetAndRotation(-1.0694F, 0.1775F, -0.0363F, 2.3408F, 1.5619F, 2.599F));

		PartDefinition torso_up = body_up.addOrReplaceChild("torso_up", CubeListBuilder.create().texOffs(20, 1).addBox(-0.5F, -5.45F, -0.625F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F))
		.texOffs(0, 18).addBox(-2.0F, -3.65F, -1.625F, 4.0F, 4.0F, 3.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, -4.075F, 0.0375F));

		PartDefinition boob_r1 = torso_up.addOrReplaceChild("boob_r1", CubeListBuilder.create().texOffs(14, 20).addBox(-2.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.9052F, -0.6712F, -0.5672F, 0.0F, 0.0F));

		PartDefinition head = torso_up.addOrReplaceChild("head", CubeListBuilder.create().texOffs(26, 0).addBox(-2.0F, -4.1F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.1F))
		.texOffs(26, 8).addBox(-2.0F, -2.5F, -1.9F, 4.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.05F, -0.025F));

		PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(0, 0).addBox(-2.9901F, -3.1581F, -2.8943F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.0099F, -3.9419F, -0.0057F));

		PartDefinition edge_w_r1 = hat.addOrReplaceChild("edge_w_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5098F, 0.7666F, 0.1566F, 0.0F, 1.5708F, -1.1345F));

		PartDefinition edge_e_r1 = hat.addOrReplaceChild("edge_e_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4264F, 0.676F, 0.0625F, 0.0F, -1.5708F, 1.1345F));

		PartDefinition edge_s_r1 = hat.addOrReplaceChild("edge_s_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0367F, 0.676F, 3.5705F, 1.1345F, 0.0F, 0.0F));

		PartDefinition edge_n_r1 = hat.addOrReplaceChild("edge_n_r1", CubeListBuilder.create().texOffs(21, 32).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0603F, 0.7666F, -3.3928F, -2.0071F, 0.0F, -3.1416F));

		PartDefinition edge_w_r2 = hat.addOrReplaceChild("edge_w_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.6407F, 0.704F, 0.1566F, 0.0F, 1.5708F, -1.0908F));

		PartDefinition edge_e_r2 = hat.addOrReplaceChild("edge_e_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5612F, 0.6153F, 0.0625F, 0.0F, -1.5708F, 1.0908F));

		PartDefinition edge_s_r2 = hat.addOrReplaceChild("edge_s_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0367F, 0.6153F, 3.7053F, 1.0908F, 0.0F, 0.0F));

		PartDefinition edge_n_r2 = hat.addOrReplaceChild("edge_n_r2", CubeListBuilder.create().texOffs(20, 36).addBox(-5.0F, -1.5F, 0.0F, 10.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0603F, 0.704F, -3.5237F, -2.0508F, 0.0F, -3.1416F));

		PartDefinition side_L_r1 = hat.addOrReplaceChild("side_L_r1", CubeListBuilder.create().texOffs(0, 39).addBox(-7.5F, -2.5F, 0.0F, 15.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0393F, -0.6581F, 0.0748F, 0.0F, 0.7854F, 0.0F));

		PartDefinition side_R_r1 = hat.addOrReplaceChild("side_R_r1", CubeListBuilder.create().texOffs(0, 39).mirror().addBox(-7.5F, -2.5F, 0.0F, 15.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.0314F, -0.6581F, 0.0658F, -3.1416F, 0.7854F, 3.1416F));

		PartDefinition hat2 = hat.addOrReplaceChild("hat2", CubeListBuilder.create().texOffs(0, 10).addBox(-1.99F, -4.0824F, -1.8977F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.0001F, -3.0757F, 0.0034F));

		PartDefinition side_L_r2 = hat2.addOrReplaceChild("side_L_r2", CubeListBuilder.create().texOffs(21, 39).addBox(-4.5F, -2.0F, 0.0F, 9.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0294F, -2.0824F, 0.0815F, 0.0F, 0.7854F, 0.0F));

		PartDefinition side_R_r2 = hat2.addOrReplaceChild("side_R_r2", CubeListBuilder.create().texOffs(21, 39).mirror().addBox(-4.5F, -2.0F, 0.0F, 9.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.0305F, -2.0824F, 0.0617F, 0.0F, -0.7854F, 0.0F));

		PartDefinition hat3 = hat2.addOrReplaceChild("hat3", CubeListBuilder.create().texOffs(16, 12).addBox(-0.9799F, -4.1375F, -0.8951F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.01F, -3.945F, -0.0027F));

		PartDefinition side_R_r3 = hat3.addOrReplaceChild("side_R_r3", CubeListBuilder.create().texOffs(8, 34).mirror().addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0699F, -2.6375F, 0.1563F, -3.1416F, 0.7854F, 3.1416F));

		PartDefinition side_L_r3 = hat3.addOrReplaceChild("side_L_r3", CubeListBuilder.create().texOffs(8, 34).addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1109F, -2.6375F, 0.0141F, 0.0F, 0.7854F, 0.0F));

		PartDefinition hat_end = hat3.addOrReplaceChild("hat_end", CubeListBuilder.create(), PartPose.offset(0.0211F, -3.0875F, 0.0049F));

		PartDefinition main5_r1 = hat_end.addOrReplaceChild("main5_r1", CubeListBuilder.create().texOffs(0, 26).addBox(0.0F, -3.0F, -3.5F, 0.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.001F, -3.05F, 0.101F, 0.0F, -1.5708F, 0.0F));

		PartDefinition arm_R = torso_up.addOrReplaceChild("arm_R", CubeListBuilder.create().texOffs(18, 25).mirror().addBox(-1.85F, -0.7555F, -1.0419F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(31, 27).mirror().addBox(-1.4F, 3.1445F, -0.5419F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.3F)).mirror(false), PartPose.offsetAndRotation(-2.05F, -2.7686F, 0.1219F, -0.2531F, 0.0F, 0.0F));

		PartDefinition shoulder_r1 = arm_R.addOrReplaceChild("shoulder_r1", CubeListBuilder.create().texOffs(34, 8).mirror().addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offsetAndRotation(-1.1542F, 0.4536F, -0.0419F, 0.0F, 0.0F, -0.1745F));

		PartDefinition arm_low_R = arm_R.addOrReplaceChild("arm_low_R", CubeListBuilder.create().texOffs(30, 21).mirror().addBox(-0.5F, -0.0118F, -0.4918F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offset(-0.9F, 6.2064F, -0.0501F));

		PartDefinition hand_R = arm_low_R.addOrReplaceChild("hand_R", CubeListBuilder.create().texOffs(36, 21).mirror().addBox(-0.5F, -0.0119F, -0.4918F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offset(0.0F, 3.8F, 0.0F));

		PartDefinition arm_L = torso_up.addOrReplaceChild("arm_L", CubeListBuilder.create().texOffs(18, 25).addBox(-0.15F, -0.7304F, -1.0387F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(31, 27).addBox(0.4F, 3.1696F, -0.5387F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(2.05F, -2.7936F, 0.1251F, -0.2531F, 0.0F, 0.0F));

		PartDefinition shoulder_r2 = arm_L.addOrReplaceChild("shoulder_r2", CubeListBuilder.create().texOffs(34, 8).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(1.1542F, 0.4787F, -0.0387F, 0.0F, 0.0F, 0.1745F));

		PartDefinition arm_low_L = arm_L.addOrReplaceChild("arm_low_L", CubeListBuilder.create().texOffs(30, 21).addBox(-0.5F, -0.0118F, -0.4918F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.offset(0.9F, 6.2314F, -0.0469F));

		PartDefinition hand_L = arm_low_L.addOrReplaceChild("hand_L", CubeListBuilder.create().texOffs(36, 21).addBox(-0.5F, -0.0119F, -0.4918F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, 3.8F, 0.0F));

		PartDefinition body_low = Spillways_model.addOrReplaceChild("body_low", CubeListBuilder.create().texOffs(10, 26).addBox(-1.0F, -0.3333F, -1.0667F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.09F)), PartPose.offset(0.0F, -9.0667F, -0.1333F));

		PartDefinition cloth_tail = body_low.addOrReplaceChild("cloth_tail", CubeListBuilder.create(), PartPose.offset(0.0129F, -0.0758F, 0.9256F));

		PartDefinition cloth_b_r1 = cloth_tail.addOrReplaceChild("cloth_b_r1", CubeListBuilder.create().texOffs(35, 18).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0293F, 2.0299F, 0.2908F, 0.0928F, -0.0924F, 0.7811F));

		PartDefinition leg_L = body_low.addOrReplaceChild("leg_L", CubeListBuilder.create().texOffs(25, 10).addBox(0.2F, -1.4F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offset(1.0F, 1.9667F, 0.0333F));

		PartDefinition hip_r1 = leg_L.addOrReplaceChild("hip_r1", CubeListBuilder.create().texOffs(34, 8).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(1.549F, -0.3607F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_low_L = leg_L.addOrReplaceChild("leg_low_L", CubeListBuilder.create().texOffs(30, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.4F)), PartPose.offset(1.1F, 5.8F, 0.0F));

		PartDefinition foot_L = leg_low_L.addOrReplaceChild("foot_L", CubeListBuilder.create().texOffs(36, 21).addBox(-0.5F, -0.1F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offset(-0.1F, 3.3F, 0.0F));

		PartDefinition leg_R = body_low.addOrReplaceChild("leg_R", CubeListBuilder.create().texOffs(25, 10).mirror().addBox(-2.2F, -1.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offset(-1.0F, 2.0667F, 0.0333F));

		PartDefinition hip_r2 = leg_R.addOrReplaceChild("hip_r2", CubeListBuilder.create().texOffs(34, 8).mirror().addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offsetAndRotation(-1.549F, -0.4607F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_low_R = leg_R.addOrReplaceChild("leg_low_R", CubeListBuilder.create().texOffs(30, 21).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.4F)).mirror(false), PartPose.offset(-1.1F, 5.7F, 0.0F));

		PartDefinition foot_R = leg_low_R.addOrReplaceChild("foot_R", CubeListBuilder.create().texOffs(36, 21).mirror().addBox(-0.5F, -0.1F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offset(0.1F, 3.3F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(spillways entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		Spillways_model.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}