package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.client.renderer.blockentity.state.WindGenBaseRenderState;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jspecify.annotations.Nullable;

public final class WindGenBaseRenderer implements BlockEntityRenderer<WindGenBaseBlockEntity, WindGenBaseRenderState> {
    public static final WindGenBaseRenderer INSTANCE = new WindGenBaseRenderer();
    public static final WindGenBaseModel MODEL = new WindGenBaseModel(WindGenBaseModel.createBodyLayer().bakeRoot());

    private WindGenBaseRenderer() {
    }

    @Override
    public WindGenBaseRenderState createRenderState() {
        return new WindGenBaseRenderState();
    }

    @Override
    public void extractRenderState(WindGenBaseBlockEntity blockEntity, WindGenBaseRenderState renderState, float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        renderState.isMain = blockEntity.isMain();
        renderState.setupState = blockEntity.setupState;
        renderState.shutdownState = blockEntity.shutdownState;
    }

    @Override
    public void submit(WindGenBaseRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        if (renderState.isMain) {
            var facing = renderState.facing;
            var yRot = facing.getOpposite().toYRot();

            poseStack.pushPose();
            poseStack.translate(0.5f, 0, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

            MODEL.render(poseStack, renderState, nodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY);

            poseStack.popPose();
        } else {
            poseStack.translate(0.5f, 0, 0.5f);
            MODEL.renderPole(poseStack, nodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WindGenBaseBlockEntity blockEntity) {
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