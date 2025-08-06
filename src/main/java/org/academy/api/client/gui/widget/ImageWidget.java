package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImageWidget extends AbstractWidget {
    protected float u0 = 0.0f;
    protected float v0 = 0.0f;
    protected float u1 = 1.0f;
    protected float v1 = 1.0f;

    protected float red = 1.0f;
    protected float green = 1.0f;
    protected float blue = 1.0f;

    protected RenderType renderType;
    protected float widthScale = 1.0f;
    protected float heightScale = 1.0f;

    protected boolean centerScale = true;

    public ImageWidget(float x, float y, float width, float height, @Nullable RenderType renderType) {
        super(x, y, width, height);
        this.renderType = renderType;
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || this.renderType == null) return;

        float scaledWidth = this.getWidth() * this.widthScale;
        float scaledHeight = this.getHeight() * this.heightScale;
        float renderX = 0;
        float renderY = 0;

        if (this.centerScale) {
            renderX = (this.getWidth() - scaledWidth) / 2.0f;
            renderY = (this.getHeight() - scaledHeight) / 2.0f;
        }

        float finalAlpha = getAbsoluteAlpha();

        RenderUtil.blitWithRenderType(stack, bufferSource, this.renderType, renderX, renderY, scaledWidth, scaledHeight,
                this.u0, this.v0, this.u1, this.v1, this.red, this.green, this.blue, finalAlpha);
    }

    @NotNull
    public RenderType getRenderType() {
        return this.renderType;
    }

    @NotNull
    public ImageWidget setTextureCoords(float u0, float v0, float u1, float v1) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        return this;
    }

    @NotNull
    public ImageWidget setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        return this;
    }

    @NotNull
    public ImageWidget setColor(int color) {
        this.red = ((color >> 16) & 0xFF) / 255.0f;
        this.green = ((color >> 8) & 0xFF) / 255.0f;
        this.blue = (color & 0xFF) / 255.0f;
        return this;
    }

    @NotNull
    public ImageWidget setRenderType(@Nullable RenderType renderType) {
        this.renderType = renderType;
        return this;
    }

    @NotNull
    public ImageWidget setWidthScale(float widthScale) {
        this.widthScale = widthScale;
        return this;
    }

    @NotNull
    public ImageWidget setHeightScale(float heightScale) {
        this.heightScale = heightScale;
        return this;
    }

    @NotNull
    public ImageWidget setCenterScale(boolean centerScale) {
        this.centerScale = centerScale;
        return this;
    }

    @NotNull
    public ImageWidget setScale(float widthScale, float heightScale, boolean center) {
        this.widthScale = widthScale;
        this.heightScale = heightScale;
        this.centerScale = center;
        return this;
    }
}