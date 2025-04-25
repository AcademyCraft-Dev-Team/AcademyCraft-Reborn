package org.academy.internal.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * @author 这里没有Badd
 */
public class WindGenTopModel extends HierarchicalModel<Entity> {
    private final ModelPart all;
    private final ModelPart main;
    private final ModelPart shell;
    private final ModelPart frontshell;
    private final ModelPart tl;
    private final ModelPart tr;
    private final ModelPart br;
    private final ModelPart bl;
    private final ModelPart li;

    public WindGenTopModel(ModelPart root) {
        this.all = root.getChild("all");
        this.main = this.all.getChild("main");
        this.shell = this.all.getChild("shell");
        this.frontshell = this.shell.getChild("frontshell");
        this.tl = this.frontshell.getChild("tl");
        this.tr = this.frontshell.getChild("tr");
        this.br = this.frontshell.getChild("br");
        this.bl = this.frontshell.getChild("bl");
        this.li = this.all.getChild("li");
    }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 23.0F, 0.0F));

        PartDefinition main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(56, 0).addBox(-14.0F, -13.0F, -7.0F, 22.0F, 13.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(82, 0).addBox(-10.0F, -15.0F, -4.0F, 15.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(5, 113).addBox(-10.0F, 0.0F, -6.0F, 12.0F, 1.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(0, 108).addBox(8.0F, -12.0F, -5.0F, 3.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(0, 116).addBox(11.0F, -10.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(91, 2).addBox(17.0F, -11.0F, -4.0F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(86, 0).addBox(-25.0F, -11.0F, -5.0F, 11.0F, 8.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(88, 0).addBox(-37.0F, -10.0F, -4.0F, 12.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(94, 0).addBox(-48.0F, -9.0F, -3.0F, 11.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -3.0F, 0.0F));

        PartDefinition cube_r1 = main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(89, 4).addBox(6.0F, -0.5F, -3.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(89, 4).addBox(-6.0F, -0.5F, -3.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-30.3F, -3.9F, 0.0F, 0.0F, 0.0F, 0.0873F));

        PartDefinition cube_r2 = main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(92, 6).addBox(-5.0F, -0.5F, -2.0F, 11.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-42.9186F, -5.0732F, 0.0F, 0.0F, 0.0F, 0.0873F));

        PartDefinition cube_r3 = main.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(89, 4).addBox(6.0F, -0.5F, -3.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(91, 6).addBox(-18.0F, -0.5F, -2.0F, 12.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(89, 4).addBox(-6.0F, -0.5F, -3.0F, 12.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-30.3F, -10.1F, 0.0F, 0.0F, 0.0F, -0.0873F));

        PartDefinition cube_r4 = main.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(94, 9).addBox(-18.0F, -1.0F, -0.5F, 12.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(94, 9).addBox(6.0F, -2.0F, -0.5F, 12.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(94, 9).addBox(-6.0F, -2.0F, -0.5F, 12.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-30.7F, -7.0F, 4.0F, 0.0F, -0.0873F, 0.0F));

        PartDefinition cube_r5 = main.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(94, 9).addBox(-18.0F, -1.0F, -0.5F, 12.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(94, 9).addBox(6.0F, -2.0F, -0.5F, 12.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(94, 9).addBox(-6.0F, -2.0F, -0.5F, 12.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-30.7F, -7.0F, -4.0F, 0.0F, 0.0873F, 0.0F));

        PartDefinition shell = all.addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition frontshell = shell.addOrReplaceChild("frontshell", CubeListBuilder.create(), PartPose.offset(7.5F, -9.0F, 0.0F));

        PartDefinition tl = frontshell.addOrReplaceChild("tl", CubeListBuilder.create().texOffs(62, 11).addBox(-25.0F, -4.0F, -8.0F, 25.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, -4.0F, 0.0F));

        PartDefinition tr = frontshell.addOrReplaceChild("tr", CubeListBuilder.create().texOffs(62, 11).addBox(-26.0F, -4.0F, 0.0F, 25.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(9.5F, -4.0F, 0.0F));

        PartDefinition br = frontshell.addOrReplaceChild("br", CubeListBuilder.create().texOffs(66, 11).addBox(-23.0F, -4.0F, 0.0F, 23.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, 4.0F, 0.0F));

        PartDefinition bl = frontshell.addOrReplaceChild("bl", CubeListBuilder.create().texOffs(66, 11).addBox(-23.0F, -4.0F, -8.0F, 23.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, 4.0F, 0.0F));

        PartDefinition li = all.addOrReplaceChild("li", CubeListBuilder.create().texOffs(35, 75).addBox(48.0F, -9.0F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(35, 75).addBox(-27.0F, -9.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-25.0F, -3.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public void setupAnim(@NotNull WindGenTopBlockEntity entity, float partialTick) {
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
        li.render(poseStack, vertexConsumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public @NotNull ModelPart root() {
        return all;
    }
}