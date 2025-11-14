package org.academy.api.client.gui.drawable;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.Widget;
import org.jetbrains.annotations.Nullable;

public class TextureDrawable implements Drawable {
    @Nullable
    private final ResourceLocation textureLocation;
    @Nullable
    private transient GpuTextureView textureView;

    private int tintColor = 0xFFFFFFFF;

    public TextureDrawable(@Nullable ResourceLocation textureLocation) {
        this.textureLocation = textureLocation;
        this.textureView = null;
    }

    public TextureDrawable(@Nullable GpuTextureView textureView) {
        this.textureLocation = null;
        this.textureView = textureView;
    }

    @Override
    public void draw(RenderContext context, Widget widget) {
        resolveAndPrepareTexture();
        if (textureView == null) return;

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

        var command = new ImageDrawCommand(textureView, paddedWidth, paddedHeight, 0, 0, 1, 1, r, g, b, finalAlpha);
        context.submit(command);

        context.pose().popPose();
    }

    private void resolveAndPrepareTexture() {
        if (textureView != null && !textureView.isClosed()) return;
        if (textureLocation == null) {
            textureView = null;
            return;
        }

        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureLocation);
            textureView = texture.getTextureView();
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Failed to resolve texture view for {}", textureLocation, e);
            textureView = null;
        }
    }

    public int getTintColor() {
        return tintColor;
    }

    public void setTintColor(int tintColor) {
        this.tintColor = tintColor;
    }
}