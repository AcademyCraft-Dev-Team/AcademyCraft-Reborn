package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.model.WirelessNodeModel;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;

public class WirelessNodeBlockEntityRenderer implements BlockEntityRenderer<WirelessNodeBlockEntity> {
    public static final BlockEntityRenderer<WirelessNodeBlockEntity> INSTANCE = new WirelessNodeBlockEntityRenderer();
    public static final WirelessNodeModel WIRELESS_NODE_MODEL = new WirelessNodeModel(WirelessNodeModel.createBodyLayer().bakeRoot());

    @Override
    public void render(WirelessNodeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        WIRELESS_NODE_MODEL.setupAnim(blockEntity, partialTick);
        WIRELESS_NODE_MODEL.render(poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private WirelessNodeBlockEntityRenderer() {
    }
}