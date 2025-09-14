package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.level.block.entity.WindGenPillarBlockEntity;

public class WindGenPillarBlockEntityRenderer implements BlockEntityRenderer<WindGenPillarBlockEntity> {
    public static final BlockEntityRenderer<WindGenPillarBlockEntity> INSTANCE = new WindGenPillarBlockEntityRenderer();

    @Override
    public void render(WindGenPillarBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        WindGenBaseBlockEntityRenderer.MODEL.renderPole(poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private WindGenPillarBlockEntityRenderer() {
    }
}