package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.internal.client.renderer.Shaders;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import static org.academy.api.client.render.RenderTypes.CIRCLE_GLOW;

/**
 * A special-case widget that renders a glow effect directly at the mouse's
 * current coordinates, ignoring its own (x, y) properties and not participating
 * in the standard layout hierarchy.
 */
public class CursorWidget extends AbstractWidget {
    public float radius = 0.25f;
    public float softness = 0.75f;

    public CursorWidget(float size) {
        super(0, 0, size, size);
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        /*
          This widget intentionally bypasses the standard layout model to render directly
          at the mouse position. The widget's own x/y fields can be used as a fixed offset.
         */
        float renderX = (float) mouseX - this.getWidth() / 2 + this.getX();
        float renderY = (float) mouseY - this.getHeight() / 2 + this.getY();

        var sdfShader = Shaders.SDF_CIRCLE_GLOW;
        sdfShader.safeGetUniform("Radius").set(radius);
        sdfShader.safeGetUniform("Softness").set(softness);

        Matrix4f matrix = stack.lastMatrix();

        sdfShader.safeGetUniform("Color").set(0.0f, 0.0f, 0.0f, 0.6f);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(CIRCLE_GLOW);
        addQuad(vertexConsumer, matrix, renderX, renderY, this.getWidth(), this.getHeight(), this.getZ());
        bufferSource.endBatch(CIRCLE_GLOW);

        sdfShader.safeGetUniform("Color").set(1.0f, 1.0f, 1.0f, 1.0f);
        vertexConsumer = bufferSource.getBuffer(CIRCLE_GLOW);
        addQuad(vertexConsumer, matrix, renderX, renderY, this.getWidth(), this.getHeight(), this.getZ());
        bufferSource.endBatch(CIRCLE_GLOW);
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height, float z) {
        float x2 = x + width;
        float y2 = y + height;
        consumer.addVertex(matrix, x, y2, z).setUv(0, 1);
        consumer.addVertex(matrix, x2, y2, z).setUv(1, 1);
        consumer.addVertex(matrix, x2, y, z).setUv(1, 0);
        consumer.addVertex(matrix, x, y, z).setUv(0, 0);
    }
}