package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuadVertexWidget extends AbstractWidget {
    protected final float[][] vertices = new float[4][5];
    protected float red = 1f;
    protected float green = 1f;
    protected float blue = 1f;
    protected RenderType renderType;

    public QuadVertexWidget(float x, float y, float width, float height, @Nullable RenderType renderType) {
        super(x, y, width, height);
        this.renderType = renderType;
        this.resetVerticesToBounds();
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

    public final void resetVerticesToBounds() {
        float w = this.getWidth();
        float h = this.getHeight();
        this.setVertex(0, 0, 0, 0, 0, 0);
        this.setVertex(1, 0, h, 0, 0, 1);
        this.setVertex(2, w, h, 0, 1, 1);
        this.setVertex(3, w, 0, 0, 1, 0);
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || this.renderType == null) return;

        var vertexConsumer = bufferSource.getBuffer(this.renderType);
        var matrix = stack.lastMatrix();
        var finalAlpha = getAbsoluteAlpha();

        for (int i = 0; i < 4; i++) {
            vertexConsumer.addVertex(matrix, vertices[i][0], vertices[i][1], vertices[i][2])
                    .setColor(this.red, this.green, this.blue, finalAlpha)
                    .setUv(vertices[i][3], vertices[i][4]);
        }
    }

    @NotNull
    public QuadVertexWidget setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        return this;
    }

    @NotNull
    public QuadVertexWidget setRenderType(@Nullable RenderType renderType) {
        this.renderType = renderType;
        return this;
    }
}