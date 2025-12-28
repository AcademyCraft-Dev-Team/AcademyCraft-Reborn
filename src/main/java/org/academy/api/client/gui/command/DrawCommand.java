package org.academy.api.client.gui.command;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformPayload;
import org.joml.Matrix4f;

import java.util.List;

public abstract class DrawCommand {
    protected final RenderPipeline pipeline;
    protected final List<TextureBinding> textures;
    protected final List<UniformPayload<?>> uniforms;

    protected DrawCommand(
            RenderPipeline pipeline,
            List<TextureBinding> textures,
            List<UniformPayload<?>> uniforms
    ) {
        validatePipelineMode(pipeline);
        this.pipeline = pipeline;
        this.textures = textures;
        this.uniforms = uniforms;
    }

    private static void validatePipelineMode(RenderPipeline pipeline) {
        var mode = pipeline.getVertexFormatMode();
        if (mode.connectedPrimitives)
            throw new IllegalArgumentException(
                    "Connected primitive modes (e.g., TRIANGLE_STRIP, LINE_STRIP) are forbidden in DrawCommands. "
                            + "To ensure correct batching, please generate independent triangles using TRIANGLES or QUADS mode."
            );
    }

    public final RenderPipeline getPipeline() {
        return pipeline;
    }

    public final List<TextureBinding> getTextures() {
        return textures;
    }

    public final List<UniformPayload<?>> getUniforms() {
        return uniforms;
    }

    public abstract void generateVertices(VertexConsumer consumer, Matrix4f pose);
}