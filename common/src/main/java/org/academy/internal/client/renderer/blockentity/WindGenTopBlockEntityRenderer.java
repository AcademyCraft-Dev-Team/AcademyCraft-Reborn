package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.academy.api.client.resource.TextureResources;
import org.academy.internal.client.model.WindGenTopModel;
import org.academy.internal.client.model.WindGenTurbineModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.NotNull;

public class WindGenTopBlockEntityRenderer implements BlockEntityRenderer<WindGenTopBlockEntity> {
    public static final BlockEntityRenderer<WindGenTopBlockEntity> INSTANCE = new WindGenTopBlockEntityRenderer();
    public static final WindGenTopModel MODEL = new WindGenTopModel(WindGenTopModel.createBodyLayer().bakeRoot());

    private WindGenTopBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull WindGenTopBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isMain()) {
            poseStack.pushPose();

            Direction facing = blockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            float yRot = facing.getOpposite().toYRot();

            poseStack.translate(0.5f, 1.5f, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90));

            MODEL.setupAnim(blockEntity, partialTick);
            MODEL.render(poseStack, buffer, packedLight, packedOverlay);

            if (blockEntity.hasFan) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(-180));
                poseStack.mulPose(Axis.YP.rotationDegrees(90));

                poseStack.translate(0, -0.85f, 1.25f);

                poseStack.mulPose(Axis.ZP.rotationDegrees((blockEntity.ticks + partialTick) * 5));
                WindGenTurbineModel windGenTurbineModel = new WindGenTurbineModel(WindGenTurbineModel.createBodyLayer().bakeRoot());
                windGenTurbineModel.main.render(poseStack, buffer.getBuffer(windGenTurbineModel.renderType(TextureResources.TEXTURE_WIND_GEN_MODEL)), packedLight, packedOverlay);
                windGenTurbineModel.tip_li.render(poseStack, buffer.getBuffer(windGenTurbineModel.renderType(TextureResources.TEXTURE_WIND_GEN_MODEL)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }
}