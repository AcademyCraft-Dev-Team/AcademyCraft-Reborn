package org.academy.api.client.gui.command;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformPayload;
import org.joml.Matrix4f;

import java.util.List;

public class PosTexRectDrawCommand extends DrawCommand {
    protected final float width;
    protected final float height;
    protected final float u0;
    protected final float v0;
    protected final float u1;
    protected final float v1;

    public PosTexRectDrawCommand(
            RenderPipeline pipeline,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            List<TextureBinding> textures,
            List<UniformPayload<?>> uniforms
    ) {
        super(pipeline, textures, uniforms);
        this.width = width;
        this.height = height;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }

    @Override
    public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
        consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setUv(u0, v0);
        consumer.addVertex(pose, 0.0F, height, 0.0F).setUv(u0, v1);
        consumer.addVertex(pose, width, height, 0.0F).setUv(u1, v1);
        consumer.addVertex(pose, width, 0.0F, 0.0F).setUv(u1, v0);
    }
}