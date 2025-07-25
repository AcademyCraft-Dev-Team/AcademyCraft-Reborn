package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.level.block.entity.CatEngineBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class CatEngineBlockEntityRenderer implements BlockEntityRenderer<CatEngineBlockEntity> {
    public static final BlockEntityRenderer<CatEngineBlockEntity> INSTANCE = new CatEngineBlockEntityRenderer();

    public CatEngineBlockEntityRenderer() {
    }

    @Override
    public void render(@NotNull CatEngineBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        var vertexConsumer = buffer.getBuffer(RenderTypes.CAT_ENGINE);
        var sizeHalf = 0.5f;
        var f1 = blockEntity.rot - blockEntity.oRot;

        f1 %= MathUtil.TWO_PI;

        if (f1 >= (float) Math.PI) {
            f1 -= MathUtil.TWO_PI;
        }
        if (f1 < -(float) Math.PI) {
            f1 += MathUtil.TWO_PI;
        }
        float f2 = blockEntity.oRot + f1 * partialTick;
        poseStack.rotateAround(Axis.YN.rotation(f2), 0.5f, 0.5f, 0.5f);
        poseStack.rotateAround(Axis.YN.rotation(90), 0.5f, 0.5f, 0.5f);
        if (blockEntity.enable) {
            poseStack.rotateAround(Axis.XN.rotation(blockEntity.rH += 0.2F), 0.5f, 0.5f, 0.5f);
        }
        poseStack.translate(sizeHalf, sizeHalf, sizeHalf);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        var entry = poseStack.last();
        var matrix = entry.pose();
        var normalMatrix = entry.normal();

        var vector3f = normalMatrix.transform(new Vector3f(0, 1, 0));
        vertexConsumer.addVertex(matrix, -sizeHalf, 0, -sizeHalf).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
        vertexConsumer.addVertex(matrix, sizeHalf, 0, -sizeHalf).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
        vertexConsumer.addVertex(matrix, sizeHalf, 0, sizeHalf).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
        vertexConsumer.addVertex(matrix, -sizeHalf, 0, sizeHalf).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());

        poseStack.popPose();
    }
}