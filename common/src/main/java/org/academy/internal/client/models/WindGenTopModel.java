package org.academy.internal.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * @author 这里没有Badd
 */
public class WindGenTopModel extends HierarchicalModel<Entity> {
    private final ModelPart all;
    private final ModelPart main;
    private final ModelPart topfin1;
    private final ModelPart topfin2;
    private final ModelPart botfin1;
    private final ModelPart botfin2;
    private final ModelPart rfin1;
    private final ModelPart rfin2;
    private final ModelPart lfin1;
    private final ModelPart lfin2;
    private final ModelPart shell;
    private final ModelPart frontshell;
    private final ModelPart bottomshell;
    private final ModelPart bottomshellend;
    private final ModelPart topshell;
    private final ModelPart topshellend;
    private final ModelPart lshell;
    private final ModelPart lshellend;
    private final ModelPart rshell;
    private final ModelPart rshellend;
    private final ModelPart tail_li;
    private final ModelPart coil_li;

    public WindGenTopModel(ModelPart root) {
        this.all = root.getChild("all");
        this.main = this.all.getChild("main");
        this.topfin1 = this.main.getChild("topfin1");
        this.topfin2 = this.main.getChild("topfin2");
        this.botfin1 = this.main.getChild("botfin1");
        this.botfin2 = this.main.getChild("botfin2");
        this.rfin1 = this.main.getChild("rfin1");
        this.rfin2 = this.main.getChild("rfin2");
        this.lfin1 = this.main.getChild("lfin1");
        this.lfin2 = this.main.getChild("lfin2");
        this.shell = this.all.getChild("shell");
        this.frontshell = this.shell.getChild("frontshell");
        this.bottomshell = this.shell.getChild("bottomshell");
        this.bottomshellend = this.bottomshell.getChild("bottomshellend");
        this.topshell = this.shell.getChild("topshell");
        this.topshellend = this.topshell.getChild("topshellend");
        this.lshell = this.shell.getChild("lshell");
        this.lshellend = this.lshell.getChild("lshellend");
        this.rshell = this.shell.getChild("rshell");
        this.rshellend = this.rshell.getChild("rshellend");
        this.tail_li = this.all.getChild("tail_li");
        this.coil_li = this.all.getChild("coil_li");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 23.0F, 0.0F));

        PartDefinition main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 101).addBox(-14.0F, -13.0F, -7.0F, 22.0F, 13.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(0, 118).addBox(-10.0F, -15.0F, -4.0F, 15.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(1, 107).addBox(8.0F, -12.0F, -5.0F, 3.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(107, 1).addBox(11.0F, -9.0F, -2.0F, 6.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(108, 0).addBox(17.0F, -11.0F, -4.0F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(108, 0).addBox(17.0F, -13.0F, -4.0F, 2.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(108, 0).addBox(17.0F, -3.0F, -4.0F, 2.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(112, 4).addBox(17.0F, -11.0F, 4.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(112, 0).addBox(17.0F, -11.0F, -6.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(116, 2).addBox(19.0F, -11.0F, -2.0F, 2.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(118, 0).addBox(21.0F, -9.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(109, 4).addBox(19.0F, -9.0F, 2.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(114, 2).addBox(19.0F, -9.0F, -4.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 109).addBox(-37.0F, -10.0F, -4.0F, 23.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 118).addBox(-49.0F, -9.0F, -3.0F, 12.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -3.0F, 0.0F));

        PartDefinition topfin1 = main.addOrReplaceChild("topfin1", CubeListBuilder.create(), PartPose.offset(-23.3F, -10.6F, 0.0F));

        PartDefinition cube_r1 = topfin1.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(48, 87).addBox(-6.0F, -1.5F, -3.0F, 13.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 0.7F, 0.0F, 0.0F, 0.0F, -0.0873F));

        PartDefinition topfin2 = main.addOrReplaceChild("topfin2", CubeListBuilder.create(), PartPose.offset(-36.8F, -9.6F, 0.0F));

        PartDefinition cube_r2 = topfin2.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(66, 96).addBox(-18.0F, -1.5F, -2.0F, 12.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.5F, -0.5F, 0.0F, 0.0F, 0.0F, -0.0873F));

        PartDefinition botfin1 = main.addOrReplaceChild("botfin1", CubeListBuilder.create(), PartPose.offset(-23.2455F, -3.3583F, 0.0F));

        PartDefinition cube_r3 = botfin1.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(62, 104).addBox(-6.0F, -0.5F, -3.0F, 13.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0545F, -0.7417F, 0.0F, 0.0F, 0.0F, 0.0873F));

        PartDefinition botfin2 = main.addOrReplaceChild("botfin2", CubeListBuilder.create(), PartPose.offset(-36.9186F, -4.1732F, 0.0F));

        PartDefinition cube_r4 = botfin2.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(74, 115).addBox(-5.2F, -0.5F, -2.0F, 12.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -0.9F, 0.0F, 0.0F, 0.0F, 0.0873F));

        PartDefinition rfin1 = main.addOrReplaceChild("rfin1", CubeListBuilder.create(), PartPose.offset(-24.9F, -7.0F, 4.9F));

        PartDefinition cube_r5 = rfin1.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(60, 71).addBox(-6.0F, -2.0F, -0.5F, 12.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.8F, 0.0F, -0.9F, 0.0F, -0.0873F, 0.0F));

        PartDefinition rfin2 = main.addOrReplaceChild("rfin2", CubeListBuilder.create(), PartPose.offset(-36.8F, -7.0F, 4.0F));

        PartDefinition cube_r6 = rfin2.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(99, 46).addBox(-18.0F, -1.0F, -0.5F, 12.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.1F, 0.0F, 0.0F, 0.0F, -0.0873F, 0.0F));

        PartDefinition lfin1 = main.addOrReplaceChild("lfin1", CubeListBuilder.create(), PartPose.offset(-25.0F, -7.0F, -5.1F));

        PartDefinition cube_r7 = lfin1.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(61, 79).addBox(-6.0F, -2.0F, -1.5F, 12.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7F, 0.0F, 1.1F, 0.0F, 0.0873F, 0.0F));

        PartDefinition lfin2 = main.addOrReplaceChild("lfin2", CubeListBuilder.create(), PartPose.offset(-36.8F, -7.0F, -4.0F));

        PartDefinition cube_r8 = lfin2.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(99, 51).addBox(-18.0F, -1.0F, -1.5F, 12.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.1F, 0.0F, 0.0F, 0.0F, 0.0873F, 0.0F));

        PartDefinition shell = all.addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition frontshell = shell.addOrReplaceChild("frontshell", CubeListBuilder.create(), PartPose.offset(13.9438F, -10.0F, 0.0F));

        PartDefinition cube_r9 = frontshell.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(116, 6).addBox(-2.0F, -2.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9562F, 0.0F, 6.3F, 0.0F, 0.5672F, 0.0F));

        PartDefinition cube_r10 = frontshell.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(106, 3).addBox(-3.0F, -3.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.3438F, 0.0F, 7.7F, 0.0F, 0.2182F, 0.0F));

        PartDefinition cube_r11 = frontshell.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(96, 8).addBox(-3.0F, -3.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.3438F, 0.0F, -7.7F, 0.0F, -0.2182F, 0.0F));

        PartDefinition cube_r12 = frontshell.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(110, 5).addBox(-2.0F, -2.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9562F, 0.0F, -6.3F, 0.0F, -0.5672F, 0.0F));

        PartDefinition cube_r13 = frontshell.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(94, 0).addBox(-7.5F, -0.5F, -3.0F, 11.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5562F, 4.5F, 4.7F, 0.6451F, 0.4384F, -0.5121F));

        PartDefinition cube_r14 = frontshell.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(97, 0).addBox(-3.0F, -0.5F, -3.0F, 5.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5353F, 7.5568F, 0.0F, 0.0F, 0.0F, -0.2182F));

        PartDefinition cube_r15 = frontshell.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(112, 11).addBox(0.5F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4438F, 7.5F, 0.0F, 0.0F, 0.0F, -0.5672F));

        PartDefinition cube_r16 = frontshell.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(94, 0).addBox(-7.5F, -0.5F, -3.0F, 11.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5562F, 4.5F, -4.7F, -0.6886F, -0.4414F, -0.5122F));

        PartDefinition cube_r17 = frontshell.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(106, 0).addBox(-3.0F, -0.5F, -3.0F, 5.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5353F, -7.5568F, 0.0F, 0.0F, 0.0F, 0.2182F));

        PartDefinition cube_r18 = frontshell.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(96, 7).addBox(0.5F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4438F, -7.5F, 0.0F, 0.0F, 0.0F, 0.5672F));

        PartDefinition cube_r19 = frontshell.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(94, 0).addBox(-7.5F, -0.5F, -3.0F, 11.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5562F, -4.5F, -4.7F, 0.6638F, -0.4065F, 0.4666F));

        PartDefinition cube_r20 = frontshell.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(94, 0).addBox(-7.5F, -0.5F, -3.0F, 11.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5562F, -4.5F, 4.7F, -0.7068F, 0.4077F, 0.4682F));

        PartDefinition bottomshell = shell.addOrReplaceChild("bottomshell", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.15F, 12.6556F, 0.0F, 0.0F, 0.0F, 1.5708F));

        PartDefinition cube_r21 = bottomshell.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(118, 0).addBox(0.4289F, -3.953F, -2.0F, 1.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-13.3489F, -15.5098F, 0.0F, 0.0F, 0.0F, -0.3927F));

        PartDefinition cube_r22 = bottomshell.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(112, 0).addBox(-2.4916F, -10.9235F, -11.7945F, 1.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.186F, -11.9024F, 0.0F, -0.5951F, -0.4758F, -2.8329F));

        PartDefinition cube_r23 = bottomshell.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(111, 0).addBox(-2.4916F, -10.9235F, 6.7946F, 1.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.186F, -11.9024F, 0.0F, 0.5951F, 0.4758F, -2.8329F));

        PartDefinition cube_r24 = bottomshell.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(96, 0).addBox(0.0402F, -7.1526F, -3.352F, 1.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.5F, -9.5F, -5.0F, -0.5951F, 0.4758F, -0.3087F));

        PartDefinition cube_r25 = bottomshell.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(116, 2).addBox(0.0402F, -7.1526F, -1.648F, 1.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.5F, -9.5F, 5.0F, 0.5951F, -0.4758F, -0.3087F));

        PartDefinition bottomshellend = bottomshell.addOrReplaceChild("bottomshellend", CubeListBuilder.create(), PartPose.offset(-13.3489F, -0.8013F, 0.0F));

        PartDefinition cube_r26 = bottomshellend.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(118, 0).addBox(2.9731F, 10.8679F, -4.0F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(100, 3).addBox(3.8731F, 5.2679F, -2.0F, 1.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(118, 0).addBox(2.9731F, 10.8679F, 1.0F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, 0.0F, 0.0F, 0.3927F));

        PartDefinition topshell = shell.addOrReplaceChild("topshell", CubeListBuilder.create(), PartPose.offset(10.7327F, -17.3802F, 0.0F));

        PartDefinition cube_r27 = topshell.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(98, 0).addBox(-6.0F, -0.5F, -4.0F, 11.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.2827F, -2.2698F, 2.0F, 0.0F, 0.0F, 0.2443F));

        PartDefinition cube_r28 = topshell.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -0.5F, -3.5F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5827F, -1.1698F, -4.7F, 0.3453F, -0.079F, 0.157F));

        PartDefinition cube_r29 = topshell.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -0.5F, -3.5F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5827F, -1.1698F, 4.7F, -0.3453F, 0.079F, 0.157F));

        PartDefinition cube_r30 = topshell.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(96, 6).addBox(-3.0F, -0.5F, -4.0F, 5.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5827F, 0.6302F, 0.0F, 0.0F, 0.0F, 0.4363F));

        PartDefinition topshellend = topshell.addOrReplaceChild("topshellend", CubeListBuilder.create(), PartPose.offsetAndRotation(-10.1502F, -2.7252F, 0.0F, 0.0F, 0.0F, -0.0436F));

        PartDefinition cube_r31 = topshellend.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(80, 0).addBox(-16.0F, -0.5F, -4.0F, 19.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8262F, 0.6626F, -3.4F, 0.2618F, 0.0873F, -0.192F));

        PartDefinition cube_r32 = topshellend.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(80, 0).addBox(-16.0F, -0.5F, -1.0F, 19.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8262F, 0.6626F, 3.4F, -0.2618F, -0.0873F, -0.192F));

        PartDefinition cube_r33 = topshellend.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(92, 0).addBox(-9.0F, -0.5F, -5.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.2262F, 0.1626F, 2.0F, 0.0F, 0.0F, -0.192F));

        PartDefinition lshell = shell.addOrReplaceChild("lshell", CubeListBuilder.create(), PartPose.offsetAndRotation(12.3327F, -9.3802F, -5.0F, 1.5708F, 0.0F, 0.0F));

        PartDefinition cube_r34 = lshell.addOrReplaceChild("cube_r34", CubeListBuilder.create().texOffs(98, 0).addBox(-6.0F, -0.5F, -4.0F, 11.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.1136F, -3.5806F, 2.0F, 0.0F, 0.0F, 0.2443F));

        PartDefinition cube_r35 = lshell.addOrReplaceChild("cube_r35", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -0.5F, -3.5F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.4136F, -2.4806F, -4.7F, 0.3453F, -0.079F, 0.157F));

        PartDefinition cube_r36 = lshell.addOrReplaceChild("cube_r36", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -0.5F, -3.5F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.4136F, -2.4806F, 4.7F, -0.3453F, 0.079F, 0.157F));

        PartDefinition cube_r37 = lshell.addOrReplaceChild("cube_r37", CubeListBuilder.create().texOffs(96, 6).addBox(-3.0F, -0.5F, -4.0F, 5.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4136F, -0.6806F, 0.0F, 0.0F, 0.0F, 0.4363F));

        PartDefinition lshellend = lshell.addOrReplaceChild("lshellend", CubeListBuilder.create(), PartPose.offsetAndRotation(-11.981F, -4.036F, 0.0F, 0.0F, 0.0F, -0.0436F));

        PartDefinition cube_r38 = lshellend.addOrReplaceChild("cube_r38", CubeListBuilder.create().texOffs(80, 0).addBox(-16.0F, -0.5F, -4.0F, 19.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8262F, 0.6626F, -3.4F, 0.2618F, 0.0873F, -0.192F));

        PartDefinition cube_r39 = lshellend.addOrReplaceChild("cube_r39", CubeListBuilder.create().texOffs(80, 0).addBox(-16.0F, -0.5F, -1.0F, 19.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8262F, 0.6626F, 3.4F, -0.2618F, -0.0873F, -0.192F));

        PartDefinition cube_r40 = lshellend.addOrReplaceChild("cube_r40", CubeListBuilder.create().texOffs(92, 0).addBox(-9.0F, -0.5F, -5.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.2262F, 0.1626F, 2.0F, 0.0F, 0.0F, -0.192F));

        PartDefinition rshell = shell.addOrReplaceChild("rshell", CubeListBuilder.create(), PartPose.offsetAndRotation(11.7327F, -9.3802F, 6.0F, -1.5708F, 0.0F, 0.0F));

        PartDefinition cube_r41 = rshell.addOrReplaceChild("cube_r41", CubeListBuilder.create().texOffs(98, 0).addBox(-6.0F, -0.5F, -4.0F, 11.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.2827F, -4.2698F, 2.0F, 0.0F, 0.0F, 0.2443F));

        PartDefinition cube_r42 = rshell.addOrReplaceChild("cube_r42", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -0.5F, -3.5F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.5827F, -3.1698F, -4.7F, 0.3453F, -0.079F, 0.157F));

        PartDefinition cube_r43 = rshell.addOrReplaceChild("cube_r43", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -0.5F, -3.5F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.5827F, -3.1698F, 4.7F, -0.3453F, 0.079F, 0.157F));

        PartDefinition cube_r44 = rshell.addOrReplaceChild("cube_r44", CubeListBuilder.create().texOffs(96, 6).addBox(-3.0F, -0.5F, -4.0F, 5.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5827F, -1.3698F, 0.0F, 0.0F, 0.0F, 0.4363F));

        PartDefinition rshellend = rshell.addOrReplaceChild("rshellend", CubeListBuilder.create(), PartPose.offsetAndRotation(-11.1502F, -4.7252F, 0.0F, 0.0F, 0.0F, -0.0436F));

        PartDefinition cube_r45 = rshellend.addOrReplaceChild("cube_r45", CubeListBuilder.create().texOffs(80, 0).addBox(-16.0F, -0.5F, -4.0F, 19.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8262F, 0.6626F, -3.4F, 0.2618F, 0.0873F, -0.192F));

        PartDefinition cube_r46 = rshellend.addOrReplaceChild("cube_r46", CubeListBuilder.create().texOffs(80, 0).addBox(-16.0F, -0.5F, -1.0F, 19.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8262F, 0.6626F, 3.4F, -0.2618F, -0.0873F, -0.192F));

        PartDefinition cube_r47 = rshellend.addOrReplaceChild("cube_r47", CubeListBuilder.create().texOffs(92, 0).addBox(-9.0F, -0.5F, -5.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.2262F, 0.1626F, 2.0F, 0.0F, 0.0F, -0.192F));

        PartDefinition tail_li = all.addOrReplaceChild("tail_li", CubeListBuilder.create().texOffs(35, 75).addBox(-28.0F, -9.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-25.0F, -3.0F, 0.0F));

        PartDefinition coil_li = all.addOrReplaceChild("coil_li", CubeListBuilder.create().texOffs(0, 75).addBox(-5.5F, -5.0F, -5.0F, 11.0F, 10.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(-15.5F, -10.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        all.translateAndRotate(poseStack);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(WindGenBaseModel.TEXTURE));
        main.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        shell.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        coil_li.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        tail_li.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public @NotNull ModelPart root() {
        return all;
    }
}