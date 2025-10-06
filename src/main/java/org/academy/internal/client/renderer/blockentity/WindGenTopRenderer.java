package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Resource;
import org.academy.internal.client.model.WindGenTopModel;
import org.academy.internal.client.model.WindGenTurbineModel;
import org.academy.internal.client.renderer.blockentity.state.WindGenTopRenderState;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.client.renderer.RenderType.entityCutoutNoCull;

public final class WindGenTopRenderer implements BlockEntityRenderer<WindGenTopBlockEntity, WindGenTopRenderState> {
    public static final WindGenTopRenderer INSTANCE = new WindGenTopRenderer();
    public static final WindGenTopModel MODEL = new WindGenTopModel(WindGenTopModel.createBodyLayer().bakeRoot());

    private WindGenTopRenderer() {
    }

    @Override
    public WindGenTopRenderState createRenderState() {
        return new WindGenTopRenderState();
    }

    @Override
    public void extractRenderState(WindGenTopBlockEntity blockEntity, WindGenTopRenderState renderState, float partialTick, Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        renderState.isMain = blockEntity.isMain();
        renderState.hasFan = blockEntity.hasFan;
    }

    @Override
    public void submit(WindGenTopRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.isMain) {
            poseStack.pushPose();

            var facing = renderState.facing;
            var yRot = facing.getOpposite().toYRot();
            var packedLight = renderState.lightCoords;
            var packedOverlay = OverlayTexture.NO_OVERLAY;

            poseStack.translate(0.5f, 1.5f, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot + 180));
            poseStack.translate(0, 0, 0.25f);

            nodeCollector.submitModel(MODEL, renderState, poseStack, RenderType.entityCutout(Resource.Textures.MODEL_WIND_GEN_TOP), packedLight, packedOverlay, 0, renderState.breakProgress);
            poseStack.pushPose();
            poseStack.translate(0, 1.5f, -0.25f);
            poseStack.scale(1, 1f / 16f, 1);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));

            WindGenBaseRenderer.MODEL.renderPole(poseStack, nodeCollector, packedLight, packedOverlay);
            poseStack.popPose();

            if (renderState.hasFan) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(180));
                poseStack.translate(0, -2.5f, 0.875f);

                poseStack.rotateAround(Axis.ZP.rotationDegrees(renderState.ageInTicks * 5), 0, 1.6125f, 0);

                WindGenTurbineModel.INSTANCE.all.translateAndRotate(poseStack);
                nodeCollector.submitCustomGeometry(poseStack, entityCutoutNoCull(Resource.Textures.MODEL_WIND_GEN),
                        (pose, consumer) -> {
                            WindGenTurbineModel.INSTANCE.main.render(poseStack, consumer, packedLight, packedOverlay);
                            WindGenTurbineModel.INSTANCE.tip_li.render(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        }
                );
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }
}