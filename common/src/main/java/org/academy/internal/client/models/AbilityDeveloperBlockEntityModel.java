package org.academy.internal.client.models;

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
import org.joml.Matrix4f;

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
    private final ModelPart middle;
    private final ModelPart bottom;
    private final ModelPart desert;
    private final ModelPart sidebar2;
    private final ModelPart sidebar3;
    private final ModelPart sidebar4;
    private final ModelPart sidebar5;
    private final ModelPart sidebar6;
    private final ModelPart sidebar7;
    private final ModelPart sidebar8;
    private final ModelPart sidebar9;
    private final ModelPart sidebar11;
    private final ModelPart sidebar12;
    private final ModelPart sidebar13;
    private final ModelPart sidebar14;
    private final ModelPart sidebar15;
    private final ModelPart sidebar16;
    private final ModelPart sidebar17;
    private final ModelPart sidebar18;

    public AbilityDeveloperBlockEntityModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.all = root.getChild("all");
        this.up = this.all.getChild("up");
        this.glasscrest = this.up.getChild("glasscrest");
        this.rside = this.glasscrest.getChild("rside");
        this.lside = this.glasscrest.getChild("lside");
        this.lsidebars = this.glasscrest.getChild("lsidebars");
        this.base = this.up.getChild("base");
        this.middle = this.all.getChild("middle");
        this.bottom = this.all.getChild("bottom");
        this.desert = root.getChild("desert");
        this.sidebar2 = this.desert.getChild("sidebar2");
        this.sidebar3 = this.desert.getChild("sidebar3");
        this.sidebar4 = this.desert.getChild("sidebar4");
        this.sidebar5 = this.desert.getChild("sidebar5");
        this.sidebar6 = this.desert.getChild("sidebar6");
        this.sidebar7 = this.desert.getChild("sidebar7");
        this.sidebar8 = this.desert.getChild("sidebar8");
        this.sidebar9 = this.desert.getChild("sidebar9");
        this.sidebar11 = this.desert.getChild("sidebar11");
        this.sidebar12 = this.desert.getChild("sidebar12");
        this.sidebar13 = this.desert.getChild("sidebar13");
        this.sidebar14 = this.desert.getChild("sidebar14");
        this.sidebar15 = this.desert.getChild("sidebar15");
        this.sidebar16 = this.desert.getChild("sidebar16");
        this.sidebar17 = this.desert.getChild("sidebar17");
        this.sidebar18 = this.desert.getChild("sidebar18");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition up = all.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -22.174F, -13.5346F, -1.0472F, 0.0F, 0.0F));

        PartDefinition glasscrest = up.addOrReplaceChild("glasscrest", CubeListBuilder.create(), PartPose.offset(0.0F, 9.174F, 0.5346F));

        // Keep only animated parts here for clarity if needed, but full definition is fine
        PartDefinition rside = glasscrest.addOrReplaceChild("rside", CubeListBuilder.create().texOffs(0, 125).addBox(-8.8284F, -13.1716F, -19.0F, 1.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(5, 101).addBox(-6.0F, -16.0F, -19.0F, 6.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r1 = rside.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 119).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, -17.0F, 0.0F, 0.0F, 0.7854F));

        PartDefinition lside = glasscrest.addOrReplaceChild("lside", CubeListBuilder.create().texOffs(23, 103).addBox(7.8284F, -13.1716F, -19.0F, 1.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(48, 79).addBox(0.0F, -16.0F, -19.0F, 6.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r2 = lside.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(23, 111).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, -17.0F, 0.0F, 0.0F, -0.7854F));

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

        // ... other static parts definitions ...
        PartDefinition cube_r3 = base.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(174, 25).addBox(-4.0F, -2.0F, 0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(174, 19).addBox(-9.0F, -2.0F, 0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, 0.174F, 14.0346F, -0.3491F, 0.0F, 0.0F));
        // ... cube_r4 to cube_r14 ...
        PartDefinition cube_r4 = base.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(145, 24).addBox(-5.0F, -3.0F, -0.5F, 10.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.174F, 16.0346F, -0.3491F, 0.0F, 0.0F));
        PartDefinition cube_r5 = base.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(146, 3).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -8.326F, 19.4346F, 1.0584F, -0.4558F, -0.664F));
        PartDefinition cube_r6 = base.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(146, 3).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.326F, 19.4346F, 1.0584F, 0.4558F, 0.664F));
        PartDefinition cube_r7 = base.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(70, 61).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(85, 61).addBox(-1.5F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(85, 61).addBox(-1.5F, -2.0F, -3.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.7F, 0.174F, 17.5346F, -0.0141F, 0.2539F, 0.0022F));
        PartDefinition cube_r8 = base.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(70, 61).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(85, 61).addBox(-1.5F, -2.0F, -3.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(85, 61).addBox(-1.5F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.7F, 0.174F, 17.5346F, -0.0141F, -0.2539F, -0.0022F));
        PartDefinition cube_r9 = base.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(133, 7).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3F, 0.174F, -2.4654F, 0.0F, 0.0F, 0.0873F));
        PartDefinition cube_r10 = base.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(103, 9).addBox(-1.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.1F, 0.174F, -19.0654F, 0.386F, -0.151F, 0.0879F));
        PartDefinition cube_r11 = base.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.6499F, -17.8644F, 0.4189F, 0.0F, 0.0F));
        PartDefinition cube_r12 = base.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(118, 9).addBox(-1.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.1F, 0.174F, -19.0654F, 0.386F, 0.151F, -0.0879F));
        PartDefinition cube_r13 = base.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(118, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.6F, -0.809F, 18.364F, -0.2769F, 0.2261F, -0.1332F));
        PartDefinition cube_r14 = base.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(103, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.6F, -0.809F, 18.364F, -0.2769F, -0.2261F, 0.1332F));

        PartDefinition middle = all.addOrReplaceChild("middle", CubeListBuilder.create(), PartPose.offset(0.0F, -19.0F, -10.0F));

        PartDefinition cube_r15 = middle.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, -13.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(116, 128).addBox(-1.0F, 4.75F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(116, 128).addBox(-1.0F, -4.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.25F, -17.1F, -0.1745F, 0.0F, 0.0F));

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

        PartDefinition cube_r16 = bottom.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(71, 210).addBox(-10.0F, -1.5F, -0.5F, 20.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 16.5F, 17.9F, -0.3927F, 0.0F, 0.0F));
        PartDefinition cube_r17 = bottom.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(9, 199).addBox(-10.0F, -3.0F, -1.5F, 22.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 13.963F, -29.3139F, 0.3927F, 0.0F, 0.0F));


        PartDefinition desert = partdefinition.addOrReplaceChild("desert", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition sidebar2 = desert.addOrReplaceChild("sidebar2", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, -15.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(134, 133).addBox(0.0F, -16.0F, -15.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r18 = sidebar2.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, -13.0F, 0.0F, 0.0F, -0.7854F));
        // ... sidebar3 to sidebar18 definitions ...
        PartDefinition sidebar3 = desert.addOrReplaceChild("sidebar3", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, -11.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, -11.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r19 = sidebar3.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, -9.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar4 = desert.addOrReplaceChild("sidebar4", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, -7.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, -7.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r20 = sidebar4.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, -5.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar5 = desert.addOrReplaceChild("sidebar5", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, -3.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, -3.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r21 = sidebar5.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, -1.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar6 = desert.addOrReplaceChild("sidebar6", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, 1.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, 1.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r22 = sidebar6.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, 3.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar7 = desert.addOrReplaceChild("sidebar7", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, 5.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, 5.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r23 = sidebar7.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, 7.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar8 = desert.addOrReplaceChild("sidebar8", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, 9.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, 9.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r24 = sidebar8.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, 11.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar9 = desert.addOrReplaceChild("sidebar9", CubeListBuilder.create().texOffs(144, 128).addBox(7.8284F, -13.1716F, 13.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(0.0F, -16.0F, 13.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r25 = sidebar9.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0607F, -14.2322F, 15.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition sidebar11 = desert.addOrReplaceChild("sidebar11", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, -15.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, -15.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r26 = sidebar11.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, -13.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar12 = desert.addOrReplaceChild("sidebar12", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, -11.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, -11.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r27 = sidebar12.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, -9.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar13 = desert.addOrReplaceChild("sidebar13", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, -7.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, -7.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r28 = sidebar13.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, -5.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar14 = desert.addOrReplaceChild("sidebar14", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, -3.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, -3.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r29 = sidebar14.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, -1.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar15 = desert.addOrReplaceChild("sidebar15", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, 1.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, 1.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r30 = sidebar15.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, 3.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar16 = desert.addOrReplaceChild("sidebar16", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, 5.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, 5.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r31 = sidebar16.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, 7.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar17 = desert.addOrReplaceChild("sidebar17", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, 9.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, 9.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r32 = sidebar17.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, 11.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition sidebar18 = desert.addOrReplaceChild("sidebar18", CubeListBuilder.create().texOffs(144, 128).addBox(-8.8284F, -13.1716F, 13.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(134, 133).addBox(-6.0F, -16.0F, 13.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, -13.0F));
        PartDefinition cube_r33 = sidebar18.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(144, 130).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0607F, -14.2322F, 15.0F, 0.0F, 0.0F, 0.7854F));


        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    public static final AnimationDefinition open = AnimationDefinition.Builder.withLength(1.75F)
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.posVec(-3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0417F, KeyframeAnimations.posVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.posVec(-6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(-6.0F, -8.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.posVec(-6.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 25.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 35.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.posVec(3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0417F, KeyframeAnimations.posVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.posVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(6.0F, -8.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.75F, KeyframeAnimations.posVec(6.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition close = AnimationDefinition.Builder.withLength(1.75F)
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -35.0F), AnimationChannel.Interpolations.LINEAR), // Start at open's end rot (1.5s)
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.25s
                    new Keyframe(1.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR) // End at open's start rot (0s)
            ))
            .addAnimation("rside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(-6.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Start at open's end pos (1.75s)
                    new Keyframe(0.25F, KeyframeAnimations.posVec(-6.0F, -8.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.5s
                    new Keyframe(0.5F, KeyframeAnimations.posVec(-6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.25s
                    new Keyframe(0.7083F, KeyframeAnimations.posVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.0417s
                    new Keyframe(0.9583F, KeyframeAnimations.posVec(-3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 0.7917s
                    new Keyframe(1.25F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 0.5s
                    new Keyframe(1.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR) // End at open's start pos (0s)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 35.0F), AnimationChannel.Interpolations.LINEAR), // Start at open's end rot (1.5s)
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 25.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.25s
                    new Keyframe(1.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR) // End at open's start rot (0s)
            ))
            .addAnimation("lside", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(6.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Start at open's end pos (1.75s)
                    new Keyframe(0.25F, KeyframeAnimations.posVec(6.0F, -8.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.5s
                    new Keyframe(0.5F, KeyframeAnimations.posVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.25s
                    new Keyframe(0.7083F, KeyframeAnimations.posVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 1.0417s
                    new Keyframe(0.9583F, KeyframeAnimations.posVec(3.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 0.7917s
                    new Keyframe(1.25F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR), // Corresponds to open's 0.5s
                    new Keyframe(1.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR) // End at open's start pos (0s)
            ))
            .build();


    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        all.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        desert.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public @NotNull ModelPart root() {
        return all;
    }

    public void setupAnim(AbilityDeveloperBlockEntity abilityDeveloperBlockEntity, float partialTick) {
        this.rside.resetPose();
        this.lside.resetPose();

        boolean animatingOpen = abilityDeveloperBlockEntity.animationState.isStarted();
        boolean animatingClose = abilityDeveloperBlockEntity.closingAnimationState.isStarted();

        if (animatingOpen) {
            animate(abilityDeveloperBlockEntity.animationState, open, abilityDeveloperBlockEntity.ticks + partialTick);
        } else if (animatingClose) {
            animate(abilityDeveloperBlockEntity.closingAnimationState, close, abilityDeveloperBlockEntity.ticks + partialTick);
        }
    }

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}