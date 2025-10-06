package org.academy.internal.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * @author 这里没有Badd
 */
public class WindGenTurbineModel extends Model<Void> {
    public static final WindGenTurbineModel INSTANCE = new WindGenTurbineModel(createBodyLayer().bakeRoot());

    public final ModelPart all;
    public final ModelPart main;
    public final ModelPart tip_li;

    private WindGenTurbineModel(ModelPart root) {
        super(root.getChild("all"), RenderType::entityCutoutNoCull);
        this.all = root.getChild("all");
        this.main = this.all.getChild("main");
        this.tip_li = this.all.getChild("tip_li");
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(17, 46).addBox(-1.5F, -16.0F, -1.0F, 3.0F, 16.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, 0.0F));

        var cube_r1 = main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(112, 7).addBox(-3.116F, -127.0F, -0.5F, 7.0F, 120.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.134F, 3.0F, 0.0F, 0.0F, -0.3927F, 2.0944F));

        var cube_r2 = main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(17, 46).addBox(-1.366F, -19.0F, -1.0F, 3.0F, 16.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.134F, 3.0F, 0.0F, 0.0F, 0.0F, 2.0944F));

        var cube_r3 = main.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(112, 8).addBox(-3.5F, -126.0F, -0.5F, 7.0F, 119.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.134F, 3.0F, 0.0F, 0.0F, -0.3927F, -2.0944F));

        var cube_r4 = main.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(17, 46).addBox(-1.5F, -19.0F, -1.0F, 3.0F, 16.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.134F, 3.0F, 0.0F, 0.0F, 0.0F, -2.0944F));

        var cube_r5 = main.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(112, 7).addBox(-3.5F, -120.0F, -1.0F, 7.0F, 120.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.0F, 0.5F, 0.0F, -0.3927F, 0.0F));

        var tip_li = all.addOrReplaceChild("tip_li", CubeListBuilder.create(), PartPose.offset(-0.134F, 1.5F, 0.0F));

        var li_r1 = tip_li.addOrReplaceChild("li_r1", CubeListBuilder.create().texOffs(1, 49).addBox(-2.116F, -130.0F, -0.5F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.3927F, 2.0944F));

        var li_r2 = tip_li.addOrReplaceChild("li_r2", CubeListBuilder.create().texOffs(1, 49).addBox(-2.5F, -129.0F, -0.5F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.2679F, 0.0F, 0.0F, 0.0F, -0.4363F, -2.0944F));

        var li_r3 = tip_li.addOrReplaceChild("li_r3", CubeListBuilder.create().texOffs(1, 49).addBox(-2.0F, -14.0F, -1.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.366F, -116.0F, 0.5F, 0.0F, -0.3927F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }
}