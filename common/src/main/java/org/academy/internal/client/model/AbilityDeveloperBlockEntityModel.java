package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * @author 这里没有Badd
 */
public class AbilityDeveloperBlockEntityModel extends HierarchicalModel<Entity> {
    private final ModelPart all;
    private final ModelPart up;
    private final ModelPart glasscrest;
    private final ModelPart rside;
    private final ModelPart lside;
    private final ModelPart lsidebars;
    private final ModelPart base;
    private final ModelPart lwheel;
    private final ModelPart rwheel;
    private final ModelPart middle;
    private final ModelPart m1;
    private final ModelPart m2;
    private final ModelPart m3;
    private final ModelPart bottom;

    public AbilityDeveloperBlockEntityModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.all = root.getChild("all");
        this.up = this.all.getChild("up");
        this.glasscrest = this.up.getChild("glasscrest");
        this.rside = this.glasscrest.getChild("rside");
        this.lside = this.glasscrest.getChild("lside");
        this.lsidebars = this.glasscrest.getChild("lsidebars");
        this.base = this.up.getChild("base");
        this.lwheel = this.base.getChild("lwheel");
        this.rwheel = this.base.getChild("rwheel");
        this.middle = this.all.getChild("middle");
        this.m1 = this.middle.getChild("m1");
        this.m2 = this.middle.getChild("m2");
        this.m3 = this.middle.getChild("m3");
        this.bottom = this.all.getChild("bottom");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 16.0F));

        PartDefinition up = all.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -22.174F, -13.5346F, -1.0472F, 0.0F, 0.0F));

        PartDefinition glasscrest = up.addOrReplaceChild("glasscrest", CubeListBuilder.create(), PartPose.offset(0.0F, 9.174F, 0.5346F));

        PartDefinition rside = glasscrest.addOrReplaceChild("rside", CubeListBuilder.create().texOffs(0, 125).addBox(2.3013F, 0.1297F, -18.0F, 1.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(5, 101).addBox(5.1297F, -2.6987F, -18.0F, 6.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.1297F, -13.3013F, -1.0F));

        PartDefinition cube_r1 = rside.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 119).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.069F, -0.931F, -16.0F, 0.0F, 0.0F, 0.7854F));

        PartDefinition lside = glasscrest.addOrReplaceChild("lside", CubeListBuilder.create().texOffs(23, 103).addBox(-3.3013F, 0.1297F, -18.0F, 1.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(48, 79).addBox(-11.1297F, -2.6987F, -18.0F, 6.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(11.1297F, -13.3013F, -1.0F));

        PartDefinition cube_r2 = lside.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(23, 111).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.069F, -0.931F, -16.0F, 0.0F, 0.0F, -0.7854F));

        PartDefinition lsidebars = glasscrest.addOrReplaceChild("lsidebars", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition base = up.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 3.174F, -18.4654F, 16.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(154, 6).addBox(-4.0F, 2.174F, -13.4654F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(69, 39).addBox(-4.0F, -7.826F, 16.5346F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(-7.0F, -3.826F, 17.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(4.0F, -3.826F, 17.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 17).addBox(4.0F, -6.826F, 17.5346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(84, 17).addBox(-7.0F, -6.826F, 17.5346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(86, 9).addBox(-8.0F, -6.826F, -22.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(69, 0).addBox(-4.0F, -7.826F, -23.4654F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 9).addBox(4.0F, -6.826F, -22.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(103, 24).addBox(-8.0F, -3.826F, -20.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(110, 42).addBox(-4.0F, -5.826F, -21.4654F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(106, 65).addBox(-2.0F, -3.826F, -17.4654F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(42, 39).addBox(-2.0F, 5.174F, -13.4654F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(103, 24).addBox(4.0F, -3.826F, -20.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(17, 39).addBox(-8.0F, -0.826F, -2.4654F, 2.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
                .texOffs(126, 1).addBox(-5.0F, -2.826F, -4.4654F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(65, 39).addBox(8.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(0, 64).addBox(10.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 33.0F, new CubeDeformation(0.0F))
                .texOffs(105, 66).addBox(-10.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(119, 40).addBox(6.0F, -0.826F, -2.4654F, 2.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
                .texOffs(145, 39).addBox(6.0F, 0.174F, -18.4654F, 2.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(0, 39).addBox(-8.0F, 0.174F, -18.4654F, 2.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(105, 0).addBox(-6.0F, 2.174F, -18.4654F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(105, 0).addBox(5.0F, 2.174F, -18.4654F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r3 = base.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(174, 25).addBox(-4.0F, -2.0F, 0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(174, 19).addBox(-9.0F, -2.0F, 0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, 0.174F, 14.0346F, -0.3491F, 0.0F, 0.0F));

        PartDefinition cube_r4 = base.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(145, 24).addBox(-5.0F, -3.0F, -0.5F, 10.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.174F, 16.0346F, -0.3491F, 0.0F, 0.0F));

        PartDefinition cube_r5 = base.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(146, 3).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -8.326F, 19.4346F, 1.0584F, -0.4558F, -0.664F));

        PartDefinition cube_r6 = base.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(146, 3).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.326F, 19.4346F, 1.0584F, 0.4558F, 0.664F));

        PartDefinition cube_r7 = base.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(133, 7).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3F, 0.174F, -2.4654F, 0.0F, 0.0F, 0.0873F));

        PartDefinition cube_r8 = base.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(103, 9).addBox(-1.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.1F, 0.174F, -19.0654F, 0.386F, -0.151F, 0.0879F));

        PartDefinition cube_r9 = base.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.6499F, -17.8644F, 0.4189F, 0.0F, 0.0F));

        PartDefinition cube_r10 = base.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(118, 9).addBox(-1.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.1F, 0.174F, -19.0654F, 0.386F, 0.151F, -0.0879F));

        PartDefinition cube_r11 = base.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(118, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.6F, -0.809F, 18.364F, -0.2769F, 0.2261F, -0.1332F));

        PartDefinition cube_r12 = base.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(103, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.6F, -0.809F, 18.364F, -0.2769F, -0.2261F, 0.1332F));

        PartDefinition lwheel = base.addOrReplaceChild("lwheel", CubeListBuilder.create(), PartPose.offset(8.7F, 0.174F, 17.5346F));

        PartDefinition cube_r13 = lwheel.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(85, 61).addBox(-1.5F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(70, 61).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(85, 61).addBox(-1.5F, -2.0F, -3.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0141F, -0.2539F, -0.0022F));

        PartDefinition rwheel = base.addOrReplaceChild("rwheel", CubeListBuilder.create(), PartPose.offset(-8.7F, 0.174F, 17.5346F));

        PartDefinition cube_r14 = rwheel.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(85, 61).addBox(-1.5F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(70, 61).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(85, 61).addBox(-1.5F, -2.0F, -3.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0141F, 0.2539F, 0.0022F));

        PartDefinition middle = all.addOrReplaceChild("middle", CubeListBuilder.create(), PartPose.offset(0.0F, -19.0F, -10.0F));

        PartDefinition m1 = middle.addOrReplaceChild("m1", CubeListBuilder.create(), PartPose.offset(0.0F, -5.3671F, -15.5806F));

        PartDefinition cube_r15 = m1.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, -13.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.6171F, -1.5194F, -0.1745F, 0.0F, 0.0F));

        PartDefinition m2 = middle.addOrReplaceChild("m2", CubeListBuilder.create(), PartPose.offset(0.0F, 3.4962F, -17.1434F));

        PartDefinition cube_r16 = m2.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, -4.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.2462F, 0.0434F, -0.1745F, 0.0F, 0.0F));

        PartDefinition m3 = middle.addOrReplaceChild("m3", CubeListBuilder.create(), PartPose.offset(0.0F, 12.3595F, -18.7063F));

        PartDefinition cube_r17 = m3.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, 4.75F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -9.1095F, 1.6063F, -0.1745F, 0.0F, 0.0F));

        PartDefinition bottom = all.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 218).addBox(-11.0F, 17.0F, -28.0F, 22.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(17, 178).addBox(-11.0F, 17.0F, 8.0F, 22.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(83, 208).addBox(10.0F, 15.0F, -29.0F, 1.0F, 2.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(60, 170).addBox(10.0F, 13.0F, -30.0F, 1.0F, 2.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(60, 170).addBox(-11.0F, 13.0F, -30.0F, 1.0F, 2.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(0, 180).addBox(-11.0F, 11.0F, -30.0F, 1.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(0, 180).addBox(10.0F, 11.0F, -30.0F, 1.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(2, 232).addBox(10.0F, 15.0F, 8.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(9.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(-10.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(-8.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(7.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(2, 232).addBox(-11.0F, 15.0F, 8.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(83, 208).addBox(-11.0F, 15.0F, -29.0F, 1.0F, 2.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(3, 219).addBox(-2.0F, 15.0F, -21.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -19.0F, -10.0F));

        PartDefinition cube_r18 = bottom.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(71, 210).addBox(-10.0F, -1.5F, -0.5F, 20.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 16.5772F, 17.8879F, -0.3927F, 0.0F, 0.0F));

        PartDefinition cube_r19 = bottom.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(9, 199).addBox(-10.0F, -3.0F, -1.5F, 22.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 13.963F, -29.3139F, 0.3927F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    public static final AnimationDefinition open = AnimationDefinition.Builder.withLength(2.25F)
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -70.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9167F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.posVec(-3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.4583F, KeyframeAnimations.posVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.posVec(-6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(-6.0F, -8.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(-2.0F, -12.0F, -2.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 35.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 70.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9167F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.posVec(3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.4583F, KeyframeAnimations.posVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.posVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(6.0F, -8.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(2.0F, -12.0F, -2.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition stand = AnimationDefinition.Builder.withLength(2.25F)
            .addAnimation("up", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(60.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(55.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(55.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("up", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -14.0F, -3.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -12.71F, -2.94F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7083F, KeyframeAnimations.posVec(0.0F, -12.71F, -2.94F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m3", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -5.4F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9167F, KeyframeAnimations.posVec(0.0F, -3.2F, -0.3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0417F, KeyframeAnimations.posVec(0.0F, -2.9F, -0.37F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.posVec(0.0F, -2.5F, -0.52F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2917F, KeyframeAnimations.posVec(0.0F, -2.3F, -0.58F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.8333F, KeyframeAnimations.posVec(0.0F, -1.02F, -0.36F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m3", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.2F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -23.0F, -3.2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.1667F, KeyframeAnimations.posVec(0.0F, -22.08F, -2.93F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7083F, KeyframeAnimations.posVec(0.0F, -22.07F, -3.2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m1", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.1F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -14.2F, -1.6F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4583F, KeyframeAnimations.posVec(0.0F, -13.74F, -1.66F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5417F, KeyframeAnimations.posVec(0.0F, -13.51F, -1.67F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.625F, KeyframeAnimations.posVec(0.0F, -13.48F, -1.69F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.6667F, KeyframeAnimations.posVec(0.0F, -13.47F, -1.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7083F, KeyframeAnimations.posVec(0.0F, -13.35F, -1.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9167F, KeyframeAnimations.posVec(0.0F, -11.81F, -1.77F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.posVec(0.0F, -9.23F, -1.48F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2917F, KeyframeAnimations.posVec(0.0F, -8.49F, -1.46F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m2", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 0.2F, 1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("middle", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition liedown = AnimationDefinition.Builder.withLength(1.0F).looping()
            .addAnimation("up", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(60.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("up", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -14.0F, -3.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-77.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m3", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -5.4F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-77.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m1", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -23.0F, -3.2F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-77.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("m2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -14.2F, -1.6F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition close = AnimationDefinition.Builder.withLength(2.25F)
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -70.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(-2.0F, -12.0F, -2.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(-6.0F, -8.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(-6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.posVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0417F, KeyframeAnimations.posVec(-3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.3333F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.posVec(-6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9167F, KeyframeAnimations.posVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.posVec(-3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.4583F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 70.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 35.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(2.0F, -12.0F, -2.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(6.0F, -8.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.posVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0417F, KeyframeAnimations.posVec(3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.3333F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5833F, KeyframeAnimations.posVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9167F, KeyframeAnimations.posVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2083F, KeyframeAnimations.posVec(3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.4583F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        all.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public @NotNull ModelPart root() {
        return all;
    }

    public void setupAnim(AbilityDeveloperBlockEntity abilityDeveloperBlockEntity, float partialTick) {
        lside.resetPose();
        rside.resetPose();
        up.resetPose();
        m1.resetPose();
        m2.resetPose();
        m3.resetPose();
        middle.resetPose();

        animate(abilityDeveloperBlockEntity.openState, open, abilityDeveloperBlockEntity.ticks + partialTick);
        animate(abilityDeveloperBlockEntity.standState, stand, abilityDeveloperBlockEntity.ticks + partialTick);
        animate(abilityDeveloperBlockEntity.closingState, close, abilityDeveloperBlockEntity.ticks + partialTick);
        animate(abilityDeveloperBlockEntity.liedownState, liedown, abilityDeveloperBlockEntity.ticks + partialTick);
    }

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}