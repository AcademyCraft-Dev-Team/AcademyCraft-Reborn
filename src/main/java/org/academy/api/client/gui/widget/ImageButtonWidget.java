package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.drawable.StateListDrawable;
import org.academy.api.client.gui.drawable.TextureDrawable;
import org.academy.api.client.gui.drawable.WidgetState;
import org.academy.api.client.gui.event.OnClickListener;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class ImageButtonWidget extends AbstractButtonWidget {
    protected int intrinsicWidth = 0;
    protected int intrinsicHeight = 0;

    public ImageButtonWidget(@Nullable GpuTextureView textureView, @Nullable OnClickListener listener) {
        super(listener);
        var defaultDrawable = new TextureDrawable(textureView);
        defaultDrawable.setTintColor(0xFFBBBBBB);

        var hoveredDrawable = new TextureDrawable(textureView);
        hoveredDrawable.setTintColor(0xFFFFFFFF);

        var sld = new StateListDrawable();
        sld.addState(WidgetState.DEFAULT, defaultDrawable);
        sld.addState(WidgetState.HOVERED, hoveredDrawable);

        setBackground(sld);
    }

    public ImageButtonWidget(@Nullable ResourceLocation textureLocation, @Nullable OnClickListener listener) {
        super(listener);
        var defaultDrawable = new TextureDrawable(textureLocation);
        defaultDrawable.setTintColor(0xFFBBBBBB);

        var hoveredDrawable = new TextureDrawable(textureLocation);
        hoveredDrawable.setTintColor(0xFFFFFFFF);

        var sld = new StateListDrawable();
        sld.addState(WidgetState.DEFAULT, defaultDrawable);
        sld.addState(WidgetState.HOVERED, hoveredDrawable);

        setBackground(sld);
        resolveIntrinsicSize(textureLocation);
        setLayoutParams(new WidgetContainer.LayoutParams()
                .size(intrinsicWidth, intrinsicHeight)
                .sizeMode(SizeMode.WRAP_CONTENT));
    }

    private void resolveIntrinsicSize(@Nullable ResourceLocation textureLocation) {
        if (textureLocation == null) {
            intrinsicWidth = 0;
            intrinsicHeight = 0;
            return;
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(textureLocation);
            try (var inputStream = resource.open(); var nativeImage = NativeImage.read(inputStream)) {
                intrinsicWidth = nativeImage.getWidth();
                intrinsicHeight = nativeImage.getHeight();
            }
        } catch (IOException e) {
            AcademyCraft.LOGGER.error("Failed to resolve intrinsic size for texture {}", textureLocation, e);
            intrinsicWidth = 0;
            intrinsicHeight = 0;
        }
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var lp = getLayoutParams();
        var desiredWidth = (float) intrinsicWidth + lp.paddingLeft + lp.paddingRight;
        var desiredHeight = (float) intrinsicHeight + lp.paddingTop + lp.paddingBottom;

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    /**
     * @deprecated Use {@code setBackground(new StateListDrawable(...))} to define custom hover effects.
     */
    @Deprecated
    public ImageButtonWidget setColor(float r, float g, float b) {
        return this;
    }

    /**
     * @deprecated The hover effect is now controlled by the background {@link StateListDrawable}.
     */
    @Deprecated
    public ImageButtonWidget setDefaultHoverEffect(boolean enabled) {
        return this;
    }
}