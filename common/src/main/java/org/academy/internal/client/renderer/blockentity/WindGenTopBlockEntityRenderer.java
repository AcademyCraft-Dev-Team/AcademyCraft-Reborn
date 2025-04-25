package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.internal.client.models.WindGenBaseModel;
import org.academy.internal.client.models.WindGenTopModel;
import org.academy.internal.client.models.WindGenTurbineModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class WindGenTopBlockEntityRenderer implements BlockEntityRenderer<WindGenTopBlockEntity> {
    public static final WindGenTopModel MODEL = new WindGenTopModel(WindGenTopModel.createBodyLayer().bakeRoot());

    @Override
    public void render(@NotNull WindGenTopBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.last().normal().rotateX((float) Math.toRadians(180));
        Matrix4f matrix4f = new Matrix4f();

        float yRot;
        switch (blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING)) {
            case NORTH -> yRot = 270;
            case EAST -> yRot = 0;
            case WEST -> yRot = 180;
            default -> yRot = -270;
        }
        matrix4f.rotateX((float) Math.toRadians(180));
        matrix4f.translate(0.5f, -1.3745f, -0.5f);

        matrix4f.rotateY((float) Math.toRadians(yRot));

        poseStack.mulPoseMatrix(matrix4f);
        MODEL.setupAnim(blockEntity, partialTick);
        MODEL.render(poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.pushPose();
        WindGenTurbineModel windGenTurbineModel = new WindGenTurbineModel(WindGenTurbineModel.createBodyLayer().bakeRoot());
        matrix4f.rotateY((float) Math.toRadians(90));
        matrix4f.translate(0.1f, 0.75f, 1.2f);
        matrix4f.rotateZ((float) Math.toRadians(180 * partialTick));
        poseStack.mulPoseMatrix(matrix4f);
        windGenTurbineModel.main.render(poseStack, buffer.getBuffer(windGenTurbineModel.renderType(WindGenBaseModel.TEXTURE)), packedLight, packedOverlay);
        windGenTurbineModel.tip_li.render(poseStack, buffer.getBuffer(windGenTurbineModel.renderType(WindGenBaseModel.TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}