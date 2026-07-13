package org.academy.internal.client.model;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.academy.internal.client.definitions.WindGenBaseAnimation;
import org.academy.internal.client.renderer.blockentity.state.WindGenBaseRenderState;

/**
 * @author MapleBadd
 */
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class WindGenBaseModel extends Model<WindGenBaseRenderState> {
    public static final WindGenBaseModel MODEL = new WindGenBaseModel(WindGenBaseModel.createBodyLayer().bakeRoot());
    private final KeyframeAnimation setup;
    private final KeyframeAnimation shut;

    public WindGenBaseModel(ModelPart root) {
        super(root, RenderTypes::entityCutoutCull);
        setup = WindGenBaseAnimation.SETUP.bake(root);
        shut = WindGenBaseAnimation.SHUT.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition base = all.addOrReplaceChild("base", CubeListBuilder.create().texOffs(11, 25).addBox(-7.5F, -16.0F, -7.5F, 15.0F, 3.0F, 15.0F, new CubeDeformation(0.0F))
                .texOffs(0, 45).addBox(-8.0F, -3.0F, -8.0F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(14, 0).addBox(-2.0F, -18.0F, 6.0F, 4.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        PartDefinition cube_r1 = base.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(28, 0).addBox(-3.0F, -3.4175F, 0.125F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.0F, 13.45F, -1.5708F, 0.0F, 0.0F));

        PartDefinition cube_r2 = base.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(15, 1).addBox(-2.0F, -3.4175F, 0.125F, 4.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.6F, 9.0F, -2.0944F, 0.0F, 0.0F));

        PartDefinition cube_r3 = base.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r4 = base.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, 0.1745F));

        PartDefinition cube_r5 = base.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, 7.0F, 0.1745F, 0.0F, -0.1745F));

        PartDefinition cube_r6 = base.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(43, 1).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 17.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -8.0F, -7.0F, -0.1745F, 0.0F, -0.1745F));

        PartDefinition rods = all.addOrReplaceChild("rods", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -16.6F, -7.0F, -3.1416F, 0.0F, 3.1416F));

        PartDefinition cube_r7 = rods.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 1).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.8727F, 0.0F, 0.0F));

        PartDefinition cube_r8 = rods.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(9, 1).addBox(-1.0F, -4.6F, 0.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 2.7F, 0.5F, -0.6109F, 0.0F, 0.0F));

        PartDefinition screen = all.addOrReplaceChild("screen", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -19.0F, -10.0F, -3.1416F, 0.0F, 3.1416F));

        PartDefinition cube_r9 = screen.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 10).addBox(1.0F, -13.0F, 0.8F, 8.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, 7.0F, 1.2F, 0.3054F, 0.0F, 0.0F));

        PartDefinition rhalf = screen.addOrReplaceChild("rhalf", CubeListBuilder.create(), PartPose.offset(-6.875F, -0.082F, 0.3826F));

        PartDefinition cube_r10 = rhalf.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(6, 24).addBox(3.0F, -13.0F, 0.8F, 5.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.125F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        PartDefinition lhalf = screen.addOrReplaceChild("lhalf", CubeListBuilder.create(), PartPose.offset(6.875F, -0.082F, 0.3826F));

        PartDefinition cube_r11 = lhalf.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(19, 10).addBox(4.0F, -13.0F, 0.8F, 5.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.875F, 7.082F, 0.8174F, 0.3054F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(WindGenBaseRenderState renderState) {
        super.setupAnim(renderState);
        setup.apply(renderState.setupState, renderState.ageInTicks);
        shut.apply(renderState.shutdownState, renderState.ageInTicks);
    }
}
