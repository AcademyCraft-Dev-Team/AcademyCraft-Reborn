package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.internal.client.renderer.Shaders;

public class BlendQuadWidget extends AbstractWidget {
    public float marginTop = 4f;
    public float marginBottom = 4f;
    public float marginLeft = 4f;
    public float marginRight = 4f;
    public boolean drawLine = true;

    public float red;
    public float green;
    public float blue;

    public final ImageWidget lineWidget;

    public BlendQuadWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
        lineWidget = new ImageWidget(1, 0, width - 2, 4, RenderTypes.ELEMENT_LINE);
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        graphics.pose().pushPose();

        var matrix = graphics.pose().last().pose();
        matrix.translate(getX(), getY(), getZ());

        var w = getWidth();
        var h = getHeight();
        var finalAlpha = getAbsoluteAlpha();

        var shader = Shaders.sdfSharpQuadWithMarginShader;
        if (shader != null) {
            shader.safeGetUniform("u_size").set(w, h);
            shader.safeGetUniform("u_margins").set(marginLeft, marginTop, marginRight, marginBottom);
            shader.safeGetUniform("u_fillColor").set(red, green, blue, finalAlpha);

            var vertexConsumer = graphics.bufferSource().getBuffer(RenderTypes.SDF_SHARP_QUAD);
            vertexConsumer.vertex(matrix, 0, 0, 0).uv(0, 0).endVertex();
            vertexConsumer.vertex(matrix, 0, h, 0).uv(0, 1).endVertex();
            vertexConsumer.vertex(matrix, w, h, 0).uv(1, 1).endVertex();
            vertexConsumer.vertex(matrix, w, 0, 0).uv(1, 0).endVertex();
            graphics.bufferSource().endBatch(RenderTypes.SDF_SHARP_QUAD);
        }

        if (drawLine) {
            lineWidget.setAlpha(finalAlpha);
            lineWidget.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().translate(0, getHeight() - marginTop / 2, 0);
            lineWidget.render(graphics, mouseX, mouseY, partialTick);
        }

        graphics.pose().popPose();
    }
}