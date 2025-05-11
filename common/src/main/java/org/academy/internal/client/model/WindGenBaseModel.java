package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.academy.AcademyCraft;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * @author 这里没有Badd
 */
@SuppressWarnings({"unused", "FieldCanBeLocal", "SpellCheckingInspection"})
public class WindGenBaseModel extends HierarchicalModel<Entity> {
    public static final float[][] PILLAR_VERTEX_BUFFER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 8, true);
    private final ModelPart all;
    private final ModelPart pole;
    private final ModelPart parts;
    private final ModelPart screen;
    private final ModelPart rhalf;
    private final ModelPart rhalf_li;
    private final ModelPart bsr;
    private final ModelPart lhalf;
    private final ModelPart lhalf_li;
    private final ModelPart bsl;
    private final ModelPart blackscreen;
    private final ModelPart midhalf_li;
    private final ModelPart rods;

    public WindGenBaseModel(ModelPart root) {
        this.all = root.getChild("all");
        this.pole = this.all.getChild("pole");
        this.parts = this.all.getChild("parts");
        this.screen = this.parts.getChild("screen");
        this.rhalf = this.screen.getChild("rhalf");
        this.rhalf_li = this.rhalf.getChild("rhalf_li");
        this.bsr = this.rhalf.getChild("bsr");
        this.lhalf = this.screen.getChild("lhalf");
        this.lhalf_li = this.lhalf.getChild("lhalf_li");
        this.bsl = this.lhalf.getChild("bsl");
        this.blackscreen = this.screen.getChild("blackscreen");
        this.midhalf_li = this.screen.getChild("midhalf_li");
        this.rods = this.parts.getChild("rods");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition pole = all.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(4, 56).addBox(-7.0F, -16.0F, -7.0F, 14.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(6.0F, -14.0F, -3.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(2.0F, -14.0F, -7.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(-3.0F, -14.0F, -7.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(-7.0F, -14.0F, 2.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(-7.0F, -14.0F, -3.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(6.0F, -14.0F, 2.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(2.0F, -14.0F, 6.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(2, 30).addBox(-3.0F, -14.0F, 6.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r1 = pole.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r2 = pole.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r3 = pole.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, -0.1745F));

        PartDefinition cube_r4 = pole.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, -0.1745F));

        PartDefinition cube_r5 = pole.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(22, 34).addBox(-6.5F, -1.5F, -6.0F, 7.0F, 3.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.7426F, -1.5F, 5.3142F, 0.0F, -0.3927F, 0.0F));

        PartDefinition cube_r6 = pole.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(20, 34).addBox(-0.5F, -1.5F, -6.0F, 7.0F, 3.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.7426F, -1.5F, 5.3897F, 0.0F, 0.3927F, 0.0F));

        PartDefinition cube_r7 = pole.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(22, 34).addBox(-6.5F, -1.5F, -4.0F, 7.0F, 3.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.7426F, -1.5F, -5.3897F, 0.0F, 0.3927F, 0.0F));

        PartDefinition cube_r8 = pole.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(20, 34).addBox(-0.5F, -1.5F, -4.0F, 7.0F, 3.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.7426F, -1.5F, -5.3142F, 0.0F, -0.3927F, 0.0F));

        PartDefinition cube_r9 = pole.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(20, 36).addBox(-4.0F, -1.5F, -0.5F, 10.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3897F, -1.5F, -10.7426F, 0.0F, 0.3927F, 0.0F));

        PartDefinition cube_r10 = pole.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(20, 36).addBox(-6.0F, -1.5F, -0.5F, 10.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3142F, -1.5F, -10.7426F, 0.0F, -0.3927F, 0.0F));

        PartDefinition cube_r11 = pole.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(22, 37).addBox(-6.0F, -1.5F, -6.5F, 10.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3897F, -1.5F, 10.7426F, 0.0F, 0.3927F, 0.0F));

        PartDefinition cube_r12 = pole.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(22, 37).addBox(-4.0F, -1.5F, -6.5F, 10.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3142F, -1.5F, 10.7426F, 0.0F, -0.3927F, 0.0F));

        PartDefinition parts = all.addOrReplaceChild("parts", CubeListBuilder.create().texOffs(22, 7).addBox(-2.0F, -18.0F, 6.0F, 4.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition screen = parts.addOrReplaceChild("screen", CubeListBuilder.create(), PartPose.offset(0.0F, -18.985F, 10.3921F));

        PartDefinition cube_r13 = screen.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, 0.9F, 8.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-4.0F, -13.0F, 0.9F, 8.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 18).addBox(-4.0F, -12.0F, 0.7F, 8.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.085F, 0.8079F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rhalf = screen.addOrReplaceChild("rhalf", CubeListBuilder.create(), PartPose.offset(-6.875F, 0.003F, -0.0095F));

        PartDefinition cube_r14 = rhalf.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(0, 3).addBox(-9.0F, -12.0F, 0.9F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(5, 3).addBox(-9.0F, -2.0F, 0.9F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(30, 18).addBox(-8.0F, -12.0F, 0.7F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(5, 3).addBox(-9.0F, -13.0F, 0.9F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.875F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rhalf_li = rhalf.addOrReplaceChild("rhalf_li", CubeListBuilder.create(), PartPose.offset(-5.125F, 7.082F, 0.8174F));

        PartDefinition li_r1 = rhalf_li.addOrReplaceChild("li_r1", CubeListBuilder.create().texOffs(30, 18).addBox(4.0F, -12.0F, 0.71F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, 0.0F, 0.0F));

        PartDefinition bsr = rhalf.addOrReplaceChild("bsr", CubeListBuilder.create(), PartPose.offset(0.875F, 0.015F, -0.0477F));

        PartDefinition cube_r15 = bsr.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(43, 14).addBox(-2.0F, -12.0F, 0.72F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.0669F, 0.8651F, 0.3054F, 0.0F, 0.0F));

        PartDefinition lhalf = screen.addOrReplaceChild("lhalf", CubeListBuilder.create(), PartPose.offset(6.875F, 0.003F, -0.0095F));

        PartDefinition cube_r16 = lhalf.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(5, 3).addBox(-1.0F, -2.0F, 0.9F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 3).addBox(3.0F, -12.0F, 0.9F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(19, 18).addBox(-1.0F, -12.0F, 0.7F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(5, 3).addBox(-1.0F, -13.0F, 0.9F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.875F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition lhalf_li = lhalf.addOrReplaceChild("lhalf_li", CubeListBuilder.create(), PartPose.offset(-6.875F, 7.082F, 0.8174F));

        PartDefinition li_r2 = lhalf_li.addOrReplaceChild("li_r2", CubeListBuilder.create().texOffs(19, 18).addBox(4.0F, -12.0F, 0.71F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, 0.0F, 0.0F));

        PartDefinition bsl = lhalf.addOrReplaceChild("bsl", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r17 = bsl.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(42, 14).addBox(10.0F, -12.0F, 0.72F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.875F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition blackscreen = screen.addOrReplaceChild("blackscreen", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r18 = blackscreen.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(35, 1).addBox(2.0F, -12.0F, 0.72F, 8.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, 7.085F, 0.8079F, 0.3054F, 0.0F, 0.0F));

        PartDefinition midhalf_li = screen.addOrReplaceChild("midhalf_li", CubeListBuilder.create(), PartPose.offset(-6.0F, 7.085F, 0.8079F));

        PartDefinition li_r3 = midhalf_li.addOrReplaceChild("li_r3", CubeListBuilder.create().texOffs(0, 18).addBox(2.0F, -12.0F, 0.71F, 8.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rods = parts.addOrReplaceChild("rods", CubeListBuilder.create(), PartPose.offset(0.0F, -16.6F, 7.0F));

        PartDefinition cube_r19 = rods.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(8, 8).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.8727F, 0.0F, 0.0F));

        PartDefinition cube_r20 = rods.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(17, 8).addBox(-1.0F, -4.6F, 0.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 2.7F, 0.5F, -0.6109F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public static final AnimationDefinition setup = AnimationDefinition.Builder.withLength(1.2917F)
            .addAnimation("rods", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rods", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -1.0F, -1.4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, -0.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, -1.0F, -0.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -1.0F, -0.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("screen", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("screen", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, -0.3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.8F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lhalf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(-4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(-4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rhalf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("blackscreen", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2917F, KeyframeAnimations.posVec(0.0F, -0.1F, -0.1F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("blackscreen", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.02F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.02F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.scaleVec(1.02F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("bsr", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2917F, KeyframeAnimations.posVec(0.0F, -0.1F, -0.1F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("bsr", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("bsl", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2917F, KeyframeAnimations.posVec(0.0F, -0.1F, -0.1F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition shut = AnimationDefinition.Builder.withLength(0.25F).looping()
            .addAnimation("screen", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("screen", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -3.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -3.0F, -1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rhalf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lhalf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(-4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(-4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rods", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rods", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -1.0F, -1.4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, -1.4F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("blackscreen", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.02F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    public void setupAnim(WindGenBaseBlockEntity windGenBaseBlockEntity, float partialTick) {
        rods.resetPose();
        screen.resetPose();
        lhalf.resetPose();
        rhalf.resetPose();
        blackscreen.resetPose();
        bsl.resetPose();
        bsr.resetPose();

        animate(windGenBaseBlockEntity.activeState, setup, windGenBaseBlockEntity.ticks + partialTick);
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        all.render(poseStack, bufferSource.getBuffer(renderType(TextureResources.TEXTURE_WIND_GEN_MODEL)), packedLight, packedOverlay);
        poseStack.pushPose();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR_BLOCK.defaultBlockState());
        RandomSource randomSource = RandomSource.create();
        randomSource.setSeed(42L);
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.rotateY((float) Math.toRadians(-90));
        matrix4f.translate(-0.5f, 0.505f, -0.5f);
        matrix4f.scale(1,0.9f,1);
        poseStack.mulPoseMatrix(matrix4f);
        RenderUtil.BakedModelRenderer.render(poseStack, bakedModel, bufferSource, randomSource, false, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public @NotNull ModelPart root() {
        return all;
    }
}