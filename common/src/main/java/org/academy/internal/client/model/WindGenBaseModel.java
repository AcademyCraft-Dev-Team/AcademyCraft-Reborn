package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

@SuppressWarnings({"unused", "FieldCanBeLocal", "SpellCheckingInspection"})
public class WindGenBaseModel extends HierarchicalModel<Entity> {
    public static final float[][] PILLAR_VERTEX_BUFFER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 8, true);
    private final ModelPart all;
    private final ModelPart pole;
    private final ModelPart parts;
    private final ModelPart screen;
    private final ModelPart rhalf;
    private final ModelPart lhalf;
    private final ModelPart rods;

    public WindGenBaseModel(ModelPart root) {
        this.all = root.getChild("all");
        this.pole = this.all.getChild("pole");
        this.parts = this.all.getChild("parts");
        this.screen = this.parts.getChild("screen");
        this.rhalf = this.screen.getChild("rhalf");
        this.lhalf = this.screen.getChild("lhalf");
        this.rods = this.parts.getChild("rods");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition pole = all.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(4, 56).addBox(-7.0F, -16.0F, -7.0F, 14.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(6.0F, -14.0F, -3.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(2.0F, -14.0F, -7.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(-3.0F, -14.0F, -7.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(-7.0F, -14.0F, 2.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(-7.0F, -14.0F, -3.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(6.0F, -14.0F, 2.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(2.0F, -14.0F, 6.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(-3.0F, -14.0F, 6.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 109).addBox(-8.0F, -3.0F, -8.0F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(22, 7).addBox(-2.0F, -18.0F, 6.0F, 4.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r1 = pole.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(32, 48).addBox(-3.0F, -3.4175F, 0.125F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.0F, 13.45F, -1.5708F, 0.0F, 0.0F));

        PartDefinition cube_r2 = pole.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(23, 8).addBox(-2.0F, -3.4175F, 0.125F, 4.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.6F, 9.0F, -2.0944F, 0.0F, 0.0F));

        PartDefinition cube_r3 = pole.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r4 = pole.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r5 = pole.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, -0.1745F));

        PartDefinition cube_r6 = pole.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, -0.1745F));

        PartDefinition parts = all.addOrReplaceChild("parts", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition screen = parts.addOrReplaceChild("screen", CubeListBuilder.create(), PartPose.offset(0.0F, -19.0F, 10.0F));

        PartDefinition cube_r7 = screen.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 18).addBox(1.0F, -13.0F, 0.8F, 8.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, 7.0F, 1.2F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rhalf = screen.addOrReplaceChild("rhalf", CubeListBuilder.create(), PartPose.offset(-6.875F, -0.082F, 0.3826F));

        PartDefinition cube_r8 = rhalf.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(6, 32).addBox(3.0F, -13.0F, 0.8F, 5.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.125F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition lhalf = screen.addOrReplaceChild("lhalf", CubeListBuilder.create(), PartPose.offset(6.875F, -0.082F, 0.3826F));

        PartDefinition cube_r9 = lhalf.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(19, 18).addBox(4.0F, -13.0F, 0.8F, 5.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.875F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rods = parts.addOrReplaceChild("rods", CubeListBuilder.create(), PartPose.offset(0.0F, -16.6F, 7.0F));

        PartDefinition cube_r10 = rods.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(8, 8).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.8727F, 0.0F, 0.0F));

        PartDefinition cube_r11 = rods.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(17, 8).addBox(-1.0F, -4.6F, 0.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 2.7F, 0.5F, -0.6109F, 0.0F, 0.0F));

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
            .build();

    public static final AnimationDefinition shut = AnimationDefinition.Builder.withLength(1.0F)
            .addAnimation("rods", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-27.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rods", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -1.0F, -0.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -1.0F, -0.7F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -1.0F, -1.4F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("screen", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("screen", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -3.0F, -1.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("rhalf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("lhalf", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(-4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(-4.0F, -0.3007F, -0.9537F), AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    public void setupAnim(WindGenBaseBlockEntity windGenBaseBlockEntity, float partialTick) {
        pole.resetPose();
        parts.resetPose();
        screen.resetPose();
        rhalf.resetPose();
        lhalf.resetPose();
        rods.resetPose();

        animate(windGenBaseBlockEntity.setupState, WindGenBaseModel.setup, windGenBaseBlockEntity.ticks + partialTick);
        animate(windGenBaseBlockEntity.shutdownState, WindGenBaseModel.shut, windGenBaseBlockEntity.ticks + partialTick);
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        all.render(poseStack, bufferSource.getBuffer(renderType(TextureResources.TEXTURE_WIND_GEN_MODEL)), packedLight, packedOverlay);
        poseStack.pushPose();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel bakedModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(Blocks.WIND_GEN_PILLAR.defaultBlockState());
        RandomSource randomSource = RandomSource.create();
        randomSource.setSeed(42L);
        poseStack.rotateAround(Axis.YP.rotationDegrees(-90), 0, 0, 0);
        Matrix4f matrix4f = new Matrix4f();
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