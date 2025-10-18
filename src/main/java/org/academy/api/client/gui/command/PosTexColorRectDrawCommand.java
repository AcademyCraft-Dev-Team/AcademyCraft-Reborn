package org.academy.api.client.gui.command;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public abstract class PosTexColorRectDrawCommand extends DrawCommand {
    protected final float width;
    protected final float height;
    protected final float u0;
    protected final float v0;
    protected final float u1;
    protected final float v1;
    protected final float red;
    protected final float green;
    protected final float blue;
    protected final float alpha;

    protected PosTexColorRectDrawCommand(
            RenderPipeline pipeline,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        super(pipeline);
        this.width = width;
        this.height = height;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
        consumer.addVertex(pose, 0.0F, 0.0F, 0.0F).setUv(u0, v0).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, 0.0F, height, 0.0F).setUv(u0, v1).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, width, height, 0.0F).setUv(u1, v1).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, width, 0.0F, 0.0F).setUv(u1, v0).setColor(red, green, blue, alpha);
    }
}