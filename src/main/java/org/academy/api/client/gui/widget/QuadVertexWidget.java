package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.Nullable;

public class QuadVertexWidget extends AbstractWidget {
    public final float[][] vertices = new float[4][5];
    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;
    public RenderType renderType;

    public QuadVertexWidget(float x, float y, float width, float height, @Nullable RenderType newRenderType) {
        super(x, y, width, height);
        this.renderType = newRenderType;
    }

    public void setVertex(int index, float x, float y, float z, float u, float v) {
        if (index >= 0 && index < 4) {
            vertices[index][0] = x;
            vertices[index][1] = y;
            vertices[index][2] = z;
            vertices[index][3] = u;
            vertices[index][4] = v;
        }
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || renderType == null) return;

        var vertexConsumer = bufferSource.getBuffer(renderType);
        var matrix = stack.lastMatrix();

        var finalAlpha = getAbsoluteAlpha();

        vertexConsumer.addVertex(matrix, vertices[0][0], vertices[0][1], vertices[0][2])
                .setColor(red, green, blue, finalAlpha)
                .setUv(vertices[0][3], vertices[0][4]);
        vertexConsumer.addVertex(matrix, vertices[1][0], vertices[1][1], vertices[1][2])
                .setColor(red, green, blue, finalAlpha)
                .setUv(vertices[1][3], vertices[1][4]);
        vertexConsumer.addVertex(matrix, vertices[2][0], vertices[2][1], vertices[2][2])
                .setColor(red, green, blue, finalAlpha)
                .setUv(vertices[2][3], vertices[2][4]);
        vertexConsumer.addVertex(matrix, vertices[3][0], vertices[3][1], vertices[3][2])
                .setColor(red, green, blue, finalAlpha)
                .setUv(vertices[3][3], vertices[3][4]);
    }
}