package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.internal.client.renderer.Shaders;

import static org.academy.api.client.render.RenderTypes.CIRCLE_GLOW;

public class CursorWidget extends AbstractWidget {
    public float radius = 0.25f;
    public float softness = 0.75f;

    public CursorWidget(float width, float height) {
        super(0, 0, width, height);
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        setX((float) mouseX - getWidth() / 2);
        setY((float) mouseY - getHeight() / 2);

        renderSDFGlowAndShadow(stack, bufferSource);
    }

    private void renderSDFGlowAndShadow(MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        var renderType = CIRCLE_GLOW;
        var sdfShader = Shaders.SDF_CIRCLE_GLOW;

        sdfShader.safeGetUniform("Radius").set(radius);
        sdfShader.safeGetUniform("Softness").set(softness);

        var x = getX();
        var y = getY();
        var w = getWidth();
        var h = getHeight();
        var z = getZ();
        var matrix4f = stack.lastMatrix();

        sdfShader.safeGetUniform("Color").set(0.0f, 0.0f, 0.0f, 0.6f);
        var vertexConsumer = bufferSource.getBuffer(renderType);
        vertexConsumer.vertex(matrix4f, x, y + h, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(matrix4f, x + w, y + h, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(matrix4f, x + w, y, z).uv(1, 0).endVertex();
        vertexConsumer.vertex(matrix4f, x, y, z).uv(0, 0).endVertex();
        bufferSource.endBatch(renderType);

        var glowMatrix = stack.lastMatrix();
        sdfShader.safeGetUniform("Color").set(1.0f, 1.0f, 1.0f, 1.0f);
        vertexConsumer = bufferSource.getBuffer(renderType);
        vertexConsumer.vertex(glowMatrix, x, y + h, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(glowMatrix, x + w, y + h, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(glowMatrix, x + w, y, z).uv(1, 0).endVertex();
        vertexConsumer.vertex(glowMatrix, x, y, z).uv(0, 0).endVertex();
        bufferSource.endBatch(renderType);
    }
}