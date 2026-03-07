package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Resource;
import org.academy.internal.client.model.WindGenTopModel;
import org.academy.internal.client.model.WindGenTurbineModel;
import org.academy.internal.client.renderer.blockentity.state.WindGenTopRenderState;
import org.academy.internal.common.world.level.block.entity.WindGenTopBlockEntity;
import org.jspecify.annotations.Nullable;

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
    public void extractRenderState(WindGenTopBlockEntity blockEntity, WindGenTopRenderState renderState, float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        renderState.hasFan = blockEntity.hasFan;
    }

    @Override
    public void submit(WindGenTopRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();

        var facing = renderState.facing;
        var yRot = facing.getOpposite().toYRot();
        var packedLight = renderState.lightCoords;
        var packedOverlay = OverlayTexture.NO_OVERLAY;

        poseStack.translate(0.5f, 1.5f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        nodeCollector.submitModel(MODEL, renderState, poseStack, RenderTypes.entityCutout(Resource.Textures.MODEL_WIND_GEN_TOP), packedLight, packedOverlay, 0, renderState.breakProgress);
        poseStack.pushPose();
        poseStack.translate(0, 1.5f, 0);
        poseStack.scale(1, 1f / 16f, 1);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        WindGenBaseRenderer.MODEL.renderPole(poseStack, nodeCollector, packedLight, packedOverlay);
        poseStack.popPose();

        if (renderState.hasFan) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.translate(0, -2.5f, -0.8f);

            poseStack.rotateAround(Axis.ZP.rotationDegrees(renderState.ageInTicks * 5), 0, 1.6125f, 0);

            WindGenTurbineModel.INSTANCE.render(poseStack, nodeCollector, packedLight, packedOverlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WindGenTopBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }
}