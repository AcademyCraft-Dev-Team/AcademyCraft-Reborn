package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.internal.client.renderer.Shaders;
import org.joml.Matrix4f;

public class BlendQuadWidget extends AbstractWidget {
    public float marginTop = 4f;
    public float marginBottom = 4f;
    public float marginLeft = 4f;
    public float marginRight = 4f;
    public boolean drawLine = true;

    public float red = 1.0f;
    public float green = 1.0f;
    public float blue = 1.0f;
    public float alpha = 0.75f;

    public final ImageWidget lineWidget;

    public BlendQuadWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
        lineWidget = new ImageWidget(0, 0, width, 4, RenderTypes.RENDER_TYPE_ELEMENT_LINE);
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (animation != null) animation.beforeRender(guiGraphics, mouseX, mouseY, partialTick);
        if (!isVisible()) return;

        guiGraphics.pose().pushPose();

        Matrix4f matrix = guiGraphics.pose().last().pose();
        matrix.translate(getX(), getY(), getZ());

        float w = getWidth(), h = getHeight();

        ShaderInstance shader = Shaders.sdfSharpQuadWithMarginShader;
        if (shader != null) {
            shader.safeGetUniform("u_size").set(w, h);
            shader.safeGetUniform("u_margins").set(marginLeft, marginTop, marginRight, marginBottom);
            shader.safeGetUniform("u_fillColor").set(this.red, this.green, this.blue, this.alpha);

            VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(RenderTypes.RENDER_TYPE_SDF_SHARP_QUAD);
            vertexConsumer.vertex(matrix, 0, 0, 0).uv(0, 0).endVertex();
            vertexConsumer.vertex(matrix, 0, h, 0).uv(0, 1).endVertex();
            vertexConsumer.vertex(matrix, w, h, 0).uv(1, 1).endVertex();
            vertexConsumer.vertex(matrix, w, 0, 0).uv(1, 0).endVertex();
            guiGraphics.bufferSource().endBatch(RenderTypes.RENDER_TYPE_SDF_SHARP_QUAD);
        }

        if (drawLine) {
            lineWidget.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().translate(0, getHeight() - marginTop / 2, 0);
            lineWidget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.pose().popPose();

        if (animation != null) animation.afterRender(guiGraphics, mouseX, mouseY, partialTick);
    }
}