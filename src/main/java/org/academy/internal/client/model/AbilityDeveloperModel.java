package org.academy.internal.client.model;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import org.academy.internal.client.definitions.AbilityDeveloperAnimation;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;

/**
 * @author MapleBadd
 */
public class AbilityDeveloperModel extends Model<AbilityDeveloperRenderState> {
    private final ModelPart all;
    private final ModelPart up;
    private final ModelPart glass;
    private final ModelPart rside;
    private final ModelPart lside;
    private final ModelPart lsidebars;
    private final ModelPart base;
    private final ModelPart light_li;
    private final ModelPart logo_li;
    private final ModelPart lwheel;
    private final ModelPart rwheel;
    private final ModelPart middle;
    private final ModelPart bottom;
    private final KeyframeAnimation opening;
    private final KeyframeAnimation closing;
    private final KeyframeAnimation lyingDown;
    private final KeyframeAnimation standing;

    public AbilityDeveloperModel(ModelPart root) {
        super(root.getChild("all"), RenderType::entityTranslucent);
        this.all = root.getChild("all");
        this.up = this.all.getChild("up");
        this.glass = this.up.getChild("glass");
        this.rside = this.glass.getChild("rside");
        this.lside = this.glass.getChild("lside");
        this.lsidebars = this.glass.getChild("lsidebars");
        this.base = this.up.getChild("base");
        this.light_li = this.base.getChild("light_li");
        this.logo_li = this.base.getChild("logo_li");
        this.lwheel = this.base.getChild("lwheel");
        this.rwheel = this.base.getChild("rwheel");
        this.middle = this.all.getChild("middle");
        this.bottom = this.all.getChild("bottom");
        this.opening = AbilityDeveloperAnimation.OPENING.bake(root);
        this.closing = AbilityDeveloperAnimation.CLOSING.bake(root);
        this.lyingDown = AbilityDeveloperAnimation.LYING_DOWN.bake(root);
        this.standing = AbilityDeveloperAnimation.STANDING.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition up = all.addOrReplaceChild("up", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -22.074F, -2.4654F, 1.0472F, 0.0F, 0.0F));

        PartDefinition glass = up.addOrReplaceChild("glass", CubeListBuilder.create(), PartPose.offset(0.0F, 9.174F, -0.5346F));

        PartDefinition rside = glass.addOrReplaceChild("rside", CubeListBuilder.create().texOffs(0, 125).addBox(-2.3013F, 0.1297F, -18.0F, 0.0F, 5.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(5, 101).addBox(-11.1297F, -2.6987F, -18.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(11.1297F, -13.3013F, 1.0F));

        PartDefinition cube_r1 = rside.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 119).addBox(0.5F, -2.0F, -34.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.069F, -0.931F, 16.0F, 0.0F, 0.0F, -0.7854F));

