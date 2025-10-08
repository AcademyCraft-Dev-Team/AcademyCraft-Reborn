package org.academy.api.client.gui.command;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * 看情况选择内部类或外部类喵, 独占选择内部类喵, 可复用选择外部类喵
 */
public abstract class DrawCommand {
    protected final RenderPipeline pipeline;

    protected DrawCommand(RenderPipeline pipeline) {
        validatePipelineMode(pipeline);
        this.pipeline = pipeline;
    }

    private static void validatePipelineMode(RenderPipeline pipeline) {
        var mode = pipeline.getVertexFormatMode();
        if (mode.connectedPrimitives)
            throw new IllegalArgumentException(
                    "Connected primitive modes (e.g., TRIANGLE_STRIP, LINE_STRIP) are forbidden in DrawCommands. "
                            + "To ensure correct batching, please generate independent triangles using TRIANGLES or QUADS mode."
            );
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    public abstract void generateVertices(VertexConsumer consumer, Matrix4f pose);

    public abstract Map<String, GpuTextureView> getSamplers();

    public abstract Map<String, GpuBufferSlice> getUniforms();
}