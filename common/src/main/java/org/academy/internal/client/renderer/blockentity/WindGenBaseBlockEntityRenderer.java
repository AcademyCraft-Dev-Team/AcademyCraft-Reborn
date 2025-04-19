package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.academy.internal.client.models.WindGenBaseModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class WindGenBaseBlockEntityRenderer implements BlockEntityRenderer<WindGenBaseBlockEntity> {
    public static final WindGenBaseModel MODEL = new WindGenBaseModel(WindGenBaseModel.createBodyLayer().bakeRoot());

    @Override
    public void render(@NotNull WindGenBaseBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        VertexConsumer vertexConsumer = buffer.getBuffer(MODEL.renderType(WindGenBaseModel.TEXTURE));
        poseStack.last().normal().rotateX((float) Math.toRadians(180));
        Matrix4f matrix4f = new Matrix4f();

        float yRot;
        switch (blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING)) {
            case NORTH -> yRot = 180;
            case EAST -> yRot = 270;
            case WEST -> yRot = 90;
            default -> yRot = 0;
        }
        matrix4f.rotateX((float) Math.toRadians(180));
        matrix4f.translate(0.5f, -1.5f, -0.5f);

        matrix4f.rotateY((float) Math.toRadians(yRot));

        poseStack.mulPoseMatrix(matrix4f);
        MODEL.setupAnim(blockEntity, partialTick);
        MODEL.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 1, 1, 1, 1);
        poseStack.popPose();
    }
}