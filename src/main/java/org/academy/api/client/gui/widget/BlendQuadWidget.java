package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.academy.internal.client.renderer.Shaders;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class BlendQuadWidget extends AbstractWidget {
    public float marginTop = 4f;
    public float marginBottom = 4f;
    public float marginLeft = 4f;
    public float marginRight = 4f;
    public boolean drawLine = true;

    public float red;
    public float green;
    public float blue;

    public BlendQuadWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        Matrix4f matrix = stack.lastMatrix();

        float w = this.getWidth();
        float h = this.getHeight();
        float finalAlpha = this.getAbsoluteAlpha();

        if (finalAlpha < 1.0f / 255.0f) {
            return;
        }

        var shader = Shaders.SDF_SHARP_QUAD_WITH_MARGIN;
        shader.safeGetUniform("u_size").set(w, h);
        shader.safeGetUniform("u_margins").set(marginLeft, marginTop, marginRight, marginBottom);
        shader.safeGetUniform("u_fillColor").set(red, green, blue, finalAlpha);

        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderTypes.SDF_SHARP_QUAD);
        addPositionTexQuad(quadConsumer, matrix, 0, 0, w, h);
        bufferSource.endBatch(RenderTypes.SDF_SHARP_QUAD);

        if (drawLine) {
            float bottomLineY = h - this.marginTop / 2.0f;
            float lineW = w - 2.0f;
            float lineH = 4.0f;

            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.ELEMENT_LINE);
            addPositionColorTexQuad(lineConsumer, matrix, 1, 0, lineW, lineH, 1f, 1f, 1f, finalAlpha);
            addPositionColorTexQuad(lineConsumer, matrix, 1, bottomLineY, lineW, lineH, 1f, 1f, 1f, finalAlpha);
            bufferSource.endBatch(RenderTypes.ELEMENT_LINE);
        }
    }

    private static void addPositionTexQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height) {
        float x2 = x + width;
        float y2 = y + height;
        consumer.addVertex(matrix, x, y, 0).setUv(0, 0);
        consumer.addVertex(matrix, x, y2, 0).setUv(0, 1);
        consumer.addVertex(matrix, x2, y2, 0).setUv(1, 1);
        consumer.addVertex(matrix, x2, y, 0).setUv(1, 0);
    }

    private static void addPositionColorTexQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height, float r, float g, float b, float a) {
        float x2 = x + width;
        float y2 = y + height;
        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setUv(0, 0);
        consumer.addVertex(matrix, x, y2, 0).setColor(r, g, b, a).setUv(0, 1);
        consumer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a).setUv(1, 1);
        consumer.addVertex(matrix, x2, y, 0).setColor(r, g, b, a).setUv(1, 0);
    }
}