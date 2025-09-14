package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.Resource;

import static net.minecraft.client.renderer.RenderType.ENTITY_SOLID;

/**
 * @author 这里没有Badd
 */
public class WindGenTopModel extends Model {
    private final ModelPart all;

    public WindGenTopModel(ModelPart root) {
        super(root, ENTITY_SOLID);
        this.all = root.getChild("all");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create().texOffs(0, 40).addBox(-7.0F, -17.0F, -8.0F, 14.0F, 14.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(5, 80).addBox(-6.0F, -15.0F, 9.0F, 12.0F, 10.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(102, 66).addBox(-3.0F, -13.0F, -11.0F, 6.0F, 6.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(100, 47).addBox(-4.0F, -14.0F, -14.0F, 8.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(100, 16).addBox(-8.0F, -18.0F, -9.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(100, 16).addBox(5.0F, -18.0F, -9.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(87, 3).addBox(5.0F, -18.0F, -6.0F, 3.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(87, 3).addBox(5.0F, -5.0F, -6.0F, 3.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(87, 3).addBox(-8.0F, -18.0F, -6.0F, 3.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(87, 3).addBox(-8.0F, -5.0F, -6.0F, 3.0F, 3.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 26.0F, -4.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(Resource.Textures.MODEL_WIND_GEN_TOP));
        all.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}