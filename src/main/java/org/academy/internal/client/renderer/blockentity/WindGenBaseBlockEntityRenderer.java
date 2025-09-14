package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;

public class WindGenBaseBlockEntityRenderer implements BlockEntityRenderer<WindGenBaseBlockEntity> {
    public static final BlockEntityRenderer<WindGenBaseBlockEntity> INSTANCE = new WindGenBaseBlockEntityRenderer();
    public static final WindGenBaseModel MODEL = new WindGenBaseModel(WindGenBaseModel.createBodyLayer().bakeRoot());

    @Override
    public void render(WindGenBaseBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        poseStack.pushPose();
        if (blockEntity.isMain()) {
            var facing = blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            poseStack.pushPose();
            poseStack.translate(0.5f, 1.5f, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

            MODEL.setupAnim(blockEntity, partialTick);
            MODEL.render(poseStack, bufferSource, packedLight, packedOverlay);

            if (blockEntity.windGenWorldGUI != null && blockEntity.isDisplayActive) {
                var width = 1f;
                var scale = width / WindGenWorldGUI.WIDTH;

                poseStack.pushPose();
                poseStack.translate(0, 0.3075, 0.625);
                poseStack.mulPose(Axis.XP.rotationDegrees(17.5f));

                var aabb = new AABB(-0.5, -5.0 / 16.0, -0.05, 0.5, 5.0 / 16.0, 0.05);
                LineBoxRenderer.renderWireframeBox(new MatrixStack().setFrom(poseStack.last()), bufferSource, aabb, 1f, 1f, 1f, 1f);

                poseStack.mulPose(Axis.XP.rotationDegrees(180));
                poseStack.translate(0, 0, -0.0575f);
                poseStack.scale(-scale, -scale, scale);
                poseStack.translate(-WindGenWorldGUI.WIDTH / 2, -WindGenWorldGUI.HEIGHT / 2, 0);

                var matrixStack = new MatrixStack();
                matrixStack.setFrom(poseStack.last());

                blockEntity.windGenWorldGUI.render(matrixStack, bufferSource, partialTick);

                poseStack.popPose();
            }
            poseStack.popPose();

        }
        poseStack.popPose();
    }

    private WindGenBaseBlockEntityRenderer() {
    }
}