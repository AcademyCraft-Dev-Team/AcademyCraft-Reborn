package org.academy.internal.client.model;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.academy.internal.client.definitions.AbilityControlTabletAnimation;
import org.academy.internal.client.renderer.special.AbilityControlTabletRenderState;

public final class AbilityControlTabletModel extends Model<AbilityControlTabletRenderState> {
    public static final AbilityControlTabletModel MODEL = new AbilityControlTabletModel(
            createBodyLayer(false).bakeRoot()
    );
    public static final AbilityControlTabletModel SCREEN_MODEL = new AbilityControlTabletModel(
            createBodyLayer(true).bakeRoot()
    );
    private final KeyframeAnimation open;

    private AbilityControlTabletModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        open = AbilityControlTabletAnimation.OPEN.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        return createBodyLayer(false);
    }

    private static LayerDefinition createBodyLayer(boolean screenOnly) {
        var meshDefinition = new MeshDefinition();
        var root = meshDefinition.getRoot();

        var allCubes = CubeListBuilder.create();
        if (!screenOnly) {
            allCubes.texOffs(25, 4).mirror().addBox(
                    -0.5F, -0.5F, -1.0F,
                    1.0F, 1.0F, 2.0F,
                    new CubeDeformation(0.0F)
            ).mirror(false).texOffs(25, 0).mirror().addBox(
                    -1.0F, -1.0F, -0.5F,
                    2.0F, 2.0F, 1.0F,
                    new CubeDeformation(0.1F)
            ).mirror(false);
        }

        var all = root.addOrReplaceChild(
                "all",
                allCubes,
                PartPose.offset(-5.25F, 23.25F, 0.0F)
        );

        var f1Cubes = CubeListBuilder.create();
        if (!screenOnly) {
            f1Cubes.texOffs(0, 10).mirror().addBox(
                    -1.0F, -7.7F, -0.5F,
                    2.0F, 7.5F, 1.0F,
                    new CubeDeformation(0.2F)
            ).mirror(false);
        }
        all.addOrReplaceChild(
                "f1",
                f1Cubes,
                PartPose.offset(0.05F, -0.05F, 0.0F)
        );

        var f2Cubes = CubeListBuilder.create();
        if (!screenOnly) {
            f2Cubes.texOffs(7, 10).mirror().addBox(
                    0.2F, 0.0F, -0.5F,
                    11.5F, 1.0F, 1.0F,
                    new CubeDeformation(0.21F)
            ).mirror(false);
        }
        all.addOrReplaceChild(
                "f2",
                f2Cubes,
                PartPose.offset(0.05F, -0.05F, 0.0F)
        );

        var screenCubes = CubeListBuilder.create();
        if (screenOnly) {
            screenCubes.texOffs(0, 0).mirror().addBox(
                    0.0F, -8.0F, 1.0F,
                    12.0F, 8.0F, 0.0F,
                    new CubeDeformation(0.0F)
            ).mirror(false);
        }
        all.addOrReplaceChild(
                "screen",
                screenCubes,
                PartPose.offset(-0.65F, 0.65F, -1.0F)
        );

        return LayerDefinition.create(meshDefinition, 48, 48);
    }

    @Override
    public void setupAnim(AbilityControlTabletRenderState renderState) {
        super.setupAnim(renderState);
        open.apply(renderState.animationTimeMillis(), 1.0F);
    }
}
