package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.internal.client.renderer.Shaders;

import static org.academy.api.client.renderer.RenderTypes.CIRCLE_GLOW;

public class CursorWidget extends AbstractWidget {
    public float radius = 0.25f;
    public float softness = 0.75f;

    public CursorWidget(float width, float height) {
        super(0, 0, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        setX((float) mouseX - getWidth() / 2);
        setY((float) mouseY - getHeight() / 2);

        renderSDFGlowAndShadow(graphics);
    }

    private void renderSDFGlowAndShadow(GuiGraphics guiGraphics) {
        var renderType = CIRCLE_GLOW;
        var sdfShader = Shaders.sdfCircleGlowShader;
        if (sdfShader == null) return;

        sdfShader.safeGetUniform("Radius").set(radius);
        sdfShader.safeGetUniform("Softness").set(softness);

        var x = getX();
        var y = getY();
        var w = getWidth();
        var h = getHeight();
        var z = getZ();
        var matrix4f = guiGraphics.pose().last().pose();

        sdfShader.safeGetUniform("Color").set(0.0f, 0.0f, 0.0f, 0.6f);
        var vertexConsumer = guiGraphics.bufferSource().getBuffer(renderType);
        vertexConsumer.vertex(matrix4f, x, y + h, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(matrix4f, x + w, y + h, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(matrix4f, x + w, y, z).uv(1, 0).endVertex();
        vertexConsumer.vertex(matrix4f, x, y, z).uv(0, 0).endVertex();
        guiGraphics.bufferSource().endBatch(renderType);

        var glowMatrix = guiGraphics.pose().last().pose();
        sdfShader.safeGetUniform("Color").set(1.0f, 1.0f, 1.0f, 1.0f);
        vertexConsumer = guiGraphics.bufferSource().getBuffer(renderType);
        vertexConsumer.vertex(glowMatrix, x, y + h, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(glowMatrix, x + w, y + h, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(glowMatrix, x + w, y, z).uv(1, 0).endVertex();
        vertexConsumer.vertex(glowMatrix, x, y, z).uv(0, 0).endVertex();
        guiGraphics.bufferSource().endBatch(renderType);
    }
}