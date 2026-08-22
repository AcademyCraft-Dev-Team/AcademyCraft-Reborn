package org.academy.internal.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;
import org.academy.internal.client.renderer.entity.state.DarkmatterCreatureRenderState;

/** One complete model tree containing every built-in head, torso, limb and additional branch. */
public final class DarkmatterCreatureModel extends EntityModel<DarkmatterCreatureRenderState> {
    private final ModelPart[] heads = new ModelPart[3];
    private final ModelPart[] torsos = new ModelPart[3];
    private final ModelPart[] limbs = new ModelPart[3];
    private final ModelPart[] additional = new ModelPart[4];

    public DarkmatterCreatureModel(ModelPart root) {
        super(root);
        var all = root.getChild("all");
        for (var i = 0; i < heads.length; i++) heads[i] = all.getChild("head_" + i);
        for (var i = 0; i < torsos.length; i++) torsos[i] = all.getChild("torso_" + i);
        for (var i = 0; i < limbs.length; i++) limbs[i] = all.getChild("limbs_" + i);
        for (var i = 0; i < additional.length; i++) additional[i] = all.getChild("additional_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var all = mesh.getRoot().addOrReplaceChild("all", CubeListBuilder.create(),
                PartPose.offset(0, 24, 0));

        all.addOrReplaceChild("head_0", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3, -10, -6, 6, 4, 5, new CubeDeformation(0))
                .texOffs(0, 10).addBox(-3, -7, -8, 6, 1, 3, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("head_1", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3, -10, -5, 6, 4, 4, new CubeDeformation(0))
                .texOffs(20, 0).addBox(-1, -9, -9, 2, 2, 5, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("head_2", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3, -10, -5, 6, 4, 4, new CubeDeformation(0))
                .texOffs(20, 8).addBox(-3, -9, -7, 2, 2, 3, new CubeDeformation(0))
                .texOffs(20, 8).addBox(1, -9, -7, 2, 2, 3, new CubeDeformation(0)), PartPose.ZERO);

        all.addOrReplaceChild("torso_0", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-4, -7, -4, 8, 6, 9, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("torso_1", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-3, -7, -4, 6, 5, 8, new CubeDeformation(0))
                .texOffs(32, 0).addBox(-10, -6, -1, 7, 1, 6, new CubeDeformation(0))
                .texOffs(32, 0).mirror().addBox(3, -6, -1, 7, 1, 6, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("torso_2", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-3, -7, -5, 6, 5, 11, new CubeDeformation(0)), PartPose.ZERO);

        all.addOrReplaceChild("limbs_0", CubeListBuilder.create().texOffs(32, 16)
                .addBox(-7, -5, -3, 3, 2, 7, new CubeDeformation(0))
                .mirror().addBox(4, -5, -3, 3, 2, 7, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("limbs_1", CubeListBuilder.create().texOffs(32, 25)
                .addBox(-6, -4, -7, 2, 2, 9, new CubeDeformation(0))
                .mirror().addBox(4, -4, -7, 2, 2, 9, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("limbs_2", CubeListBuilder.create().texOffs(24, 36)
                .addBox(-6, -5, -2, 2, 4, 6, new CubeDeformation(0))
                .mirror().addBox(4, -5, -2, 2, 4, 6, new CubeDeformation(0)), PartPose.ZERO);

        all.addOrReplaceChild("additional_0", CubeListBuilder.create(), PartPose.ZERO);
        all.addOrReplaceChild("additional_1", CubeListBuilder.create().texOffs(0, 36)
                .addBox(-4, -8, -4, 8, 2, 9, new CubeDeformation(.35f)), PartPose.ZERO);
        all.addOrReplaceChild("additional_2", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-1, -13, -1, 2, 5, 2, new CubeDeformation(0))
                .texOffs(48, 8).addBox(-3, -14, -1, 6, 1, 2, new CubeDeformation(0)), PartPose.ZERO);
        all.addOrReplaceChild("additional_3", CubeListBuilder.create().texOffs(40, 38)
                .addBox(-2, -6, 4, 4, 3, 7, new CubeDeformation(0)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(DarkmatterCreatureRenderState state) {
        super.setupAnim(state);
        select(heads, state.headModel);
        select(torsos, state.torsoModel);
        select(limbs, state.limbsModel);
        select(additional, state.additionalModel);
        var gait = Mth.sin(state.walkAnimationPos * .9f) * state.walkAnimationSpeed * .2f;
        for (var part : limbs) part.zRot = gait;
        if (state.torsoModel == 1) torsos[1].zRot = Mth.sin(state.ageInTicks * .25f) * .04f;
        if (state.gammaCatalyzed) additional[Math.clamp(state.additionalModel, 0, 3)].yRot
                = Mth.sin(state.ageInTicks * .12f) * .05f;
    }

    private static void select(ModelPart[] parts, int selected) {
        for (var i = 0; i < parts.length; i++) parts[i].visible = i == selected;
    }
}
