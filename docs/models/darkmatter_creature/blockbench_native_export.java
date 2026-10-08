// Made with Blockbench 5.2.1
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class darkmatter_creature<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "darkmatter_creature"), "main");
	private final ModelPart all;
	private final ModelPart head_0;
	private final ModelPart antenna_left;
	private final ModelPart antenna_right;
	private final ModelPart horn;
	private final ModelPart jaw_left;
	private final ModelPart jaw_right;
	private final ModelPart head_1;
	private final ModelPart antenna_left;
	private final ModelPart antenna_right;
	private final ModelPart horn;
	private final ModelPart cannon;
	private final ModelPart head_2;
	private final ModelPart antenna_left;
	private final ModelPart antenna_right;
	private final ModelPart horn;
	private final ModelPart cannon;
	private final ModelPart torso_0;
	private final ModelPart elytra_left;
	private final ModelPart elytra_right;
	private final ModelPart torso_1;
	private final ModelPart elytra_left;
	private final ModelPart wing_left;
	private final ModelPart wing_blade_0;
	private final ModelPart wing_blade_1;
	private final ModelPart wing_blade_2;
	private final ModelPart wing_blade_3;
	private final ModelPart wing_blade_4;
	private final ModelPart elytra_right;
	private final ModelPart wing_right;
	private final ModelPart wing_blade_0;
	private final ModelPart wing_blade_1;
	private final ModelPart wing_blade_2;
	private final ModelPart wing_blade_3;
	private final ModelPart wing_blade_4;
	private final ModelPart torso_2;
	private final ModelPart elytra_left;
	private final ModelPart elytra_right;
	private final ModelPart fin_left;
	private final ModelPart fin_right;
	private final ModelPart swim_tail;
	private final ModelPart limbs_0;
	private final ModelPart leg_left_front;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_left_middle;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_left_rear;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_front;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_middle;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_rear;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart limbs_1;
	private final ModelPart leg_left_front;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_left_middle;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_left_rear;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_front;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_middle;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_rear;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart limbs_2;
	private final ModelPart leg_left_front;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_left_middle;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_left_rear;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_front;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_middle;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart leg_right_rear;
	private final ModelPart tibia;
	private final ModelPart tarsus;
	private final ModelPart additional_0;
	private final ModelPart additional_1;
	private final ModelPart armor_-1_0;
	private final ModelPart armor_-1_1;
	private final ModelPart armor_-1_2;
	private final ModelPart armor_-1_3;
	private final ModelPart armor_1_0;
	private final ModelPart armor_1_1;
	private final ModelPart armor_1_2;
	private final ModelPart armor_1_3;
	private final ModelPart additional_2;
	private final ModelPart sensor_left;
	private final ModelPart sensor_right;
	private final ModelPart additional_3;
	private final ModelPart dorsal_cannon;

	public darkmatter_creature(ModelPart root) {
		this.all = root.getChild("all");
		this.head_0 = this.all.getChild("head_0");
		this.antenna_left = this.head_0.getChild("antenna_left");
		this.antenna_right = this.head_0.getChild("antenna_right");
		this.horn = this.head_0.getChild("horn");
		this.jaw_left = this.head_0.getChild("jaw_left");
		this.jaw_right = this.head_0.getChild("jaw_right");
		this.head_1 = this.all.getChild("head_1");
		this.antenna_left = this.head_1.getChild("antenna_left");
		this.antenna_right = this.head_1.getChild("antenna_right");
		this.horn = this.head_1.getChild("horn");
		this.cannon = this.head_1.getChild("cannon");
		this.head_2 = this.all.getChild("head_2");
		this.antenna_left = this.head_2.getChild("antenna_left");
		this.antenna_right = this.head_2.getChild("antenna_right");
		this.horn = this.head_2.getChild("horn");
		this.cannon = this.head_2.getChild("cannon");
		this.torso_0 = this.all.getChild("torso_0");
		this.elytra_left = this.torso_0.getChild("elytra_left");
		this.elytra_right = this.torso_0.getChild("elytra_right");
		this.torso_1 = this.all.getChild("torso_1");
		this.elytra_left = this.torso_1.getChild("elytra_left");
		this.wing_left = this.torso_1.getChild("wing_left");
		this.wing_blade_0 = this.wing_left.getChild("wing_blade_0");
		this.wing_blade_1 = this.wing_left.getChild("wing_blade_1");
		this.wing_blade_2 = this.wing_left.getChild("wing_blade_2");
		this.wing_blade_3 = this.wing_left.getChild("wing_blade_3");
		this.wing_blade_4 = this.wing_left.getChild("wing_blade_4");
		this.elytra_right = this.torso_1.getChild("elytra_right");
		this.wing_right = this.torso_1.getChild("wing_right");
		this.wing_blade_0 = this.wing_right.getChild("wing_blade_0");
		this.wing_blade_1 = this.wing_right.getChild("wing_blade_1");
		this.wing_blade_2 = this.wing_right.getChild("wing_blade_2");
		this.wing_blade_3 = this.wing_right.getChild("wing_blade_3");
		this.wing_blade_4 = this.wing_right.getChild("wing_blade_4");
		this.torso_2 = this.all.getChild("torso_2");
		this.elytra_left = this.torso_2.getChild("elytra_left");
		this.elytra_right = this.torso_2.getChild("elytra_right");
		this.fin_left = this.torso_2.getChild("fin_left");
		this.fin_right = this.torso_2.getChild("fin_right");
		this.swim_tail = this.torso_2.getChild("swim_tail");
		this.limbs_0 = this.all.getChild("limbs_0");
		this.leg_left_front = this.limbs_0.getChild("leg_left_front");
		this.tibia = this.leg_left_front.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_left_middle = this.limbs_0.getChild("leg_left_middle");
		this.tibia = this.leg_left_middle.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_left_rear = this.limbs_0.getChild("leg_left_rear");
		this.tibia = this.leg_left_rear.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_front = this.limbs_0.getChild("leg_right_front");
		this.tibia = this.leg_right_front.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_middle = this.limbs_0.getChild("leg_right_middle");
		this.tibia = this.leg_right_middle.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_rear = this.limbs_0.getChild("leg_right_rear");
		this.tibia = this.leg_right_rear.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.limbs_1 = this.all.getChild("limbs_1");
		this.leg_left_front = this.limbs_1.getChild("leg_left_front");
		this.tibia = this.leg_left_front.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_left_middle = this.limbs_1.getChild("leg_left_middle");
		this.tibia = this.leg_left_middle.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_left_rear = this.limbs_1.getChild("leg_left_rear");
		this.tibia = this.leg_left_rear.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_front = this.limbs_1.getChild("leg_right_front");
		this.tibia = this.leg_right_front.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_middle = this.limbs_1.getChild("leg_right_middle");
		this.tibia = this.leg_right_middle.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_rear = this.limbs_1.getChild("leg_right_rear");
		this.tibia = this.leg_right_rear.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.limbs_2 = this.all.getChild("limbs_2");
		this.leg_left_front = this.limbs_2.getChild("leg_left_front");
		this.tibia = this.leg_left_front.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_left_middle = this.limbs_2.getChild("leg_left_middle");
		this.tibia = this.leg_left_middle.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_left_rear = this.limbs_2.getChild("leg_left_rear");
		this.tibia = this.leg_left_rear.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_front = this.limbs_2.getChild("leg_right_front");
		this.tibia = this.leg_right_front.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_middle = this.limbs_2.getChild("leg_right_middle");
		this.tibia = this.leg_right_middle.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.leg_right_rear = this.limbs_2.getChild("leg_right_rear");
		this.tibia = this.leg_right_rear.getChild("tibia");
		this.tarsus = this.tibia.getChild("tarsus");
		this.additional_0 = this.all.getChild("additional_0");
		this.additional_1 = this.all.getChild("additional_1");
		this.armor_-1_0 = this.additional_1.getChild("armor_-1_0");
		this.armor_-1_1 = this.additional_1.getChild("armor_-1_1");
		this.armor_-1_2 = this.additional_1.getChild("armor_-1_2");
		this.armor_-1_3 = this.additional_1.getChild("armor_-1_3");
		this.armor_1_0 = this.additional_1.getChild("armor_1_0");
		this.armor_1_1 = this.additional_1.getChild("armor_1_1");
		this.armor_1_2 = this.additional_1.getChild("armor_1_2");
		this.armor_1_3 = this.additional_1.getChild("armor_1_3");
		this.additional_2 = this.all.getChild("additional_2");
		this.sensor_left = this.additional_2.getChild("sensor_left");
		this.sensor_right = this.additional_2.getChild("sensor_right");
		this.additional_3 = this.all.getChild("additional_3");
		this.dorsal_cannon = this.additional_3.getChild("dorsal_cannon");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition head_0 = all.addOrReplaceChild("head_0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.6F, -2.3F, -3.5F, 5.2F, 3.8F, 4.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.1F, -3.0F, -2.8F, 4.2F, 1.2F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.3F, -1.2F, 0.4F, 4.6F, 2.7F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(-2.75F, -1.4F, -2.9F, 0.3F, 0.55F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(2.45F, -1.4F, -2.9F, 0.3F, 0.55F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, -4.0F));

		PartDefinition antenna_left = head_0.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(0, 0).addBox(-0.16F, -0.2F, -2.2F, 0.32F, 0.32F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.32F, -0.35F, -2.6F, 0.64F, 0.5F, 0.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.15F, -2.4F, -2.8F, 0.0F, -0.3142F, -0.3491F));

		PartDefinition antenna_right = head_0.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(0, 0).addBox(-0.16F, -0.2F, -2.2F, 0.32F, 0.32F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.32F, -0.35F, -2.6F, 0.64F, 0.5F, 0.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.15F, -2.4F, -2.8F, 0.0F, 0.3142F, 0.3491F));

		PartDefinition horn = head_0.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(0, 0).addBox(-1.05F, -0.4F, -2.0F, 2.1F, 1.5F, 3.1F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.92F, -1.35F, -3.3F, 1.84F, 1.4F, 2.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.75F, -2.65F, -4.05F, 1.5F, 1.6F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.55F, -4.1F, -4.6F, 1.1F, 1.8F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.3F, -5.55F, -4.95F, 0.6F, 1.65F, 0.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, -2.8F));

		PartDefinition jaw_left = head_0.addOrReplaceChild("jaw_left", CubeListBuilder.create().texOffs(0, 0).addBox(-0.58F, -0.35F, -2.1F, 1.16F, 1.35F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(64, 64).addBox(0.0F, -0.2F, -3.0F, 1.0F, 0.85F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.7F, 0.0F, -3.1F, 0.35F, 0.6F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.15F, 0.75F, -2.7F, 0.0F, 0.2094F, 0.0F));

		PartDefinition jaw_right = head_0.addOrReplaceChild("jaw_right", CubeListBuilder.create().texOffs(0, 0).addBox(-0.58F, -0.35F, -2.1F, 1.16F, 1.35F, 2.4F, new CubeDeformation(0.0F))
		.texOffs(64, 64).addBox(-1.0F, -0.2F, -3.0F, 1.0F, 0.85F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.05F, 0.0F, -3.1F, 0.35F, 0.6F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.15F, 0.75F, -2.7F, 0.0F, -0.2094F, 0.0F));

		PartDefinition head_1 = all.addOrReplaceChild("head_1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.6F, -2.3F, -3.5F, 5.2F, 3.8F, 4.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.1F, -3.0F, -2.8F, 4.2F, 1.2F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.3F, -1.2F, 0.4F, 4.6F, 2.7F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(-2.75F, -1.4F, -2.9F, 0.3F, 0.55F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(2.45F, -1.4F, -2.9F, 0.3F, 0.55F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, -4.0F));

		PartDefinition antenna_left = head_1.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(0, 0).addBox(-0.16F, -0.2F, -2.2F, 0.32F, 0.32F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.32F, -0.35F, -2.6F, 0.64F, 0.5F, 0.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.15F, -2.4F, -2.8F, 0.0F, -0.3142F, -0.3491F));

		PartDefinition antenna_right = head_1.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(0, 0).addBox(-0.16F, -0.2F, -2.2F, 0.32F, 0.32F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.32F, -0.35F, -2.6F, 0.64F, 0.5F, 0.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.15F, -2.4F, -2.8F, 0.0F, 0.3142F, 0.3491F));

		PartDefinition horn = head_1.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(0, 0).addBox(-0.8F, -0.5F, -1.8F, 1.6F, 1.2F, 2.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.55F, -1.9F, -2.4F, 1.1F, 1.5F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.3F, -3.0F, -2.75F, 0.6F, 1.3F, 0.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, -2.8F));

		PartDefinition cannon = head_1.addOrReplaceChild("cannon", CubeListBuilder.create().texOffs(0, 0).addBox(-0.95F, -0.85F, -2.5F, 1.9F, 1.8F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.8F, -0.85F, -4.3F, 1.6F, 0.36F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.8F, 0.49F, -4.3F, 1.6F, 0.36F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.8F, -0.5F, -4.3F, 0.36F, 1.0F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.44F, -0.5F, -4.3F, 0.36F, 1.0F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(-0.44F, -0.49F, -2.58F, 0.88F, 0.98F, 0.08F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.65F, -2.6F));

		PartDefinition head_2 = all.addOrReplaceChild("head_2", CubeListBuilder.create().texOffs(0, 0).addBox(-2.6F, -2.3F, -3.5F, 5.2F, 3.8F, 4.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.1F, -3.0F, -2.8F, 4.2F, 1.2F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.3F, -1.2F, 0.4F, 4.6F, 2.7F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(-2.75F, -1.4F, -2.9F, 0.3F, 0.55F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(2.45F, -1.4F, -2.9F, 0.3F, 0.55F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, -4.0F));

		PartDefinition antenna_left = head_2.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(0, 0).addBox(-0.16F, -0.2F, -2.2F, 0.32F, 0.32F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.32F, -0.35F, -2.6F, 0.64F, 0.5F, 0.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.15F, -2.4F, -2.8F, 0.0F, -0.3142F, -0.3491F));

		PartDefinition antenna_right = head_2.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(0, 0).addBox(-0.16F, -0.2F, -2.2F, 0.32F, 0.32F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.32F, -0.35F, -2.6F, 0.64F, 0.5F, 0.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.15F, -2.4F, -2.8F, 0.0F, 0.3142F, 0.3491F));

		PartDefinition horn = head_2.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(0, 0).addBox(-0.8F, -0.5F, -1.8F, 1.6F, 1.2F, 2.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.55F, -1.9F, -2.4F, 1.1F, 1.5F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.3F, -3.0F, -2.75F, 0.6F, 1.3F, 0.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, -2.8F));

		PartDefinition cannon = head_2.addOrReplaceChild("cannon", CubeListBuilder.create().texOffs(0, 0).addBox(-2.3F, -0.85F, -2.5F, 1.9F, 1.8F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -0.85F, -4.3F, 1.6F, 0.36F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, 0.49F, -4.3F, 1.6F, 0.36F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.15F, -0.5F, -4.3F, 0.36F, 1.0F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.91F, -0.5F, -4.3F, 0.36F, 1.0F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(-1.79F, -0.49F, -2.58F, 0.88F, 0.98F, 0.08F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.4F, -0.85F, -2.5F, 1.9F, 1.8F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.55F, -0.85F, -4.3F, 1.6F, 0.36F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.55F, 0.49F, -4.3F, 1.6F, 0.36F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.55F, -0.5F, -4.3F, 0.36F, 1.0F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(1.79F, -0.5F, -4.3F, 0.36F, 1.0F, 1.9F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(0.91F, -0.49F, -2.58F, 0.88F, 0.98F, 0.08F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.65F, -2.6F));

		PartDefinition torso_0 = all.addOrReplaceChild("torso_0", CubeListBuilder.create().texOffs(0, 0).addBox(-3.35F, -1.9F, -3.6F, 6.7F, 3.9F, 8.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.65F, -1.4F, 4.6F, 5.3F, 3.0F, 1.65F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.5F, -2.0F, -3.8F, 7.0F, 3.4F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.9F, 1.7F, -1.8F, 5.8F, 0.42F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.8F, 1.7F, 0.0F, 5.6F, 0.42F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.7F, 1.7F, 1.8F, 5.4F, 0.42F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.2F, 1.1F));

		PartDefinition elytra_left = torso_0.addOrReplaceChild("elytra_left", CubeListBuilder.create().texOffs(0, 32).addBox(-2.75F, -0.55F, -2.4F, 2.75F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, 0.12F, -2.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.35F, -1.0F, -0.6F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.6F, 0.12F, -0.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.35F, -1.0F, 1.2F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.6F, 0.12F, 1.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.35F, -0.55F, 3.0F, 2.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.6F, 0.12F, 3.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F)), PartPose.offset(-0.4F, -2.4F, -0.9F));

		PartDefinition elytra_right = torso_0.addOrReplaceChild("elytra_right", CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, -0.55F, -2.4F, 2.75F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.2F, 0.12F, -2.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -1.0F, -0.6F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.8F, 0.12F, -0.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -1.0F, 1.2F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.8F, 0.12F, 1.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.55F, 3.0F, 2.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(1.8F, 0.12F, 3.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F)), PartPose.offset(0.4F, -2.4F, -0.9F));

		PartDefinition torso_1 = all.addOrReplaceChild("torso_1", CubeListBuilder.create().texOffs(0, 0).addBox(-3.35F, -1.9F, -3.6F, 6.7F, 3.9F, 8.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.65F, -1.4F, 4.6F, 5.3F, 3.0F, 1.65F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.5F, -2.0F, -3.8F, 7.0F, 3.4F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.9F, 1.7F, -1.8F, 5.8F, 0.42F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.8F, 1.7F, 0.0F, 5.6F, 0.42F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.7F, 1.7F, 1.8F, 5.4F, 0.42F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.2F, 1.1F));

		PartDefinition elytra_left = torso_1.addOrReplaceChild("elytra_left", CubeListBuilder.create().texOffs(0, 32).addBox(-2.75F, -0.55F, -2.4F, 2.75F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, 0.12F, -2.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.35F, -1.0F, -0.6F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.6F, 0.12F, -0.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.35F, -1.0F, 1.2F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.6F, 0.12F, 1.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.35F, -0.55F, 3.0F, 2.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.6F, 0.12F, 3.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F)), PartPose.offset(-0.4F, -2.4F, -0.9F));

		PartDefinition wing_left = torso_1.addOrReplaceChild("wing_left", CubeListBuilder.create(), PartPose.offset(-2.6F, -1.7F, -0.5F));

		PartDefinition wing_blade_0 = wing_left.addOrReplaceChild("wing_blade_0", CubeListBuilder.create().texOffs(0, 64).addBox(-5.7F, -0.09F, -0.2F, 5.7F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-5.7F, -0.13F, -0.12F, 5.7F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.3F, 0.0F, 0.2094F, 0.0F));

		PartDefinition wing_blade_1 = wing_left.addOrReplaceChild("wing_blade_1", CubeListBuilder.create().texOffs(0, 64).addBox(-6.5F, -0.09F, -0.2F, 6.5F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-6.5F, -0.13F, -0.12F, 6.5F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.15F, 0.0F, 0.2618F, 0.0F));

		PartDefinition wing_blade_2 = wing_left.addOrReplaceChild("wing_blade_2", CubeListBuilder.create().texOffs(0, 64).addBox(-7.3F, -0.09F, -0.2F, 7.3F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-7.3F, -0.13F, -0.12F, 7.3F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.0F, 0.3142F, 0.0F));

		PartDefinition wing_blade_3 = wing_left.addOrReplaceChild("wing_blade_3", CubeListBuilder.create().texOffs(0, 64).addBox(-6.5F, -0.09F, -0.2F, 6.5F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-6.5F, -0.13F, -0.12F, 6.5F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.15F, 0.0F, 0.3665F, 0.0F));

		PartDefinition wing_blade_4 = wing_left.addOrReplaceChild("wing_blade_4", CubeListBuilder.create().texOffs(0, 64).addBox(-5.7F, -0.09F, -0.2F, 5.7F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-5.7F, -0.13F, -0.12F, 5.7F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 3.3F, 0.0F, 0.4189F, 0.0F));

		PartDefinition elytra_right = torso_1.addOrReplaceChild("elytra_right", CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, -0.55F, -2.4F, 2.75F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.2F, 0.12F, -2.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -1.0F, -0.6F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.8F, 0.12F, -0.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -1.0F, 1.2F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.8F, 0.12F, 1.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.55F, 3.0F, 2.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(1.8F, 0.12F, 3.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F)), PartPose.offset(0.4F, -2.4F, -0.9F));

		PartDefinition wing_right = torso_1.addOrReplaceChild("wing_right", CubeListBuilder.create(), PartPose.offset(2.6F, -1.7F, -0.5F));

		PartDefinition wing_blade_0 = wing_right.addOrReplaceChild("wing_blade_0", CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, -0.09F, -0.2F, 5.7F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.13F, -0.12F, 5.7F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.3F, 0.0F, -0.2094F, 0.0F));

		PartDefinition wing_blade_1 = wing_right.addOrReplaceChild("wing_blade_1", CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, -0.09F, -0.2F, 6.5F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.13F, -0.12F, 6.5F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.15F, 0.0F, -0.2618F, 0.0F));

		PartDefinition wing_blade_2 = wing_right.addOrReplaceChild("wing_blade_2", CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, -0.09F, -0.2F, 7.3F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.13F, -0.12F, 7.3F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.0F, -0.3142F, 0.0F));

		PartDefinition wing_blade_3 = wing_right.addOrReplaceChild("wing_blade_3", CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, -0.09F, -0.2F, 6.5F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.13F, -0.12F, 6.5F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.15F, 0.0F, -0.3665F, 0.0F));

		PartDefinition wing_blade_4 = wing_right.addOrReplaceChild("wing_blade_4", CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, -0.09F, -0.2F, 5.7F, 0.18F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.13F, -0.12F, 5.7F, 0.08F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 3.3F, 0.0F, -0.4189F, 0.0F));

		PartDefinition torso_2 = all.addOrReplaceChild("torso_2", CubeListBuilder.create().texOffs(0, 0).addBox(-3.35F, -1.9F, -3.6F, 6.7F, 3.9F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.65F, -1.4F, 4.6F, 5.3F, 3.0F, 1.65F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.5F, -2.0F, -3.8F, 7.0F, 3.4F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.9F, 1.7F, -1.8F, 5.8F, 0.42F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.8F, 1.7F, 0.0F, 5.6F, 0.42F, 1.1F, new CubeDeformation(0.0F))
		.texOffs(64, 0).addBox(-2.7F, 1.7F, 1.8F, 5.4F, 0.42F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.2F, 1.1F));

		PartDefinition elytra_left = torso_2.addOrReplaceChild("elytra_left", CubeListBuilder.create().texOffs(0, 32).addBox(-2.75F, -0.55F, -2.4F, 2.75F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, 0.12F, -2.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.35F, -1.0F, -0.6F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.6F, 0.12F, -0.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-3.35F, -1.0F, 1.2F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.6F, 0.12F, 1.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.35F, -0.55F, 3.0F, 2.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.6F, 0.12F, 3.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F)), PartPose.offset(-0.4F, -2.4F, -0.9F));

		PartDefinition elytra_right = torso_2.addOrReplaceChild("elytra_right", CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, -0.55F, -2.4F, 2.75F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.2F, 0.12F, -2.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -1.0F, -0.6F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.8F, 0.12F, -0.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -1.0F, 1.2F, 3.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(2.8F, 0.12F, 1.4F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.0F, -0.55F, 3.0F, 2.35F, 1.1F, 2.35F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(1.8F, 0.12F, 3.2F, 0.8F, 0.5F, 2.05F, new CubeDeformation(0.0F)), PartPose.offset(0.4F, -2.4F, -0.9F));

		PartDefinition fin_left = torso_2.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(0, 64).addBox(-1.8F, -0.12F, 0.0F, 1.8F, 0.24F, 2.8F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(-3.15F, -0.09F, 1.2F, 1.8F, 0.18F, 2.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.0F, 3.5F, 0.0F, 0.4363F, 0.0F));

		PartDefinition fin_right = torso_2.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, -0.12F, 0.0F, 1.8F, 0.24F, 2.8F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(1.35F, -0.09F, 1.2F, 1.8F, 0.18F, 2.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.0F, 3.5F, 0.0F, -0.4363F, 0.0F));

		PartDefinition swim_tail = torso_2.addOrReplaceChild("swim_tail", CubeListBuilder.create().texOffs(0, 0).addBox(-0.55F, -0.4F, -0.2F, 1.1F, 0.8F, 2.2F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(-2.35F, -0.12F, 1.7F, 4.7F, 0.24F, 1.8F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.6F, 5.5F));

		PartDefinition limbs_0 = all.addOrReplaceChild("limbs_0", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_left_front = limbs_0.addOrReplaceChild("leg_left_front", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, -2.7F, 0.0F, 0.3142F, -0.2618F));

		PartDefinition tibia = leg_left_front.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.9F, 0.6F, -0.8F, 0.5F, 2.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_left_middle = limbs_0.addOrReplaceChild("leg_left_middle", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, 0.7F, 0.0F, 0.0F, -0.2618F));

		PartDefinition tibia = leg_left_middle.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_left_rear = limbs_0.addOrReplaceChild("leg_left_rear", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, 4.1F, 0.0F, -0.3142F, -0.2618F));

		PartDefinition tibia = leg_left_rear.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_right_front = limbs_0.addOrReplaceChild("leg_right_front", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, -2.7F, 0.0F, -0.3142F, 0.2618F));

		PartDefinition tibia = leg_right_front.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.4F, 0.6F, -0.8F, 0.5F, 2.3F, 1.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_right_middle = limbs_0.addOrReplaceChild("leg_right_middle", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, 0.7F, 0.0F, 0.0F, 0.2618F));

		PartDefinition tibia = leg_right_middle.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_right_rear = limbs_0.addOrReplaceChild("leg_right_rear", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, 4.1F, 0.0F, 0.3142F, 0.2618F));

		PartDefinition tibia = leg_right_rear.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition limbs_1 = all.addOrReplaceChild("limbs_1", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_left_front = limbs_1.addOrReplaceChild("leg_left_front", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, -2.7F, 0.0F, 0.3142F, -0.2618F));

		PartDefinition tibia = leg_left_front.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-1.3F, -0.2F, -2.35F, 2.6F, 0.5F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.05F, -0.1F, -2.85F, 0.5F, 0.35F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.25F, -0.1F, -2.85F, 0.5F, 0.35F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.55F, -0.1F, -2.85F, 0.5F, 0.35F, 0.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_left_middle = limbs_1.addOrReplaceChild("leg_left_middle", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, 0.7F, 0.0F, 0.0F, -0.2618F));

		PartDefinition tibia = leg_left_middle.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_left_rear = limbs_1.addOrReplaceChild("leg_left_rear", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, 4.1F, 0.0F, -0.3142F, -0.2618F));

		PartDefinition tibia = leg_left_rear.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_right_front = limbs_1.addOrReplaceChild("leg_right_front", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, -2.7F, 0.0F, -0.3142F, 0.2618F));

		PartDefinition tibia = leg_right_front.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-1.3F, -0.2F, -2.35F, 2.6F, 0.5F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.05F, -0.1F, -2.85F, 0.5F, 0.35F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.25F, -0.1F, -2.85F, 0.5F, 0.35F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.55F, -0.1F, -2.85F, 0.5F, 0.35F, 0.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_right_middle = limbs_1.addOrReplaceChild("leg_right_middle", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, 0.7F, 0.0F, 0.0F, 0.2618F));

		PartDefinition tibia = leg_right_middle.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_right_rear = limbs_1.addOrReplaceChild("leg_right_rear", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, 4.1F, 0.0F, 0.3142F, 0.2618F));

		PartDefinition tibia = leg_right_rear.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition limbs_2 = all.addOrReplaceChild("limbs_2", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_left_front = limbs_2.addOrReplaceChild("leg_left_front", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, -2.7F, 0.0F, 0.3142F, -0.2618F));

		PartDefinition tibia = leg_left_front.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_left_middle = limbs_2.addOrReplaceChild("leg_left_middle", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, 0.7F, 0.0F, 0.0F, -0.2618F));

		PartDefinition tibia = leg_left_middle.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_left_rear = limbs_2.addOrReplaceChild("leg_left_rear", CubeListBuilder.create().texOffs(64, 0).addBox(-2.9F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.65F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-2.7F, -1.35F, -1.2F, 2.3F, 0.6F, 2.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.1F, -4.7F, 4.1F, 0.0F, -0.3142F, -0.2618F));

		PartDefinition tibia = leg_left_rear.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 0.45F, 0.0F, 0.0F, 0.0F, 0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition leg_right_front = limbs_2.addOrReplaceChild("leg_right_front", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, -2.7F, 0.0F, -0.3142F, 0.2618F));

		PartDefinition tibia = leg_right_front.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_right_middle = limbs_2.addOrReplaceChild("leg_right_middle", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, 0.7F, 0.0F, 0.0F, 0.2618F));

		PartDefinition tibia = leg_right_middle.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition leg_right_rear = limbs_2.addOrReplaceChild("leg_right_rear", CubeListBuilder.create().texOffs(64, 0).addBox(-0.2F, -0.6F, -0.65F, 3.1F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.35F, -0.88F, -0.85F, 2.3F, 0.65F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(0.4F, -1.35F, -1.2F, 2.3F, 0.6F, 2.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1F, -4.7F, 4.1F, 0.0F, 0.3142F, 0.2618F));

		PartDefinition tibia = leg_right_rear.addOrReplaceChild("tibia", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 3.9F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.65F, -0.3F, -0.7F, 1.3F, 2.6F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 0.45F, 0.0F, 0.0F, 0.0F, -0.4363F));

		PartDefinition tarsus = tibia.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(64, 64).addBox(-0.6F, -0.25F, -0.95F, 1.2F, 0.65F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.16F, 0.06F, -1.55F, 0.34F, 0.35F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.4F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition additional_0 = all.addOrReplaceChild("additional_0", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition additional_1 = all.addOrReplaceChild("additional_1", CubeListBuilder.create(), PartPose.offset(0.0F, -8.7F, 1.1F));

		PartDefinition armor_-1_0 = additional_1.addOrReplaceChild("armor_-1_0", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -0.12F, -1.6F, 0.0F, 0.0F, -0.2094F));

		PartDefinition armor_-1_1 = additional_1.addOrReplaceChild("armor_-1_1", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -0.12F, 0.25F, 0.0F, 0.0F, -0.2094F));

		PartDefinition armor_-1_2 = additional_1.addOrReplaceChild("armor_-1_2", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -0.12F, 2.1F, 0.0F, 0.0F, -0.2094F));

		PartDefinition armor_-1_3 = additional_1.addOrReplaceChild("armor_-1_3", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -0.12F, 3.95F, 0.0F, 0.0F, -0.2094F));

		PartDefinition armor_1_0 = additional_1.addOrReplaceChild("armor_1_0", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, -0.12F, -1.6F, 0.0F, 0.0F, 0.2094F));

		PartDefinition armor_1_1 = additional_1.addOrReplaceChild("armor_1_1", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, -0.12F, 0.25F, 0.0F, 0.0F, 0.2094F));

		PartDefinition armor_1_2 = additional_1.addOrReplaceChild("armor_1_2", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, -0.12F, 2.1F, 0.0F, 0.0F, 0.2094F));

		PartDefinition armor_1_3 = additional_1.addOrReplaceChild("armor_1_3", CubeListBuilder.create().texOffs(0, 32).addBox(-1.55F, -0.35F, -0.6F, 3.1F, 0.65F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.3F, -0.15F, 1.5F, 2.6F, 0.36F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, -0.12F, 3.95F, 0.0F, 0.0F, 0.2094F));

		PartDefinition additional_2 = all.addOrReplaceChild("additional_2", CubeListBuilder.create(), PartPose.offset(0.0F, -8.4F, 0.0F));

		PartDefinition sensor_left = additional_2.addOrReplaceChild("sensor_left", CubeListBuilder.create().texOffs(0, 0).addBox(-0.18F, -3.4F, -0.18F, 0.36F, 3.5F, 0.36F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.75F, -3.65F, -0.36F, 1.5F, 0.4F, 0.72F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(-0.48F, -3.7F, -0.38F, 0.96F, 0.13F, 0.76F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.0F, 0.0F, 0.2094F, 0.0F, -0.2793F));

		PartDefinition sensor_right = additional_2.addOrReplaceChild("sensor_right", CubeListBuilder.create().texOffs(0, 0).addBox(-0.18F, -3.4F, -0.18F, 0.36F, 3.5F, 0.36F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-0.75F, -3.65F, -0.36F, 1.5F, 0.4F, 0.72F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(-0.48F, -3.7F, -0.38F, 0.96F, 0.13F, 0.76F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 0.0F, 0.0F, 0.2094F, 0.0F, 0.2793F));

		PartDefinition additional_3 = all.addOrReplaceChild("additional_3", CubeListBuilder.create().texOffs(0, 32).addBox(-1.6F, -0.3F, -1.25F, 3.2F, 1.7F, 2.5F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -9.3F, 3.2F));

		PartDefinition dorsal_cannon = additional_3.addOrReplaceChild("dorsal_cannon", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -0.8F, -4.1F, 1.8F, 0.35F, 4.1F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.9F, 0.45F, -4.1F, 1.8F, 0.35F, 4.1F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.9F, -0.46F, -4.1F, 0.35F, 0.92F, 4.1F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(0.55F, -0.46F, -4.1F, 0.35F, 0.92F, 4.1F, new CubeDeformation(0.0F))
		.texOffs(64, 32).addBox(-0.55F, -0.45F, -2.0F, 1.1F, 0.9F, 0.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.55F, -0.45F, -0.1396F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		all.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}