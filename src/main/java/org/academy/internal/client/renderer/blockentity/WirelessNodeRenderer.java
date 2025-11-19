package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.model.WirelessNodeModel;
import org.academy.internal.client.renderer.blockentity.state.WirelessNodeRenderState;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;
import org.jspecify.annotations.Nullable;

public final class WirelessNodeRenderer implements BlockEntityRenderer<WirelessNodeBlockEntity, WirelessNodeRenderState> {
    public static final WirelessNodeRenderer INSTANCE = new WirelessNodeRenderer();
    public static final WirelessNodeModel MODEL = new WirelessNodeModel(WirelessNodeModel.createBodyLayer().bakeRoot());

    @Override
    public void submit(WirelessNodeRenderState wirelessNodeRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        MODEL.setupAnim(wirelessNodeRenderState);
        MODEL.render(poseStack, submitNodeCollector, wirelessNodeRenderState.lightCoords, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public WirelessNodeRenderState createRenderState() {
        return new WirelessNodeRenderState();
    }

    @Override
    public void extractRenderState(WirelessNodeBlockEntity blockEntity, WirelessNodeRenderState renderState, float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.ageInTicks = blockEntity.ticks + partialTick;
        renderState.coreState = blockEntity.coreState;
        renderState.connectedUsersCount = blockEntity.connectedUsersCount;
        renderState.maxConnectedUsers = blockEntity.maxConnectedUsers;
    }

    private WirelessNodeRenderer() {
    }
}