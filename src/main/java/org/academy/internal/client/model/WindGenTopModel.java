package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.Resource;

import static net.minecraft.client.renderer.RenderType.ENTITY_SOLID;

/**
 * @author 这里没有Badd
 */
public class WindGenTopModel extends Model {
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
        super(root, ENTITY_SOLID);
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
                .texOffs(0, 108).addBox(8.0F, -12.0F, -5.0F, 2.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(-1, 116).addBox(10.0F, -10.0F, -3.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(91, 2).addBox(17.0F, -11.0F, -4.0F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(82, 0).addBox(-25.0F, -12.0F, -6.0F, 11.0F, 10.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(84, 0).addBox(-37.0F, -11.0F, -5.0F, 12.0F, 8.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(90, 0).addBox(-48.0F, -10.0F, -4.0F, 11.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -3.0F, 0.0F));

        PartDefinition shell = all.addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition frontshell = shell.addOrReplaceChild("frontshell", CubeListBuilder.create(), PartPose.offset(7.5F, -9.0F, 0.0F));

        PartDefinition tl = frontshell.addOrReplaceChild("tl", CubeListBuilder.create().texOffs(62, 11).addBox(-25.0F, -4.0F, -8.0F, 25.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, -4.0F, 0.0F));

        PartDefinition tr = frontshell.addOrReplaceChild("tr", CubeListBuilder.create().texOffs(62, 11).addBox(-26.0F, -4.0F, 0.0F, 25.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(9.5F, -4.0F, 0.0F));

        PartDefinition br = frontshell.addOrReplaceChild("br", CubeListBuilder.create().texOffs(66, 11).addBox(-23.0F, -4.0F, 0.0F, 23.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, 4.0F, 0.0F));

        PartDefinition bl = frontshell.addOrReplaceChild("bl", CubeListBuilder.create().texOffs(66, 11).addBox(-23.0F, -4.0F, -8.0F, 23.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, 4.0F, 0.0F));

        PartDefinition li = all.addOrReplaceChild("li", CubeListBuilder.create().texOffs(35, 75).addBox(48.0F, -9.0F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(33, 73).addBox(-27.0F, -9.0F, -3.0F, 8.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-25.0F, -3.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        all.translateAndRotate(poseStack);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(Resource.Textures.MODEL_WIND_GEN));
        main.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        shell.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        li.render(poseStack, vertexConsumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}