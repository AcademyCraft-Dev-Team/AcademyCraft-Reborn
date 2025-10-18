package org.academy.internal.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * @author MapleBadd
 */
public class CleaningRobotModel extends EntityModel<EntityRenderState> {
    private final ModelPart all;
    private final ModelPart top;
    private final ModelPart bottom;
    private final ModelPart rleg;
    private final ModelPart lleg;
    private final ModelPart bleg;

    public CleaningRobotModel(ModelPart root) {
        super(root);
        all = root.getChild("all");
        top = all.getChild("top");
        bottom = all.getChild("bottom");
        rleg = bottom.getChild("rleg");
        lleg = bottom.getChild("lleg");
        bleg = bottom.getChild("bleg");
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var top = all.addOrReplaceChild("top", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5F, -14.0F, -3.5F, 7.0F, 8.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-4.0F, -15.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(31, 5).addBox(-1.5F, -6.0F, -3.5F, 3.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(33, 30).addBox(1.5F, -6.0F, -1.5F, 2.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(34, 17).addBox(-3.5F, -6.0F, -1.5F, 2.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(25, 2).addBox(-1.0F, -15.25F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var bottom = all.addOrReplaceChild("bottom", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var rleg = bottom.addOrReplaceChild("rleg", CubeListBuilder.create().texOffs(56, 0).addBox(-1.4971F, -1.0F, -1.4971F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(57, 1).addBox(-1.4971F, -2.0F, -1.4971F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(49, 15).addBox(-1.4971F, -2.0F, -0.4971F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.9971F, -4.0F, -1.9971F));

        var cube_r1 = rleg.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(51, 19).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.4971F, -1.0F, 0.4971F, 0.3927F, 0.7854F, 0.0F));

        var lleg = bottom.addOrReplaceChild("lleg", CubeListBuilder.create().texOffs(56, 9).addBox(-0.5F, -1.075F, -1.5F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(57, 10).addBox(-0.5F, -2.075F, -1.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(49, 12).addBox(0.5F, -2.075F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, -3.925F, -2.0F));

        var cube_r2 = lleg.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(51, 19).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -0.925F, 0.5F, 0.3927F, -0.7854F, 0.0F));

        var bleg = bottom.addOrReplaceChild("bleg", CubeListBuilder.create().texOffs(45, 1).addBox(-1.5F, -1.0F, -1.75F, 3.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(48, 9).addBox(-1.5F, -2.0F, -0.75F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 3.25F));

        var cube_r3 = bleg.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(51, 19).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -1.75F, -0.3927F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}