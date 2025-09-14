package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Resource;
import org.academy.internal.client.model.WindGenTopModel;
import org.academy.internal.client.model.WindGenTurbineModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;

import static net.minecraft.client.renderer.RenderType.entityCutoutNoCull;

public class WindGenTopBlockEntityRenderer implements BlockEntityRenderer<WindGenTopBlockEntity> {
    public static final BlockEntityRenderer<WindGenTopBlockEntity> INSTANCE = new WindGenTopBlockEntityRenderer();
    public static final WindGenTopModel MODEL = new WindGenTopModel(WindGenTopModel.createBodyLayer().bakeRoot());

    private WindGenTopBlockEntityRenderer() {
    }

    @Override
    public void render(WindGenTopBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();

            var facing = blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            poseStack.translate(0.5f, 1.5f, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot + 180));
            poseStack.translate(0, 0, 0.25f);

            MODEL.render(poseStack, bufferSource, packedLight, packedOverlay);

            if (blockEntity.hasFan) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(180));
                poseStack.translate(0, -2.5f, 0.875f);

                poseStack.rotateAround(Axis.ZP.rotationDegrees((blockEntity.ticks + partialTick) * 5),0,1.6125f,0);

                WindGenTurbineModel.INSTANCE.all.translateAndRotate(poseStack);
                WindGenTurbineModel.INSTANCE.main.render(poseStack, bufferSource.getBuffer(entityCutoutNoCull(Resource.Textures.MODEL_WIND_GEN)), packedLight, packedOverlay);
                WindGenTurbineModel.INSTANCE.tip_li.render(poseStack, bufferSource.getBuffer(entityCutoutNoCull(Resource.Textures.MODEL_WIND_GEN)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }
}