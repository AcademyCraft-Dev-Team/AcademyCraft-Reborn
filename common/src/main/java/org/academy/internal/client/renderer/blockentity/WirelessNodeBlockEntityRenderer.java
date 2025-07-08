package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.academy.internal.client.model.WirelessNodeModel;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;
import org.jetbrains.annotations.NotNull;

public class WirelessNodeBlockEntityRenderer implements BlockEntityRenderer<WirelessNodeBlockEntity> {
    public static final BlockEntityRenderer<WirelessNodeBlockEntity> INSTANCE = new WirelessNodeBlockEntityRenderer();
    public static final WirelessNodeModel WIRELESS_NODE_MODEL = new WirelessNodeModel(WirelessNodeModel.createBodyLayer().bakeRoot());

    private WirelessNodeBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull WirelessNodeBlockEntity wirelessNodeBlockEntity, float partialTick, PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        WIRELESS_NODE_MODEL.setupAnim(wirelessNodeBlockEntity, partialTick);
        WIRELESS_NODE_MODEL.render(poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}