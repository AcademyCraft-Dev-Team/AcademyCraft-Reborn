package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.jetbrains.annotations.Nullable;

public class ImageWidget extends AbstractWidget {
    @Nullable
    protected ResourceLocation textureLocation;
    @Nullable
    protected transient GpuTextureView textureView;
    @Nullable
    private FilterMode filterMode;
    private boolean useMipmap = false;
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

    public ImageWidget(@Nullable GpuTextureView textureView) {
        this.textureView = textureView;
        textureLocation = null;
    }

    public ImageWidget(@Nullable ResourceLocation textureLocation) {
        this.textureLocation = textureLocation;
        textureView = null;
    }

    public void resolveAndPrepareTexture() {
        if (textureView != null && !textureView.isClosed()) return;

        if (textureLocation == null) {
            textureView = null;
            return;
        }

        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureLocation);
            if (filterMode != null) {
                texture.setFilter(useMipmap, filterMode == FilterMode.LINEAR);
            }
            textureView = texture.getTextureView();
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Failed to resolve texture view for {}", textureLocation, e);
            textureView = null;
        }
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        resolveAndPrepareTexture();
        if (textureView == null) return;

        var lp = getLayoutParams();
        var paddedWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var paddedHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        if (paddedWidth <= 0 || paddedHeight <= 0) return;

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();
        var scaledWidth = paddedWidth * widthScale;
        var scaledHeight = paddedHeight * heightScale;

        context.pose().pushPose();
        {
            context.pose().translate(lp.paddingLeft, lp.paddingTop, 0);

            if (centerScale) {
                context.pose().translate((paddedWidth - scaledWidth) / 2.0F, (paddedHeight - scaledHeight) / 2.0F, 0.0F);
            }

            var command = new ImageDrawCommand(textureView, scaledWidth, scaledHeight, u0, v0, u1, v1, red, green, blue, finalAlpha);
            context.submit(command);
        }
        context.pose().popPose();
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

    public ImageWidget setTextureFilter(FilterMode mode, boolean useMipmap) {
        filterMode = mode;
        this.useMipmap = useMipmap;
        textureView = null;
        return this;
    }

    public ImageWidget setTexture(@Nullable GpuTextureView textureView) {
        this.textureView = textureView;
        textureLocation = null;
        requestLayout();
        return this;
    }

    public ImageWidget setTexture(@Nullable ResourceLocation textureLocation) {
        this.textureLocation = textureLocation;
        textureView = null;
        requestLayout();
        return this;
    }

    public ImageWidget setUv(float u0, float v0, float u1, float v1) {
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
        centerScale = center;
        return this;
    }
}