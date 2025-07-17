package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.Nullable;

public class ImageWidget extends AbstractWidget {
    public float u0 = 0;
    public float v0 = 0;
    public float u1 = 1;
    public float v1 = 1;
    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;
    public RenderType renderType;
    public float widthScale = 1.0f;
    public float heightScale = 1.0f;
    public boolean centerScale = true;

    public ImageWidget(float x, float y, float width, float height, @Nullable RenderType newRenderType) {
        super(x, y, width, height);
        renderType = newRenderType;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;
        if (renderType == null) return;
        var vertexConsumer = bufferSource.getBuffer(renderType);

        stack.pushPose();
        var matrix4f = stack.lastMatrix();

        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;

        stack.translate(getX(), getY(), getZ());
        if (centerScale) {
            stack.translate((getWidth() - scaledWidth) / 2f, (getHeight() - scaledHeight) / 2f, 0);
        }

        stack.scale(scaledWidth, scaledHeight, 1);

        var finalAlpha = getAbsoluteAlpha();
        vertexConsumer.vertex(matrix4f, 0, 0, 0).color(red, green, blue, finalAlpha).uv(u0, v0).endVertex();
        vertexConsumer.vertex(matrix4f, 0, 1, 0).color(red, green, blue, finalAlpha).uv(u0, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 1, 0).color(red, green, blue, finalAlpha).uv(u1, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 0, 0).color(red, green, blue, finalAlpha).uv(u1, v0).endVertex();

        stack.popPose();
    }
}