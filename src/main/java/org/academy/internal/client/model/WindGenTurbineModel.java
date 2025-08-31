package org.academy.internal.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;

/**
 * @author 这里没有Badd
 */
public class WindGenTurbineModel extends Model {
    public final ModelPart all;
    public final ModelPart main;
    public final ModelPart tip_li;

    public WindGenTurbineModel(ModelPart root) {
        super(root.getChild("all"), RenderType::entityCutoutNoCull);
        this.all = root.getChild("all");
        this.main = this.all.getChild("main");
        this.tip_li = this.all.getChild("tip_li");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(11, 110).addBox(-1.5F, -16.0F, -1.0F, 3.0F, 16.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(112, 7).addBox(-3.5F, -124.0F, -0.5F, 7.0F, 120.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, 0.0F));

        main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(112, 7).addBox(-3.116F, -127.0F, -0.5F, 7.0F, 120.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(11, 110).addBox(-1.366F, -19.0F, -1.0F, 3.0F, 16.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.134F, 3.0F, 0.0F, 0.0F, 0.0F, 2.0944F));

        main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(112, 8).addBox(-3.5F, -126.0F, -0.5F, 7.0F, 119.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(11, 110).addBox(-1.5F, -19.0F, -1.0F, 3.0F, 16.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.134F, 3.0F, 0.0F, 0.0F, 0.0F, -2.0944F));

        PartDefinition tip_li = all.addOrReplaceChild("tip_li", CubeListBuilder.create().texOffs(41, 75).addBox(-2.366F, -130.0F, -0.5F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.134F, 1.5F, 0.0F));

        tip_li.addOrReplaceChild("li_r1", CubeListBuilder.create().texOffs(41, 75).addBox(-2.116F, -130.0F, -0.5F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0944F));

        tip_li.addOrReplaceChild("li_r2", CubeListBuilder.create().texOffs(41, 75).addBox(-2.5F, -129.0F, -0.5F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.2679F, 0.0F, 0.0F, 0.0F, 0.0F, -2.0944F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }
}