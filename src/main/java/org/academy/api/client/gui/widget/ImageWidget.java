package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ImageWidget extends AbstractWidget {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    
    @Nullable
    protected Identifier textureIdentifier;
    @Nullable
    protected transient GpuTextureView textureView;
    private transient GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    protected float u0 = 0.0F;
    protected float v0 = 0.0F;
    protected float u1 = 1.0F;
    protected float v1 = 1.0F;
    protected float red = 1.0F;
    protected float green = 1.0F;
    protected float blue = 1.0F;

    public ImageWidget() {
    }

    public ImageWidget(@Nullable GpuTextureView textureView) {
        this.textureView = textureView;
        textureIdentifier = null;
    }

    public ImageWidget(@Nullable Identifier textureIdentifier) {
        this.textureIdentifier = textureIdentifier;
        textureView = null;
    }

    public void resolveAndPrepareTexture() {
        if (textureView != null && !textureView.isClosed()) return;

        if (textureIdentifier == null) {
            textureView = null;
            return;
        }

        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureIdentifier);
            textureView = texture.getTextureView();
        } catch (Exception e) {
            LOGGER.error("Failed to resolve texture view for {}", textureIdentifier, e);
            textureView = null;
        }
    }

    @Override
    protected void renderInternal(RenderContext context) {
        super.renderInternal(context);
        resolveAndPrepareTexture();
        if (textureView == null) return;

        var lp = getLayoutParams();
        var paddedWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var paddedHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        if (paddedWidth <= 0 || paddedHeight <= 0) return;

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        context.pose().pushPose();
        {
            context.pose().translate(lp.paddingLeft, lp.paddingTop, 0);

            var command = generateDrawCommand(textureView, sampler, paddedWidth, paddedHeight, u0, v0, u1, v1, red, green, blue, finalAlpha);
            context.submit(command);
        }
        context.pose().popPose();
    }

    protected DrawCommand generateDrawCommand(GpuTextureView texture, GpuSampler sampler,
                                              float width, float height,
                                              float u0, float v0, float u1, float v1,
                                              float red, float green, float blue, float alpha
    ) {
        return new ImageDrawCommand(
                texture, sampler, width, height, u0, v0, u1, v1, red, green, blue, alpha
        );
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

    public ImageWidget setSampler(FilterMode mode, boolean useMipmap) {
        return setSampler(RenderSystem.getSamplerCache().getClampToEdge(mode, useMipmap));
    }

    public ImageWidget setSampler(GpuSampler sampler) {
        this.sampler = sampler;
        return this;
    }

    public ImageWidget setTexture(@Nullable GpuTextureView textureView) {
        this.textureView = textureView;
        textureIdentifier = null;
        requestLayout();
        return this;
    }

    public ImageWidget setTexture(@Nullable Identifier textureLocation) {
        textureIdentifier = textureLocation;
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

    public float getBrightness() {
        return red;
    }

    public ImageWidget setBrightness(float val) {
        red = val;
        green = val;
        blue = val;
        return this;
    }
}