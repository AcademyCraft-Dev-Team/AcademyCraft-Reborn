package org.academy.api.client.gui.command;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformPayload;
import org.joml.Matrix4f;

import java.util.List;

public abstract class PosColorRectDrawCommand extends DrawCommand {
    protected final float width;
    protected final float height;
    protected final float red;
    protected final float green;
    protected final float blue;
    protected final float alpha;

    protected PosColorRectDrawCommand(
            RenderPipeline pipeline,
            float width,
            float height,
            float red,
            float green,
            float blue,
            float alpha,
            List<TextureBinding> textures,
            List<UniformPayload<?>> uniforms
    ) {
        super(pipeline, textures, uniforms);
        this.width = width;
        this.height = height;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
        consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, 0.0F, height, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, width, height, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, width, 0.0F, 0.0F).setColor(red, green, blue, alpha);
    }
}