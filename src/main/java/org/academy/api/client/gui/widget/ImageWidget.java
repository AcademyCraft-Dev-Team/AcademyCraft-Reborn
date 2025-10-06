package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.WidgetRenderContext;

import javax.annotation.Nullable;

public class ImageWidget extends AbstractWidget {
    @Nullable
    protected GpuTextureView textureView;
    protected float u0 = 0.0F;
    protected float v0 = 0.0F;
    protected float u1 = 1.0F;
    protected float v1 = 1.0F;
    protected float red = 1.0F;
    protected float green = 1.0F;
    protected float blue = 1.0F;
    protected float widthScale = 1.0F;
    protected float heightScale = 1.0F;
    protected boolean centerScale = true;

    public ImageWidget(float x, float y, float width, float height, @Nullable GpuTextureView textureView) {
        super(x, y, width, height);
        this.textureView = textureView;
    }

    public ImageWidget(float x, float y, float width, float height, @Nullable ResourceLocation textureLocation) {
        super(x, y, width, height);
        this.textureView = resolveTexture(textureLocation);
    }

    @Nullable
    private static GpuTextureView resolveTexture(@Nullable ResourceLocation location) {
        if (location == null)
            return null;

        return Minecraft.getInstance().getTextureManager().getTexture(location).getTextureView();
    }

    @Override
    public void render(WidgetRenderContext renderContext, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || textureView == null)
            return;

        var finalAlpha = getAlpha() * renderContext.getAccumulatedAlpha();

        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;

        renderContext.pose().pushPose();
        {
            renderContext.pose().translate(getX(), getY(), getZ());
            if (centerScale) {
                renderContext.pose().translate((getWidth() - scaledWidth) / 2.0F, (getHeight() - scaledHeight) / 2.0F, 0.0F);
            }

            var command = new ImageDrawCommand(textureView, scaledWidth, scaledHeight, u0, v0, u1, v1, red, green, blue, finalAlpha);
            renderContext.submit(command);
        }
        renderContext.pose().popPose();
    }

    public float getRed() {
        return red;
    }

    public ImageWidget setRed(float red) {
        this.red = red;
        return this;
    }

    public float getGreen() {
        return green;
    }

    public ImageWidget setGreen(float green) {
        this.green = green;
        return this;
    }

    public float getBlue() {
        return blue;
    }

    public ImageWidget setBlue(float blue) {
        this.blue = blue;
        return this;
    }

    public ImageWidget setTexture(@Nullable GpuTextureView textureView) {
        this.textureView = textureView;
        return this;
    }

    public ImageWidget setTexture(@Nullable ResourceLocation textureLocation) {
        this.textureView = resolveTexture(textureLocation);
        return this;
    }

    public ImageWidget setTextureCoords(float u0, float v0, float u1, float v1) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        return this;
    }

    public ImageWidget setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        return this;
    }

    public ImageWidget setColor(int color) {
        red = ARGB.red(color) / 255.0F;
        green = ARGB.green(color) / 255.0F;
        blue = ARGB.blue(color) / 255.0F;
        return this;
    }

    public ImageWidget setWidthScale(float widthScale) {
        this.widthScale = widthScale;
        return this;
    }

    public ImageWidget setHeightScale(float heightScale) {
        this.heightScale = heightScale;
        return this;
    }

    public ImageWidget setCenterScale(boolean centerScale) {
        this.centerScale = centerScale;
        return this;
    }

    public ImageWidget setScale(float widthScale, float heightScale, boolean center) {
        this.widthScale = widthScale;
        this.heightScale = heightScale;
        this.centerScale = center;
        return this;
    }
}