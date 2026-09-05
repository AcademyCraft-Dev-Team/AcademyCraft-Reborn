package org.academy.internal.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.academy.internal.client.renderer.entity.state.MisakaSisterRenderState;

public final class MisakaSisterModel extends EntityModel<MisakaSisterRenderState> {
    private static final float DEG = (float) Math.PI / 180F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public MisakaSisterModel(ModelPart root) {
        super(root);
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var part = mesh.getRoot();
        part.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create().texOffs(40, 20).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 10.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create().texOffs(40, 20).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 10.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F)
        );
        part.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MisakaSisterRenderState state) {
        clearLimbPose();
        head.yRot = state.yRot * DEG;
        head.xRot = state.xRot * DEG;

        if (state.carried) {
            applyCarriedPose();
        }
    }

    private void clearLimbPose() {
        body.setPos(0.0F, 0.0F, 0.0F);
        body.xRot = 0.0F;
        body.yRot = 0.0F;
        body.zRot = 0.0F;

        head.setPos(0.0F, 0.0F, 0.0F);
        head.zRot = 0.0F;

        leftArm.setPos(5.0F, 2.0F, 0.0F);
        leftArm.xRot = 0.0F;
        leftArm.yRot = 0.0F;
        leftArm.zRot = 0.0F;

        rightArm.setPos(-5.0F, 2.0F, 0.0F);
        rightArm.xRot = 0.0F;
        rightArm.yRot = 0.0F;
        rightArm.zRot = 0.0F;

        leftLeg.setPos(2.0F, 12.0F, 0.0F);
        leftLeg.xRot = 0.0F;
        leftLeg.yRot = 0.0F;
        leftLeg.zRot = 0.0F;

        rightLeg.setPos(-2.0F, 12.0F, 0.0F);
        rightLeg.xRot = 0.0F;
        rightLeg.yRot = 0.0F;
        rightLeg.zRot = 0.0F;
    }

    /**
     * Static carry silhouette: curled toward the holder, legs tucked, arms inward.
     * Combined with a front-chest attachment point so it reads as hugged, not seated.
     */
    private void applyCarriedPose() {
        body.y = 6.0F;
        body.xRot = 0.55F;
        body.zRot = 0.12F;

        head.y = 6.0F;
        head.xRot = Math.max(head.xRot, 0.25F) + 0.15F;
        head.zRot = -0.08F;

        leftArm.y = 8.0F;
        leftArm.xRot = -1.15F;
        leftArm.zRot = 0.55F;

        rightArm.y = 8.0F;
        rightArm.xRot = -1.05F;
        rightArm.zRot = -0.65F;

        leftLeg.y = 16.0F;
        leftLeg.z = -1.5F;
        leftLeg.xRot = -1.45F;
        leftLeg.zRot = 0.25F;

        rightLeg.y = 16.0F;
        rightLeg.z = -1.5F;
        rightLeg.xRot = -1.55F;
        rightLeg.zRot = -0.2F;
    }
}
