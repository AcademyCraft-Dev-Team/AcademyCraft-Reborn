package org.academy.internal.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;

/**
 * @author MapleBadd
 */
@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class AbilityDeveloperModel extends Model<AbilityDeveloperRenderState> {
    public static final AbilityDeveloperModel MODEL = new AbilityDeveloperModel(createBodyLayer().bakeRoot());

    public AbilityDeveloperModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 7.6F, -20.9F));

        var up = all.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offset(0.0F, 8.4F, 1.9F));

        var pod = up.addOrReplaceChild("pod", CubeListBuilder.create().texOffs(13, 5).addBox(-1.5F, -3.0F, -3.0F, 3.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(1.4F, 3.0F, 0.0F, 16.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(69, 39).addBox(5.4F, -8.0F, -5.0F, 8.0F, 13.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(2.4F, -4.0F, -2.0F, 14.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 17).addBox(2.4F, -7.0F, -4.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(84, 17).addBox(13.4F, -7.0F, -4.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(178, 0).addBox(1.4F, -7.0F, 36.0F, 16.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(146, 24).addBox(5.4F, -8.0F, 35.0F, 8.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(180, 10).addBox(1.4F, -4.0F, 36.0F, 16.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(158, 0).addBox(5.4F, -6.0F, 38.0F, 8.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(19, 42).addBox(12.4F, 5.0F, 15.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(19, 42).addBox(2.4F, 5.0F, 15.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(63, 40).addBox(14.2F, 1.0F, 0.0F, 5.0F, 3.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(34, 102).addBox(15.4F, 0.0F, 0.0F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(34, 102).mirror().addBox(1.4F, 0.0F, 0.0F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(105, 0).addBox(3.4F, 1.0F, 0.0F, 1.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(0, 64).addBox(-0.3F, 1.0F, 3.0F, 3.0F, 3.0F, 33.0F, new CubeDeformation(0.0F))
                .texOffs(13, 5).addBox(17.3F, -3.0F, -3.0F, 3.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-9.4F, 0.1F, 0.0F));

        var cube_r1 = pod.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(144, 14).addBox(-5.0F, -3.0F, -2.5F, 10.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.4F, 0.0F, 1.5F, 0.3491F, 0.0F, 0.0F));

        var cube_r2 = pod.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(103, 9).addBox(-2.0F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(118, 9).addBox(14.9F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.45F, -1.0F, 37.5F, -0.1833F, 0.0F, 0.0F));

        var cube_r3 = pod.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.4F, 0.4759F, 35.399F, -0.4363F, 0.0F, 0.0F));

        var cube_r4 = pod.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(109, 26).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.4F, -1.75F, 34.85F, -0.4363F, 0.0F, 0.0F));

        var cube_r5 = pod.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(118, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(17.0F, -0.983F, -0.8294F, 0.2769F, 0.2261F, 0.1332F));

        var cube_r6 = pod.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(103, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.8F, -0.983F, -0.8294F, 0.2769F, -0.2261F, -0.1332F));

        var logo = up.addOrReplaceChild("logo", CubeListBuilder.create(), PartPose.offset(0.0F, -8.4F, -1.9F));

        var cube_r7 = logo.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(144, 3).addBox(-1.6628F, -0.506F, -0.3372F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.4F, 0.0F, -1.0584F, 0.4558F, -0.664F));

        var glass = up.addOrReplaceChild("glass", CubeListBuilder.create(), PartPose.offset(9.8284F, 1.9284F, 36.0F));

        var L1 = glass.addOrReplaceChild("L1", CubeListBuilder.create().texOffs(0, 124).addBox(-0.1716F, -7.1716F, -35.0F, 0.0F, 6.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.8284F, 1.1716F, -1.0F));

        var L2 = L1.addOrReplaceChild("L2", CubeListBuilder.create(), PartPose.offset(0.0607F, -8.2322F, -1.0F));

        var cube_r8 = L2.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(0, 119).addBox(0.5F, -2.0F, -34.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));

        var L3 = L2.addOrReplaceChild("L3", CubeListBuilder.create().texOffs(-21, 101).addBox(-6.0F, -1.0F, -35.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.0607F, -0.7678F, 1.0F));

        var R1 = glass.addOrReplaceChild("R1", CubeListBuilder.create().texOffs(0, 107).addBox(0.1716F, -7.1716F, -35.0F, 0.0F, 6.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-18.8284F, 1.1716F, -1.0F));

        var R2 = R1.addOrReplaceChild("R2", CubeListBuilder.create(), PartPose.offset(-0.0607F, -8.2322F, -1.0F));

        var cube_r9 = R2.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 114).addBox(-0.5F, -2.0F, -34.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));

        var R3 = R2.addOrReplaceChild("R3", CubeListBuilder.create().texOffs(-36, 102).addBox(0.0F, -1.0F, -35.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(3.0607F, -0.7678F, 1.0F));

        var poles = all.addOrReplaceChild("poles", CubeListBuilder.create().texOffs(132, 105).addBox(4.0F, -1.0F, -20.0F, 2.0F, 2.0F, 20.0F, new CubeDeformation(0.0F))
                .texOffs(132, 105).addBox(-6.0F, -1.0F, -20.0F, 2.0F, 2.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 13.9F, 32.9F));

        var base = all.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 204).addBox(-15.0F, -7.0F, -14.0F, 1.0F, 7.0F, 45.0F, new CubeDeformation(0.0F))
                .texOffs(49, 199).addBox(-35.0F, -1.0F, -14.0F, 20.0F, 1.0F, 45.0F, new CubeDeformation(0.0F))
                .texOffs(0, 234).addBox(-32.0F, -9.0F, 30.0F, 14.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 204).addBox(-36.0F, -7.0F, -14.0F, 1.0F, 7.0F, 45.0F, new CubeDeformation(0.0F))
                .texOffs(0, 222).addBox(-32.0F, -6.0F, -14.0F, 14.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(-22.0F, -2.9F, 19.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(-32.0F, -2.9F, 19.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(25.0F, 16.4F, 11.9F));

        var cube_r10 = base.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(22, 204).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-16.3F, -5.25F, -11.75F, -0.0436F, 0.0F, -0.0436F));

        var cube_r11 = base.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(22, 204).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-33.7F, -5.25F, -11.75F, -0.0436F, 0.0F, 0.0436F));

        var cube_r12 = base.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(0, 203).addBox(-3.0F, -5.5F, 0.0F, 6.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-33.7F, -5.25F, 29.75F, 0.0436F, 0.0F, 0.0436F));

        var cube_r13 = base.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(0, 203).addBox(-3.0F, -5.5F, 0.0F, 6.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-16.3F, -5.25F, 29.75F, 0.0436F, 0.0F, -0.0436F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(AbilityDeveloperRenderState renderState) {
        super.setupAnim(renderState);

    }
}
