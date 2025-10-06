package org.academy.internal.client.model;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import org.academy.internal.client.definitions.AbilityDeveloperAnimation;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;

/**
 * @author 这里没有Badd
 */
public class AbilityDeveloperModel extends Model<AbilityDeveloperRenderState> {
    private final KeyframeAnimation open;
    private final KeyframeAnimation close;
    private final KeyframeAnimation liedown;
    private final KeyframeAnimation stand;

    public AbilityDeveloperModel(ModelPart root) {
        super(root.getChild("all"), RenderType::entityTranslucent);
        var all = root.getChild("all");
        ModelPart up = all.getChild("up");
        var glass = up.getChild("glass");
        glass.getChild("lsidebars");
        var base = up.getChild("base");
        base.getChild("light_li");
        base.getChild("logo_li");
        base.getChild("lwheel");
        base.getChild("rwheel");
        all.getChild("bottom");
        this.open = AbilityDeveloperAnimation.OPEN.bake(root);
        this.close = AbilityDeveloperAnimation.CLOSE.bake(root);
        this.liedown = AbilityDeveloperAnimation.LIEDOWN.bake(root);
        this.stand = AbilityDeveloperAnimation.STAND.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 16.0F));

        var up = all.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -22.174F, -13.5346F, -1.0472F, 0.0F, 0.0F));

        var glass = up.addOrReplaceChild("glass", CubeListBuilder.create(), PartPose.offset(0.0F, 9.174F, 0.5346F));

        var rside = glass.addOrReplaceChild("rside", CubeListBuilder.create().texOffs(0, 125).addBox(2.3013F, 0.1297F, -18.0F, 0.0F, 5.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(5, 101).addBox(5.1297F, -2.6987F, -18.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.1297F, -13.3013F, -1.0F));

        rside.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 119).addBox(-0.5F, -2.0F, -2.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.069F, -0.931F, -16.0F, 0.0F, 0.0F, 0.7854F));

        var lside = glass.addOrReplaceChild("lside", CubeListBuilder.create().texOffs(24, 107).addBox(-2.3013F, 0.1297F, -18.0F, 0.0F, 5.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(48, 79).addBox(-11.1297F, -2.6987F, -18.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(11.1297F, -13.3013F, -1.0F));

        lside.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 113).addBox(0.5F, -2.0F, -2.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.069F, -0.931F, -16.0F, 0.0F, 0.0F, -0.7854F));

        glass.addOrReplaceChild("lsidebars", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var base = up.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 3.174F, -18.4654F, 16.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(154, 6).addBox(-4.0F, 2.174F, -13.4654F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(69, 39).addBox(-4.0F, -7.826F, 16.5346F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(-7.0F, -3.826F, 17.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(4.0F, -3.826F, 17.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 17).addBox(4.0F, -6.826F, 18.0346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(84, 17).addBox(-7.0F, -6.826F, 18.0346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(86, 9).addBox(-8.0F, -6.826F, -22.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(69, 0).addBox(-4.0F, -7.826F, -23.4654F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 9).addBox(4.0F, -6.826F, -22.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(103, 24).addBox(-8.0F, -3.826F, -20.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(110, 42).addBox(-4.0F, -5.826F, -21.4654F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(106, 65).addBox(-2.0F, -3.826F, -17.4654F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(42, 39).addBox(-2.0F, 5.174F, -13.4654F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(103, 24).addBox(4.0F, -3.826F, -20.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(126, 1).addBox(-5.0F, -2.826F, -4.4654F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(65, 39).addBox(8.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(0, 64).addBox(10.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 33.0F, new CubeDeformation(0.0F))
                .texOffs(105, 66).addBox(-10.0F, 1.174F, -18.4654F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(22, 102).addBox(-8.0F, 0.174F, -18.4654F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(22, 102).mirror().addBox(6.0F, 0.174F, -18.4654F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(105, 0).addBox(-6.0F, 2.174F, -18.4654F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(105, 0).addBox(5.0F, 2.174F, -18.4654F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        base.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(174, 25).addBox(-4.0F, -2.0F, 0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(174, 19).addBox(-9.0F, -2.0F, 0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, 0.174F, 14.0346F, -0.3491F, 0.0F, 0.0F));

        base.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(145, 24).addBox(-5.0F, -3.0F, -0.5F, 10.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.174F, 16.0346F, -0.3491F, 0.0F, 0.0F));

        base.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(146, 3).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -8.326F, 19.4346F, 1.0584F, -0.4558F, -0.664F));

        base.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(133, 7).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3F, 0.174F, -2.4654F, 0.0F, 0.0F, 0.0873F));

        base.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(103, 9).addBox(-1.0F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(118, 9).addBox(-17.9F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.95F, -0.826F, -19.9654F, 0.1833F, 0.0F, 0.0F));

        base.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.6499F, -17.8644F, 0.4189F, 0.0F, 0.0F));

        base.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(118, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.6F, -0.809F, 18.864F, -0.2769F, 0.2261F, -0.1332F));

        base.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(103, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.6F, -0.809F, 18.864F, -0.2769F, -0.2261F, 0.1332F));

        base.addOrReplaceChild("light_li", CubeListBuilder.create().texOffs(146, 138).addBox(-4.0F, -5.51F, -22.75F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(152, 130).addBox(-8.0F, -4.51F, -21.75F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(107, 115).addBox(10.0F, 3.49F, -17.75F, 2.0F, 3.0F, 33.0F, new CubeDeformation(0.0F))
                .texOffs(155, 124).addBox(-2.0F, -1.5F, -16.74F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.326F, -0.7154F));

        var logo_li = base.addOrReplaceChild("logo_li", CubeListBuilder.create(), PartPose.offset(0.0F, -8.326F, 19.4346F));

        logo_li.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(146, 3).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 1.0584F, 0.4558F, 0.664F));

        base.addOrReplaceChild("lwheel", CubeListBuilder.create().texOffs(86, 61).addBox(0.2F, -2.0F, -3.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(71, 61).addBox(0.2F, -3.0F, -2.0F, 2.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(85, 61).addBox(-0.8F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(8.7F, 0.174F, 17.5346F));

        base.addOrReplaceChild("rwheel", CubeListBuilder.create().texOffs(85, 61).addBox(-2.2F, -2.0F, -3.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(70, 61).addBox(-2.2F, -3.0F, -2.0F, 2.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(85, 61).addBox(-2.2F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.7F, 0.174F, 17.5346F));

        var middle = all.addOrReplaceChild("middle", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, -29.0F));

        var m1 = middle.addOrReplaceChild("m1", CubeListBuilder.create(), PartPose.offset(0.0F, -21.3671F, 3.4194F));

        m1.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, -13.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.6171F, -1.5194F, -0.1745F, 0.0F, 0.0F));

        var m2 = middle.addOrReplaceChild("m2", CubeListBuilder.create(), PartPose.offset(0.0F, -12.5038F, 1.8566F));

        m2.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, -4.25F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.2462F, 0.0434F, -0.1745F, 0.0F, 0.0F));

        var m3 = middle.addOrReplaceChild("m3", CubeListBuilder.create(), PartPose.offset(0.0F, -3.6405F, 0.2937F));

        m3.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(116, 128).addBox(-1.0F, 4.75F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -9.1095F, 1.6063F, -0.1745F, 0.0F, 0.0F));

        var bottom = all.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 218).addBox(-11.0F, 17.0F, -28.0F, 22.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(17, 178).addBox(-11.0F, 17.0F, 8.0F, 22.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(83, 208).addBox(10.0F, 15.0F, -29.0F, 1.0F, 2.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(60, 170).addBox(10.0F, 13.0F, -30.0F, 1.0F, 2.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(60, 170).addBox(-11.0F, 13.0F, -30.0F, 1.0F, 2.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(0, 180).addBox(-11.0F, 11.0F, -30.0F, 1.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(0, 180).addBox(10.0F, 11.0F, -30.0F, 1.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(2, 232).addBox(10.0F, 15.0F, 8.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(9.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(-10.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(-8.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(7.0F, 16.0F, -28.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(2, 232).addBox(-11.0F, 15.0F, 8.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(83, 208).addBox(-11.0F, 15.0F, -29.0F, 1.0F, 2.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(3, 219).addBox(-2.0F, 15.0F, -21.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -19.0F, -10.0F));

        bottom.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(71, 210).addBox(-10.0F, -1.5F, -0.5F, 20.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 16.5772F, 17.8879F, -0.3927F, 0.0F, 0.0F));

        bottom.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(9, 199).addBox(-10.0F, -3.0F, -1.5F, 22.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 13.963F, -29.3139F, 0.3927F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(AbilityDeveloperRenderState renderState) {
        super.setupAnim(renderState);
        open.apply(renderState.openState, renderState.ageInTicks);
        close.apply(renderState.closingState, renderState.ageInTicks);
        stand.apply(renderState.standState, renderState.ageInTicks);
        liedown.apply(renderState.liedownState, renderState.ageInTicks);
    }
}