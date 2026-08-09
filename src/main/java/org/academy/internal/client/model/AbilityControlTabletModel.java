package org.academy.internal.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public final class AbilityControlTabletModel {
    public static final ModelPart MODEL = createBodyLayer().bakeRoot().getChild("all");

    private AbilityControlTabletModel() {
    }

    public static LayerDefinition createBodyLayer() {
        var meshDefinition = new MeshDefinition();
        var root = meshDefinition.getRoot();

        var all = root.addOrReplaceChild(
                "all",
                CubeListBuilder.create()
                        .texOffs(25, 4).mirror().addBox(
                                -0.5F, -0.5F, -1.0F,
                                1.0F, 1.0F, 2.0F,
                                new CubeDeformation(0.0F)
                        ).mirror(false)
                        .texOffs(25, 0).mirror().addBox(
                                -1.0F, -1.0F, -0.5F,
                                2.0F, 2.0F, 1.0F,
                                new CubeDeformation(0.1F)
                        ).mirror(false),
                PartPose.offset(-5.25F, 23.25F, 0.0F)
        );

        all.addOrReplaceChild(
                "f1",
                CubeListBuilder.create()
                        .texOffs(0, 10).mirror().addBox(
                                -1.0F, -7.7F, -0.5F,
                                2.0F, 7.5F, 1.0F,
                                new CubeDeformation(0.2F)
                        ).mirror(false),
                PartPose.offset(0.05F, -0.05F, 0.0F)
        );

        all.addOrReplaceChild(
                "f2",
                CubeListBuilder.create()
                        .texOffs(8, 11).mirror().addBox(
                                0.2F, -1.0F, -0.5F,
                                11.5F, 2.0F, 1.0F,
                                new CubeDeformation(0.21F)
                        ).mirror(false),
                PartPose.offset(0.05F, -0.05F, 0.0F)
        );

        all.addOrReplaceChild(
                "screen",
                CubeListBuilder.create()
                        .texOffs(0, 0).mirror().addBox(
                                0.0F, -8.0F, 1.0F,
                                12.0F, 8.0F, 0.0F,
                                new CubeDeformation(0.0F)
                        ).mirror(false),
                PartPose.offset(-0.65F, 0.65F, -1.0F)
        );

        return LayerDefinition.create(meshDefinition, 48, 48);
    }
}
