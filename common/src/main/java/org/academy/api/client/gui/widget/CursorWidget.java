package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.internal.client.renderer.Shaders;
import org.joml.Matrix4f;

import static org.academy.api.client.renderer.RenderTypes.RENDER_TYPE_CIRCLE_GLOW;

public class CursorWidget extends AbstractWidget {
    public float radius = 0.5f;
    public float softness = 0.5f;

    public CursorWidget(float width, float height) {
        super(0, 0, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        setX((float) mouseX - getWidth() / 2);
        setY((float) mouseY - getHeight() / 2);

        renderSDFGlowAndShadow(guiGraphics);
    }

    private void renderSDFGlowAndShadow(GuiGraphics guiGraphics) {
        RenderType renderType = RENDER_TYPE_CIRCLE_GLOW;
        ShaderInstance sdfShader = Shaders.sdfCircleGlowShader;
        if (sdfShader == null) return;

        sdfShader.safeGetUniform("Radius").set(this.radius);
        sdfShader.safeGetUniform("Softness").set(this.softness);

        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float z = getZ();
        Matrix4f matrix4f = guiGraphics.pose().last().pose();

        sdfShader.safeGetUniform("Color").set(0.0f, 0.0f, 0.0f, 0.4f);
        VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(renderType);
        vertexConsumer.vertex(matrix4f, x, y + h, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(matrix4f, x + w, y + h, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(matrix4f, x + w, y, z).uv(1, 0).endVertex();
        vertexConsumer.vertex(matrix4f, x, y, z).uv(0, 0).endVertex();
        guiGraphics.bufferSource().endBatch(renderType);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + w / 2.0f, y + h / 2.0f, 0);
        guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
        guiGraphics.pose().translate(-(x + w / 2.0f), -(y + h / 2.0f), 0);

        Matrix4f glowMatrix = guiGraphics.pose().last().pose();
        sdfShader.safeGetUniform("Color").set(1.0f, 1.0f, 1.0f, 0.6f);
        vertexConsumer = guiGraphics.bufferSource().getBuffer(renderType);
        vertexConsumer.vertex(glowMatrix, x, y + h, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(glowMatrix, x + w, y + h, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(glowMatrix, x + w, y, z).uv(1, 0).endVertex();
        vertexConsumer.vertex(glowMatrix, x, y, z).uv(0, 0).endVertex();
        guiGraphics.bufferSource().endBatch(renderType);
        guiGraphics.pose().popPose();
    }
}