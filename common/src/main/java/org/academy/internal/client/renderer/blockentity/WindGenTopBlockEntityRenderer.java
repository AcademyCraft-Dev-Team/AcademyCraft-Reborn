package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
    public void render(@NotNull WindGenTopBlockEntity newBlockEntity, float partialTick, @NotNull PoseStack newPoseStack, @NotNull MultiBufferSource newBuffer, int packedLight, int packedOverlay) {
        if (newBlockEntity.isMain()) {
            newPoseStack.pushPose();

            var facing = newBlockEntity.getBlockState().getValue(AbilityDeveloperBlock.FACING);
            var yRot = facing.getOpposite().toYRot();

            newPoseStack.translate(0.5f, 1.5f, 0.5f);
            newPoseStack.mulPose(Axis.XP.rotationDegrees(180));
            newPoseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90));

            MODEL.setupAnim(newBlockEntity, partialTick);
            MODEL.render(newPoseStack, newBuffer, packedLight, packedOverlay);

            if (newBlockEntity.hasFan) {
                newPoseStack.pushPose();
                newPoseStack.mulPose(Axis.XP.rotationDegrees(-180));
                newPoseStack.mulPose(Axis.YP.rotationDegrees(90));

                newPoseStack.translate(0, -0.85f, 1.25f);

                newPoseStack.mulPose(Axis.ZP.rotationDegrees((newBlockEntity.ticks + partialTick) * 5));
                var windGenTurbineModel = new WindGenTurbineModel(WindGenTurbineModel.createBodyLayer().bakeRoot());
                windGenTurbineModel.main.render(newPoseStack, newBuffer.getBuffer(windGenTurbineModel.renderType(TextureResources.TEXTURE_WIND_GEN_MODEL)), packedLight, packedOverlay);
                windGenTurbineModel.tip_li.render(newPoseStack, newBuffer.getBuffer(windGenTurbineModel.renderType(TextureResources.TEXTURE_WIND_GEN_MODEL)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                newPoseStack.popPose();
            }
            newPoseStack.popPose();
        }
    }
}