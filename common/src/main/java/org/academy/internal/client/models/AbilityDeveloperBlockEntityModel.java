package org.academy.internal.client.models;// Made with Blockbench 4.12.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class AbilityDeveloperBlockEntityModel<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "ablility_converted"), "main");
	private final ModelPart up;
	private final ModelPart sidebars;
	private final ModelPart rsidebars;
	private final ModelPart sidebar10;
	private final ModelPart sidebar11;
	private final ModelPart sidebar12;
	private final ModelPart sidebar13;
	private final ModelPart sidebar14;
	private final ModelPart sidebar15;
	private final ModelPart sidebar16;
	private final ModelPart sidebar17;
	private final ModelPart sidebar18;
	private final ModelPart lsidebars;
	private final ModelPart sidebar1;
	private final ModelPart sidebar2;
	private final ModelPart sidebar3;
	private final ModelPart sidebar4;
	private final ModelPart sidebar5;
	private final ModelPart sidebar6;
	private final ModelPart sidebar7;
	private final ModelPart sidebar8;
	private final ModelPart sidebar9;
	private final ModelPart base;
	private final ModelPart middle;
	private final ModelPart bottom;

	public AbilityDeveloperBlockEntityModel(ModelPart root) {
		this.up = root.getChild("up");
		this.sidebars = this.up.getChild("sidebars");
		this.rsidebars = this.sidebars.getChild("rsidebars");
		this.sidebar10 = this.rsidebars.getChild("sidebar10");
		this.sidebar11 = this.rsidebars.getChild("sidebar11");
		this.sidebar12 = this.rsidebars.getChild("sidebar12");
		this.sidebar13 = this.rsidebars.getChild("sidebar13");
		this.sidebar14 = this.rsidebars.getChild("sidebar14");
		this.sidebar15 = this.rsidebars.getChild("sidebar15");
		this.sidebar16 = this.rsidebars.getChild("sidebar16");
		this.sidebar17 = this.rsidebars.getChild("sidebar17");
		this.sidebar18 = this.rsidebars.getChild("sidebar18");
		this.lsidebars = this.sidebars.getChild("lsidebars");
		this.sidebar1 = this.lsidebars.getChild("sidebar1");
		this.sidebar2 = this.lsidebars.getChild("sidebar2");
		this.sidebar3 = this.lsidebars.getChild("sidebar3");
		this.sidebar4 = this.lsidebars.getChild("sidebar4");
		this.sidebar5 = this.lsidebars.getChild("sidebar5");
		this.sidebar6 = this.lsidebars.getChild("sidebar6");
		this.sidebar7 = this.lsidebars.getChild("sidebar7");
		this.sidebar8 = this.lsidebars.getChild("sidebar8");
		this.sidebar9 = this.lsidebars.getChild("sidebar9");
		this.base = this.up.getChild("base");
		this.middle = root.getChild("middle");
		this.bottom = root.getChild("bottom");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition up = partdefinition.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.826F, -13.5346F, -1.0472F, 0.0F, 0.0F));

		PartDefinition sidebars = up.addOrReplaceChild("sidebars", CubeListBuilder.create(), PartPose.offset(0.0F, 9.174F, 0.5346F));

		PartDefinition rsidebars = sidebars.addOrReplaceChild("rsidebars", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition sidebar10 = rsidebars.addOrReplaceChild("sidebar10", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, -19.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, -19.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r1 = sidebar10.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, -17.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar11 = rsidebars.addOrReplaceChild("sidebar11", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, -15.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, -15.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r2 = sidebar11.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, -13.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar12 = rsidebars.addOrReplaceChild("sidebar12", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, -11.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, -11.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r3 = sidebar12.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, -9.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar13 = rsidebars.addOrReplaceChild("sidebar13", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, -7.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, -7.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r4 = sidebar13.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, -5.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar14 = rsidebars.addOrReplaceChild("sidebar14", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, -3.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, -3.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r5 = sidebar14.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, -1.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar15 = rsidebars.addOrReplaceChild("sidebar15", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, 1.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, 1.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r6 = sidebar15.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, 3.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar16 = rsidebars.addOrReplaceChild("sidebar16", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, 5.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, 5.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r7 = sidebar16.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, 7.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar17 = rsidebars.addOrReplaceChild("sidebar17", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, 9.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, 9.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r8 = sidebar17.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, 11.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition sidebar18 = rsidebars.addOrReplaceChild("sidebar18", CubeListBuilder.create().texOffs(11, 23).addBox(-9.0F, -13.0F, 13.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -16.0F, 13.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r9 = sidebar18.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.2F, -14.0F, 15.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition lsidebars = sidebars.addOrReplaceChild("lsidebars", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition sidebar1 = lsidebars.addOrReplaceChild("sidebar1", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, -19.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, -19.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r10 = sidebar1.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, -17.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar2 = lsidebars.addOrReplaceChild("sidebar2", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, -15.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, -15.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r11 = sidebar2.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, -13.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar3 = lsidebars.addOrReplaceChild("sidebar3", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, -11.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, -11.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r12 = sidebar3.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, -9.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar4 = lsidebars.addOrReplaceChild("sidebar4", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, -7.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, -7.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r13 = sidebar4.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, -5.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar5 = lsidebars.addOrReplaceChild("sidebar5", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, -3.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, -3.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r14 = sidebar5.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, -1.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar6 = lsidebars.addOrReplaceChild("sidebar6", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, 1.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, 1.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r15 = sidebar6.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, 3.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar7 = lsidebars.addOrReplaceChild("sidebar7", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, 5.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, 5.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r16 = sidebar7.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, 7.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar8 = lsidebars.addOrReplaceChild("sidebar8", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, 9.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, 9.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r17 = sidebar8.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, 11.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition sidebar9 = lsidebars.addOrReplaceChild("sidebar9", CubeListBuilder.create().texOffs(11, 12).addBox(8.0F, -13.0F, 13.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(0.0F, -16.0F, 13.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r18 = sidebar9.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.2F, -14.0F, 15.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition base = up.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 3.174F, -18.4654F, 16.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(67, 39).addBox(-4.0F, -5.826F, 16.5346F, 8.0F, 10.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(69, 25).addBox(-7.0F, -3.826F, 17.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(69, 25).addBox(4.0F, -3.826F, 17.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(69, 17).addBox(4.0F, -6.826F, 17.5346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(84, 17).addBox(-7.0F, -6.826F, 17.5346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(98, 1).addBox(-4.0F, -7.826F, 16.5346F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(86, 9).addBox(-8.0F, -6.826F, -22.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(69, 0).addBox(-4.0F, -7.826F, -23.4654F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(69, 9).addBox(4.0F, -6.826F, -22.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(103, 24).addBox(-8.0F, -3.826F, -20.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(67, 39).addBox(-4.0F, -5.826F, -20.4654F, 8.0F, 10.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(42, 39).addBox(-2.0F, 5.174F, -13.4654F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(103, 24).addBox(4.0F, -3.826F, -20.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(17, 39).addBox(-8.0F, -0.826F, -2.4654F, 2.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(126, 1).addBox(-5.0F, -2.826F, -4.4654F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(85, 57).addBox(7.2F, -1.826F, 19.5346F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(85, 57).addBox(7.2F, -1.826F, 14.5346F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(85, 57).addBox(-10.2F, -1.826F, 14.5346F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(85, 57).addBox(-10.2F, -1.826F, 19.5346F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(70, 57).addBox(7.2F, -2.826F, 15.5346F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(70, 57).addBox(-10.2F, -2.826F, 15.5346F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(65, 39).addBox(8.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
		.texOffs(65, 39).addBox(-10.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
		.texOffs(17, 39).addBox(6.0F, -0.826F, -2.4654F, 2.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(0, 39).addBox(6.0F, 0.174F, -18.4654F, 2.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(0, 39).addBox(-8.0F, 0.174F, -18.4654F, 2.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(104, 0).addBox(-6.0F, 2.174F, -18.4654F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(104, 0).addBox(5.0F, 2.174F, -18.4654F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r19 = base.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(130, 7).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3F, 0.174F, -2.4654F, 0.0F, 0.0F, 0.0873F));

		PartDefinition cube_r20 = base.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(103, 9).addBox(7.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(116, 9).addBox(-9.2F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1F, 0.174F, -19.0654F, 0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r21 = base.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.6499F, -17.8644F, 0.4189F, 0.0F, 0.0F));

		PartDefinition cube_r22 = base.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(116, 9).addBox(-8.0F, -5.5F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(103, 9).addBox(6.2F, -5.5F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1F, -0.326F, 18.2346F, -0.2618F, 0.0F, 0.0F));

		PartDefinition middle = partdefinition.addOrReplaceChild("middle", CubeListBuilder.create(), PartPose.offset(0.0F, 6.0F, -10.0F));

		PartDefinition cube_r23 = middle.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(117, 49).addBox(-1.0F, -13.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(117, 49).addBox(-1.0F, 4.75F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(117, 49).addBox(-1.0F, -4.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.25F, -17.1F, -0.1745F, 0.0F, 0.0F));

		PartDefinition bottom = partdefinition.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(55, 83).addBox(-9.0F, 17.0F, -24.0F, 18.0F, 1.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(41, 118).addBox(-11.0F, 17.0F, 8.0F, 22.0F, 1.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 115).addBox(8.1F, 15.0F, -24.0F, 1.0F, 2.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(44, 72).addBox(8.1F, 13.0F, -25.0F, 1.0F, 2.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(44, 72).addBox(-9.1F, 13.0F, -25.0F, 1.0F, 2.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(13, 80).addBox(-9.1F, 11.0F, -26.0F, 1.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(13, 80).addBox(8.1F, 11.0F, -26.0F, 1.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(0, 83).addBox(10.0F, 15.0F, 8.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 72).addBox(9.0F, 16.0F, 9.0F, 1.0F, 1.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(0, 72).addBox(-10.0F, 16.0F, 9.0F, 1.0F, 1.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(0, 71).addBox(-8.0F, 16.0F, -23.0F, 1.0F, 1.0F, 41.0F, new CubeDeformation(0.0F))
		.texOffs(0, 71).addBox(7.0F, 16.0F, -23.0F, 1.0F, 1.0F, 41.0F, new CubeDeformation(0.0F))
		.texOffs(0, 83).addBox(-11.0F, 15.0F, 8.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 72).addBox(8.0F, 15.0F, 8.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 72).addBox(-10.0F, 15.0F, 8.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 115).addBox(-9.1F, 15.0F, -24.0F, 1.0F, 2.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(12, 73).addBox(-2.0F, 15.0F, -21.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, -10.0F));

		PartDefinition cube_r24 = bottom.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(44, 104).addBox(-10.0F, -1.5F, -0.5F, 20.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 16.5F, 17.9F, -0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r25 = bottom.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(0, 97).addBox(-8.0F, -3.0F, -0.5F, 17.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 14.0F, -24.6F, 0.3927F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 256, 256);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		up.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		middle.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		bottom.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}