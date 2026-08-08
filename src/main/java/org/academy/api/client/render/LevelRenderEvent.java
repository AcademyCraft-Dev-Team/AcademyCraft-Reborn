package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.bus.api.Event;
import org.academy.internal.client.renderer.effect.WorldLineOverlayPass;

import java.util.function.BiConsumer;

public class LevelRenderEvent extends Event {
    /**
     * 坐标系原点是相机位置, 而非世界原点
     */
    private final MatrixStack matrixStack;
    private final float partialTick;
    private final PoseStack poseStack;
    private final SubmitNodeCollector submitNodeCollector;

    public LevelRenderEvent(float partialTick, MatrixStack matrixStack, PoseStack poseStack,
                            SubmitNodeCollector submitNodeCollector) {
        this.matrixStack = matrixStack;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.submitNodeCollector = submitNodeCollector;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public float getPartialTick() {
        return partialTick;
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
}
