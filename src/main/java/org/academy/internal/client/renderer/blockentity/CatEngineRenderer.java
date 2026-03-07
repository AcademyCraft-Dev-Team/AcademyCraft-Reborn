package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.Render;
import org.academy.internal.client.renderer.blockentity.state.CatEngineRenderState;
import org.academy.internal.common.world.level.block.entity.CatEngineBlockEntity;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class CatEngineRenderer implements BlockEntityRenderer<CatEngineBlockEntity, CatEngineRenderState> {
    public static final CatEngineRenderer INSTANCE = new CatEngineRenderer();

    private CatEngineRenderer() {
    }

    @Override
    public CatEngineRenderState createRenderState() {
        return new CatEngineRenderState();
    }

    @Override
    public void extractRenderState(CatEngineBlockEntity blockEntity, CatEngineRenderState renderState, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        renderState.enable = blockEntity.enable;
        renderState.oRot = blockEntity.oRot;
        renderState.partialTick = partialTick;
        renderState.rH = blockEntity.rH;
        renderState.rot = blockEntity.rot;
    }

    @Override
    public void submit(CatEngineRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        var sizeHalf = 0.5f;
        var f1 = renderState.rot - renderState.oRot;

        f1 %= Mth.TWO_PI;

        if (f1 >= (float) Math.PI) {
            f1 -= Mth.TWO_PI;
        }
        if (f1 < -(float) Math.PI) {
            f1 += Mth.TWO_PI;
        }
        var f2 = renderState.oRot + f1 * renderState.partialTick;
        poseStack.rotateAround(Axis.YN.rotation(f2), 0.5f, 0.5f, 0.5f);
        poseStack.rotateAround(Axis.YN.rotation(90), 0.5f, 0.5f, 0.5f);
        if (renderState.enable) {
            poseStack.rotateAround(Axis.XN.rotation(renderState.rH += 0.2F), 0.5f, 0.5f, 0.5f);
        }
        poseStack.translate(sizeHalf, sizeHalf, sizeHalf);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        var packedLight = renderState.lightCoords;
        submitNodeCollector.submitCustomGeometry(poseStack, Render.RenderTypes.CAT_ENGINE, (pose, vertexConsumer) -> {
            var matrix = pose.pose();
            var normalMatrix = pose.normal();

            var vector3f = normalMatrix.transform(new Vector3f(0, 1, 0));
            vertexConsumer.addVertex(matrix, -sizeHalf, 0, -sizeHalf).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
            vertexConsumer.addVertex(matrix, sizeHalf, 0, -sizeHalf).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
            vertexConsumer.addVertex(matrix, sizeHalf, 0, sizeHalf).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
            vertexConsumer.addVertex(matrix, -sizeHalf, 0, sizeHalf).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(vector3f.x(), vector3f.y(), vector3f.z());
        });
        poseStack.popPose();
    }
}