package org.academy.internal.client.model;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.academy.internal.client.definitions.AbilityDeveloperAnimation;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;

/**
 * @author MapleBadd
 */
public class AbilityDeveloperModel extends Model<AbilityDeveloperRenderState> {
    private final ModelPart all;
    private final ModelPart base;
    private final ModelPart middle;
    private final ModelPart up;
    private final ModelPart pod;
    private final ModelPart sideBar;
    private final ModelPart rwheel;
    private final ModelPart lwheel;
    private final ModelPart logo;
    private final ModelPart glass;
    private final ModelPart lside;
    private final ModelPart lside1;
    private final ModelPart lside2;
    private final ModelPart rside;
    private final ModelPart rside1;
    private final ModelPart rside2;


    public AbilityDeveloperModel(ModelPart root) {
        var all = root.getChild("all");
        super(all, RenderTypes::entityTranslucent);

        this.all = all;
        base = all.getChild("base");
        middle = all.getChild("middle");
        up = all.getChild("up");
        pod = up.getChild("pod");
        sideBar = pod.getChild("sideBar");
        rwheel = pod.getChild("rwheel");
        lwheel = pod.getChild("lwheel");
        logo = pod.getChild("logo");
        glass = up.getChild("glass");
        lside = glass.getChild("lside");
        lside1 = lside.getChild("lside1");
        lside2 = lside.getChild("lside2");
        rside = glass.getChild("rside");
        rside1 = rside.getChild("rside1");
        rside2 = rside.getChild("rside2");
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(8.0F, 0.0F, -13.0F));

