package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
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
        if (!isVisible() || renderType == null) return;

        stack.pushPose();
        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;
        var renderX = getX();
        var renderY = getY();

        if (centerScale) {
            renderX += (getWidth() - scaledWidth) / 2f;
            renderY += (getHeight() - scaledHeight) / 2f;
        }

        var finalAlpha = getAbsoluteAlpha();

        RenderUtil.blitWithRenderType(stack, bufferSource, renderType, renderX, renderY, scaledWidth, scaledHeight, u0, v0, u1, v1, red, green, blue, finalAlpha);

        stack.popPose();
    }
}