        PartDefinition lside = glass.addOrReplaceChild("lside", CubeListBuilder.create().texOffs(24, 107).addBox(2.3013F, 0.1297F, -18.0F, 0.0F, 5.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(48, 79).addBox(5.1297F, -2.6987F, -18.0F, 6.0F, 0.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.1297F, -13.3013F, 1.0F));

        PartDefinition cube_r2 = lside.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 113).addBox(-0.5F, -2.0F, -34.0F, 0.0F, 4.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.069F, -0.931F, 16.0F, 0.0F, 0.0F, 0.7854F));

        PartDefinition lsidebars = glass.addOrReplaceChild("lsidebars", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition base = up.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 3.174F, -17.5346F, 16.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(154, 6).addBox(-4.0F, 2.174F, 8.4654F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(69, 39).addBox(-4.0F, -7.826F, -22.5346F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(4.0F, -3.826F, -19.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 25).addBox(-7.0F, -3.826F, -19.5346F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(69, 17).addBox(-7.0F, -6.826F, -21.5346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(84, 17).addBox(4.0F, -6.826F, -21.5346F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(86, 9).addBox(4.0F, -6.826F, 18.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(69, 0).addBox(-4.0F, -7.826F, 17.4654F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(69, 9).addBox(-8.0F, -6.826F, 18.4654F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(103, 24).addBox(4.0F, -3.826F, 18.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(110, 42).addBox(-4.0F, -5.826F, 17.4654F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(106, 65).addBox(-2.0F, -3.826F, 16.4654F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(42, 39).addBox(-2.0F, 5.174F, 9.4654F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(103, 24).addBox(-8.0F, -3.826F, 18.4654F, 4.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(126, 1).addBox(2.75F, -2.826F, 0.4654F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(65, 39).addBox(-10.0F, 1.174F, -15.5346F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(0, 64).addBox(-12.0F, 1.174F, -14.5346F, 2.0F, 3.0F, 33.0F, new CubeDeformation(0.0F))
                .texOffs(105, 66).addBox(8.0F, 1.174F, -15.5346F, 2.0F, 3.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(22, 102).addBox(6.0F, 0.174F, -17.5346F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(22, 102).mirror().addBox(-8.0F, 0.174F, -17.5346F, 2.0F, 3.0F, 36.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(105, 0).addBox(5.0F, 2.174F, -17.5346F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(105, 0).addBox(-6.0F, 2.174F, -17.5346F, 1.0F, 1.0F, 36.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r3 = base.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(174, 25).addBox(1.0F, -2.0F, -1.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(174, 19).addBox(6.0F, -2.0F, -1.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, 0.174F, -14.0346F, 0.3491F, 0.0F, 0.0F));

        PartDefinition cube_r4 = base.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(145, 24).addBox(-5.0F, -3.0F, -1.5F, 10.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.174F, -16.0346F, 0.3491F, 0.0F, 0.0F));

        PartDefinition cube_r5 = base.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(133, 7).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3F, 0.174F, 2.4654F, 0.0F, 0.0F, -0.0873F));

        PartDefinition cube_r6 = base.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(103, 9).addBox(-2.0F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(118, 9).addBox(14.9F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.95F, -0.826F, 19.9654F, -0.1833F, 0.0F, 0.0F));

        PartDefinition cube_r7 = base.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(42, 46).addBox(-5.0F, -4.0F, -1.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.6499F, 17.8644F, -0.4189F, 0.0F, 0.0F));

        PartDefinition cube_r8 = base.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(118, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.6F, -0.809F, -18.364F, 0.2769F, 0.2261F, 0.1332F));

        PartDefinition cube_r9 = base.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(103, 9).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.6F, -0.809F, -18.364F, 0.2769F, -0.2261F, -0.1332F));

        PartDefinition light_li = base.addOrReplaceChild("light_li", CubeListBuilder.create().texOffs(146, 138).addBox(-4.0F, -5.51F, 16.75F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(152, 130).addBox(4.0F, -4.51F, 17.75F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(107, 115).addBox(-12.0F, 3.49F, -15.25F, 2.0F, 3.0F, 33.0F, new CubeDeformation(0.0F))
                .texOffs(155, 124).addBox(-2.0F, -1.5F, 15.74F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.326F, 0.7154F));

        PartDefinition logo_li = base.addOrReplaceChild("logo_li", CubeListBuilder.create(), PartPose.offset(0.0F, -8.326F, -19.4346F));

        PartDefinition cube_r10 = logo_li.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(144, 3).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.0584F, 0.4558F, -0.664F));

        PartDefinition lwheel = base.addOrReplaceChild("lwheel", CubeListBuilder.create().texOffs(86, 61).addBox(-2.2F, -2.0F, -3.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(70, 61).addBox(-2.2F, -3.0F, -2.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(85, 61).addBox(-2.2F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.7F, 0.174F, -17.5346F));

        PartDefinition rwheel = base.addOrReplaceChild("rwheel", CubeListBuilder.create().texOffs(85, 61).addBox(0.2F, -2.0F, -3.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(70, 61).addBox(-0.8F, -3.0F, -2.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(85, 61).addBox(-0.8F, -2.0F, 2.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(8.7F, 0.174F, -17.5346F));

        PartDefinition middle = all.addOrReplaceChild("middle", CubeListBuilder.create().texOffs(114, 109).addBox(-1.0F, -26.0F, -2.9F, 2.0F, 27.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.15F, 15.0F, 0.1745F, 0.0F, 0.0F));

        PartDefinition bottom = all.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 218).addBox(-11.0F, 17.0F, -8.0F, 22.0F, 2.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(17, 178).addBox(-11.0F, 17.0F, -18.0F, 22.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(83, 208).addBox(-11.0F, 15.0F, -8.0F, 1.0F, 2.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(60, 170).addBox(-11.0F, 13.0F, 7.0F, 1.0F, 2.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(60, 170).addBox(10.0F, 13.0F, 7.0F, 1.0F, 2.0F, 23.0F, new CubeDeformation(0.0F))
                .texOffs(0, 180).addBox(10.0F, 11.0F, 17.0F, 1.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(0, 180).addBox(-11.0F, 11.0F, 17.0F, 1.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(2, 232).addBox(-11.0F, 15.0F, -18.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(-10.0F, 16.0F, -18.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(9.0F, 16.0F, -18.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(7.0F, 16.0F, -18.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(71, 158).addBox(-8.0F, 16.0F, -18.0F, 1.0F, 1.0F, 46.0F, new CubeDeformation(0.0F))
                .texOffs(2, 232).addBox(10.0F, 15.0F, -18.0F, 1.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(83, 208).addBox(10.0F, 15.0F, -8.0F, 1.0F, 2.0F, 37.0F, new CubeDeformation(0.0F))
                .texOffs(3, 219).addBox(-2.0F, 15.0F, 17.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -18.9F, -6.0F));

        PartDefinition cube_r11 = bottom.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(71, 210).addBox(-10.0F, -1.5F, -0.5F, 20.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 16.5772F, -17.8879F, 0.3927F, 0.0F, 0.0F));

        PartDefinition cube_r12 = bottom.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(9, 199).addBox(-12.0F, -3.0F, -0.5F, 22.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 13.963F, 29.3139F, -0.3927F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(AbilityDeveloperRenderState renderState) {
        super.setupAnim(renderState);
        opening.apply(renderState.openingState, renderState.ageInTicks);
        closing.apply(renderState.closingState, renderState.ageInTicks);
        standing.apply(renderState.standingState, renderState.ageInTicks);
        lyingDown.apply(renderState.lyingDownState, renderState.ageInTicks);
    }
}