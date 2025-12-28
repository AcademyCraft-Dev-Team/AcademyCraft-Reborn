package org.academy.api.client.gui.drawable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.Widget;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TextureDrawable implements Drawable {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    @Nullable
    protected final Identifier textureLocation;
    @Nullable
    protected transient GpuTextureView texture;
    protected transient GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

    private int tintColor = 0xFFFFFFFF;

    public TextureDrawable(@Nullable Identifier textureLocation) {
        this.textureLocation = textureLocation;
        texture = null;
    }

    public TextureDrawable(@Nullable GpuTextureView texture) {
        textureLocation = null;
        this.texture = texture;
    }

    public void setSampler(GpuSampler sampler) {
        this.sampler = sampler;
    }

    @Override
    public void draw(RenderContext context, Widget widget) {
        resolveAndPrepareTexture();
        if (texture == null) return;

        var lp = widget.getLayoutParams();
        var paddedWidth = widget.getWidth() - lp.paddingLeft - lp.paddingRight;
        var paddedHeight = widget.getHeight() - lp.paddingTop - lp.paddingBottom;

        if (paddedWidth <= 0 || paddedHeight <= 0) return;

        var baseAlpha = ARGB.alpha(tintColor) / 255.0f;
        var finalAlpha = baseAlpha * widget.getAbsoluteAlpha();

        if (finalAlpha <= 0) return;

        var r = ARGB.red(tintColor) / 255.0f;
        var g = ARGB.green(tintColor) / 255.0f;
        var b = ARGB.blue(tintColor) / 255.0f;

        context.pose().pushPose();
        context.pose().translate(lp.paddingLeft, lp.paddingTop, 0);

        var command = new ImageDrawCommand(texture, sampler, paddedWidth, paddedHeight, 0, 0, 1, 1, r, g, b, finalAlpha);
        context.submit(command);

        context.pose().popPose();
    }

    private void resolveAndPrepareTexture() {
        if (texture != null && !texture.isClosed()) return;
        if (textureLocation == null) {
            texture = null;
            return;
        }

        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureLocation);
            this.texture = texture.getTextureView();
        } catch (Exception e) {
            LOGGER.error("Failed to resolve texture view for {}", textureLocation, e);
            texture = null;
        }
    }

    public int getTintColor() {
        return tintColor;
    }

    public void setTintColor(int tintColor) {
        this.tintColor = tintColor;
    }
}