/*
package org.academy.internal.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;

*/
/**
 * @author MapleBadd
 *//*

public class ImagiphaseDowsingRodModel extends Model<Unit> {
    private final ModelPart all;
    private final ModelPart handle;
    private final ModelPart pointer;
    private final ModelPart main;

    public ImagiphaseDowsingRodModel(ModelPart root) {
        super(root.getChild("all"), RenderTypes::entityCutout);
        all = root.getChild("all");
        handle = all.getChild("handle");
        pointer = handle.getChild("pointer");
        main = all.getChild("main");
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var handle = all.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(1, 17).addBox(-7.0F, -9.75F, -1.5F, 10.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 44).addBox(-11.0F, -9.75F, -0.5F, 10.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(31, 35).addBox(-10.75F, -10.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-12.0F, -12.75F, -1.5F, 16.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 23).addBox(-12.0F, -14.75F, -1.5F, 15.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var cube_r1 = handle.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(13, 31).addBox(-0.5F, -1.0F, -2.001F, 1.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0182F, -13.6321F, 0.0F, 0.0F, 0.0F, -0.48F));

        var cube_r2 = handle.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 31).addBox(-3.0F, -4.5F, -1.0F, 4.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.25F, -6.5F, 0.0F, 0.0F, 0.0F, -0.3054F));

        var pointer = handle.addOrReplaceChild("pointer", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var cube_r3 = pointer.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(28, 44).addBox(0.0F, -2.75F, -0.5F, 0.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -11.0F, 0.0F, 0.0F, 0.0F, -0.48F));

        var main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 8).addBox(-13.0F, -15.0F, -1.0F, 14.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(25, 35).addBox(-13.5F, -14.5F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}*/
