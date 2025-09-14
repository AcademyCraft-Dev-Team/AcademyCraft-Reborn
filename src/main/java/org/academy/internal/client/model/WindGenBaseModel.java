package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.animation.definitions.WindGenBaseAnimation;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;

import static org.academy.api.client.Resource.Textures.BLOCK_WIND_GEN_PILLAR;
import static org.academy.api.client.Resource.Textures.MODEL_WIND_GEN;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class WindGenBaseModel extends Model {
    public static final float[][] PILLAR_VERTEX_BUFFER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.3f, 8, true);
    public static final float[][] PILLAR_OUTLINE_VERTEX_BUFFER = VertexUtil.Cylinder.getCylinderWireframeBuffer(0, 1, 0.3f, 8);
    private final RenderType BASE_RENDER_TYPE = renderType(MODEL_WIND_GEN);
    private final RenderType PILLAR_RENDER_TYPE = renderType(BLOCK_WIND_GEN_PILLAR);
    private final ModelPart all;
    private final ModelPart parts;
    private final ModelPart screen;
    private final ModelPart rhalf;
    private final ModelPart lhalf;
    private final ModelPart rods;
    private final ModelPart base;
    private final KeyframeAnimation setup;
    private final KeyframeAnimation shut;

    public WindGenBaseModel(ModelPart root) {
        super(root, RenderType::entityCutoutNoCull);
        this.all = root.getChild("all");
        this.parts = this.all.getChild("parts");
        this.screen = this.parts.getChild("screen");
        this.rhalf = this.screen.getChild("rhalf");
        this.lhalf = this.screen.getChild("lhalf");
        this.rods = this.parts.getChild("rods");
        this.base = this.parts.getChild("base");
        this.setup = WindGenBaseAnimation.SETUP.bake(root);
        this.shut = WindGenBaseAnimation.SHUT.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition parts = all.addOrReplaceChild("parts", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition screen = parts.addOrReplaceChild("screen", CubeListBuilder.create(), PartPose.offset(0.0F, -19.0F, 10.0F));

        PartDefinition cube_r1 = screen.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 10).addBox(1.0F, -13.0F, 0.8F, 8.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, 7.0F, 1.2F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rhalf = screen.addOrReplaceChild("rhalf", CubeListBuilder.create(), PartPose.offset(-6.875F, -0.082F, 0.3826F));

        PartDefinition cube_r2 = rhalf.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(6, 24).addBox(3.0F, -13.0F, 0.8F, 5.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.125F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition lhalf = screen.addOrReplaceChild("lhalf", CubeListBuilder.create(), PartPose.offset(6.875F, -0.082F, 0.3826F));

        PartDefinition cube_r3 = lhalf.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(19, 10).addBox(4.0F, -13.0F, 0.8F, 5.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.875F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rods = parts.addOrReplaceChild("rods", CubeListBuilder.create(), PartPose.offset(0.0F, -16.6F, 7.0F));

        PartDefinition cube_r4 = rods.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 1).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.8727F, 0.0F, 0.0F));

        PartDefinition cube_r5 = rods.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(9, 1).addBox(-1.0F, -4.6F, 0.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 2.7F, 0.5F, -0.6109F, 0.0F, 0.0F));

        PartDefinition base = parts.addOrReplaceChild("base", CubeListBuilder.create().texOffs(11, 25).addBox(-7.5F, -16.0F, -7.5F, 15.0F, 3.0F, 15.0F, new CubeDeformation(0.0F))
                .texOffs(0, 45).addBox(-8.0F, -3.0F, -8.0F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(14, 0).addBox(-2.0F, -18.0F, 6.0F, 4.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r6 = base.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(28, 0).addBox(-3.0F, -3.4175F, 0.125F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.0F, 13.45F, -1.5708F, 0.0F, 0.0F));

        PartDefinition cube_r7 = base.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(15, 1).addBox(-2.0F, -3.4175F, 0.125F, 4.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.6F, 9.0F, -2.0944F, 0.0F, 0.0F));

        PartDefinition cube_r8 = base.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r9 = base.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r10 = base.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, -0.1745F));

        PartDefinition cube_r11 = base.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, -0.1745F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public void setupAnim(WindGenBaseBlockEntity windGenBaseBlockEntity, float partialTick) {
        resetPose();

        setup.apply(windGenBaseBlockEntity.setupState, windGenBaseBlockEntity.ticks + partialTick);
        shut.apply(windGenBaseBlockEntity.shutdownState, windGenBaseBlockEntity.ticks + partialTick);
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        all.render(poseStack, bufferSource.getBuffer(BASE_RENDER_TYPE), packedLight, packedOverlay);
        poseStack.pushPose();
        poseStack.translate(0, -0.5f, 0);
        poseStack.scale(1, -31f / 16f, 1);
        renderPole(poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * 1x1 size
     */
    public void renderPole(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0, -1, 0);
        poseStack.rotateAround(Axis.YN.rotationDegrees(22.5f), 0, 0, 0);
        CylinderRenderer.renderCylinder(poseStack, bufferSource.getBuffer(PILLAR_RENDER_TYPE), PILLAR_VERTEX_BUFFER, 1, 1, 1, 1, packedLight, packedOverlay);
        poseStack.popPose();
    }
}