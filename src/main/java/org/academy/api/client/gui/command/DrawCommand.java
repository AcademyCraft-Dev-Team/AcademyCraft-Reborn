package org.academy.api.client.gui.command;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.Map;

public abstract class DrawCommand {
    protected final RenderPipeline pipeline;

    protected DrawCommand(RenderPipeline pipeline) {
        validatePipelineMode(pipeline);
        this.pipeline = pipeline;
    }

    private static void validatePipelineMode(RenderPipeline pipeline) {
        VertexFormat.Mode mode = pipeline.getVertexFormatMode();
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