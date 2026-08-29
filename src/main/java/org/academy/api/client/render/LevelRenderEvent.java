package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import org.academy.internal.client.render.vfx.WorldLineOverlayPass;

import java.util.function.BiConsumer;

public class LevelRenderEvent extends Event {
    /**
     * 坐标系原点是相机位置, 而非世界原点
     */
    private final MatrixStack matrixStack;
    private final float partialTick;
    private final PoseStack poseStack;
    private final SubmitNodeCollector submitNodeCollector;
    private final Vec3 cameraPosition;

    public LevelRenderEvent(float partialTick, MatrixStack matrixStack, PoseStack poseStack,
                            SubmitNodeCollector submitNodeCollector) {
        this(partialTick, matrixStack, poseStack, submitNodeCollector, Vec3.ZERO);
    }

    public LevelRenderEvent(float partialTick, MatrixStack matrixStack, PoseStack poseStack,
                            SubmitNodeCollector submitNodeCollector, Vec3 cameraPosition) {
        this.matrixStack = matrixStack;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.submitNodeCollector = submitNodeCollector;
        this.cameraPosition = cameraPosition;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public float getPartialTick() {
        return partialTick;
    }

    /** Exact world-space camera position captured for this render state. */
    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public void submitCustomGeometry(RenderType renderType,
                                     BiConsumer<MatrixStack, VertexConsumer> renderer) {
        var snapshot = matrixStack.copy();
        if (WorldLineOverlayPass.accepts(renderType)) {
            WorldLineOverlayPass.submit(poseStack, renderType, snapshot, renderer);
            return;
        }
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (_, vertexConsumer) -> renderer.accept(snapshot, vertexConsumer)
        );
    }

    public void submitPoseGeometry(RenderType renderType,
                                   SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (WorldLineOverlayPass.accepts(renderType)) {
            WorldLineOverlayPass.submitPose(poseStack, renderType, renderer);
            return;
        }
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, renderer);
    }
}
