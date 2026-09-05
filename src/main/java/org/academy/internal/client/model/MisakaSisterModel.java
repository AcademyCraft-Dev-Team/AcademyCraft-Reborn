package org.academy.internal.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.academy.internal.client.renderer.entity.state.MisakaSisterRenderState;

public final class MisakaSisterModel extends EntityModel<MisakaSisterRenderState> {
    private final ModelPart root;

    public MisakaSisterModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var part = mesh.getRoot();
        part.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MisakaSisterRenderState state) {
        root.getChild("head").yRot = state.yRot * ((float) Math.PI / 180F);
        root.getChild("head").xRot = state.xRot * ((float) Math.PI / 180F);
    }
}