        var base = all.addOrReplaceChild("base", CubeListBuilder.create().texOffs(49, 199).addBox(-10.0F, 0.0F, -10.0F, 20.0F, 1.0F, 45.0F, new CubeDeformation(0.0F))
                .texOffs(0, 222).addBox(-7.0F, 1.0F, -10.0F, 14.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 234).addBox(-7.0F, 0.0F, 34.0F, 14.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 204).addBox(-11.0F, 0.0F, -10.0F, 1.0F, 7.0F, 45.0F, new CubeDeformation(0.0F))
                .texOffs(0, 204).addBox(10.0F, 0.0F, -10.0F, 1.0F, 7.0F, 45.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(3.0F, 0.9F, 23.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(-7.0F, 0.9F, 23.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var cube_r1 = base.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 203).addBox(-3.0F, -5.5F, 0.0F, 6.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.7F, 5.25F, 33.75F, 0.0436F, 0.0F, 0.0436F));

        var cube_r2 = base.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(22, 204).addBox(-3.0F, -5.5F, -3.0F, 6.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.7F, 5.25F, -7.75F, -0.0436F, 0.0F, 0.0436F));

        var cube_r3 = base.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 203).addBox(-3.0F, -5.5F, 0.0F, 6.0F, 11.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.7F, 5.25F, 33.75F, 0.0436F, 0.0F, -0.0436F));

        var cube_r4 = base.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(22, 204).addBox(-3.0F, -5.5F, -3.0F, 6.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.7F, 5.25F, -7.75F, -0.0436F, 0.0F, -0.0436F));

        var middle = all.addOrReplaceChild("middle", CubeListBuilder.create(), PartPose.offset(0.0F, 2.5F, 25.0F));

        var cube_r5 = middle.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(0, 1).addBox(4.0F, -13.9146F, -7.0157F, 2.0F, 20.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 1).addBox(-6.0F, -13.9146F, -7.0157F, 2.0F, 20.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 5.9146F, -13.9843F, 1.5708F, 0.0F, 0.0F));

        var up = all.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offset(-2.0F, 8.0F, -6.0F));

        var pod = up.addOrReplaceChild("pod", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -5.5F, -18.25F, 16.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(69, 39).addBox(-2.0F, -5.5F, -23.25F, 8.0F, 13.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(-5.0F, -4.5F, -20.25F, 14.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 17).addBox(-5.0F, 3.5F, -22.25F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(84, 17).addBox(6.0F, 3.5F, -22.25F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(178, 0).addBox(-6.0F, 3.5F, 17.75F, 16.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(146, 24).addBox(-2.0F, 3.5F, 16.75F, 8.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(180, 10).addBox(-6.0F, -4.5F, 17.75F, 16.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(158, 0).addBox(-2.0F, -4.5F, 19.75F, 8.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(19, 42).addBox(5.0F, -7.5F, -3.25F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(19, 42).addBox(-5.0F, -7.5F, -3.25F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(63, 40).addBox(6.8F, -4.5F, -18.25F, 5.0F, 3.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(34, 102).addBox(8.0F, -3.5F, -18.25F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(34, 102).mirror().addBox(-6.0F, -3.5F, -18.25F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(105, 0).addBox(-4.0F, -3.5F, -18.25F, 1.0F, 2.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.4F, 18.25F));

        var cube_r6 = pod.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(144, 14).addBox(-5.0F, -3.0F, -2.5F, 10.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -0.5F, -16.75F, 0.3491F, 0.0F, 0.0F));

        var cube_r7 = pod.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(103, 9).addBox(-2.0F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(118, 9).addBox(14.9F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.95F, 0.5F, 19.25F, -0.1833F, 0.0F, 0.0F));

        var cube_r8 = pod.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -0.9759F, 17.149F, -0.4363F, 0.0F, 0.0F));

        var cube_r9 = pod.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(109, 26).addBox(-2.0F, -4.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 1.25F, 16.6F, -0.4363F, 0.0F, 0.0F));

        var cube_r10 = pod.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(118, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.6F, 0.483F, -19.0794F, 0.2769F, 0.2261F, 0.1332F));

        var cube_r11 = pod.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(103, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6F, 0.483F, -19.0794F, 0.2769F, -0.2261F, -0.1332F));

        var sideBar = pod.addOrReplaceChild("sideBar", CubeListBuilder.create().texOffs(0, 64).addBox(-7.7F, -4.5F, -15.25F, 3.0F, 3.0F, 33.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var rwheel = pod.addOrReplaceChild("rwheel", CubeListBuilder.create().texOffs(13, 5).addBox(-0.8F, -3.0F, -3.0F, 3.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(10.7F, -0.5F, -18.25F));

        var lwheel = pod.addOrReplaceChild("lwheel", CubeListBuilder.create().texOffs(13, 5).addBox(-2.2F, -3.0F, -3.0F, 3.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.7F, -0.5F, -18.25F));

        var logo = pod.addOrReplaceChild("logo", CubeListBuilder.create(), PartPose.offset(2.0F, 8.0F, -20.15F));

        var cube_r12 = logo.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(144, 3).addBox(-1.6628F, -1.494F, -0.3372F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.0584F, 0.4558F, -0.664F));

        var glass = up.addOrReplaceChild("glass", CubeListBuilder.create(), PartPose.offset(2.0F, -1.0F, 0.0F));

        var lside = glass.addOrReplaceChild("lside", CubeListBuilder.create().texOffs(0, 107).addBox(0.1716F, 0.0716F, -36.0F, 0.0F, 6.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-9.0F, -1.0F, 36.0F));

        var lside1 = lside.addOrReplaceChild("lside1", CubeListBuilder.create().texOffs(-36, 102).addBox(0.0F, 0.0F, -36.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(3.0F, 8.9F, 0.0F));

        var lside2 = lside.addOrReplaceChild("lside2", CubeListBuilder.create(), PartPose.offset(0.0F, 6.0F, 0.0F));

        var cube_r13 = lside2.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(0, 114).addBox(-0.5F, -2.0F, -34.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9393F, 1.1322F, -2.0F, 0.0F, 0.0F, 0.7854F));

        var rside = glass.addOrReplaceChild("rside", CubeListBuilder.create().texOffs(0, 124).addBox(-0.1716F, 0.0716F, -36.0F, 0.0F, 6.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(9.0F, -1.0F, 36.0F));

        var rside1 = rside.addOrReplaceChild("rside1", CubeListBuilder.create().texOffs(-21, 101).addBox(-6.0F, 0.0F, -36.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.0F, 8.9F, 0.0F));

        var rside2 = rside.addOrReplaceChild("rside2", CubeListBuilder.create(), PartPose.offset(0.0F, 6.0F, 0.0F));

        var cube_r14 = rside2.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(0, 119).addBox(0.5F, -2.0F, -34.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.9393F, 1.1322F, -2.0F, 0.0F, 0.0F, -0.7854F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(AbilityDeveloperRenderState renderState) {
        super.setupAnim(renderState);
    }
}