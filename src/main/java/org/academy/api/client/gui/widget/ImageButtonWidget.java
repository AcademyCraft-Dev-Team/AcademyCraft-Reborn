package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class ImageButtonWidget extends AbstractButtonWidget {
    @Nullable
    protected ResourceLocation textureLocation;
    @Nullable
    protected transient GpuTextureView textureView;
    private int intrinsicWidth = 0;
    private int intrinsicHeight = 0;
    @Nullable
    private FilterMode filterMode;
    private boolean useMipmap = false;
    protected float u0 = 0;
    protected float v0 = 0;
    protected float u1 = 1;
    protected float v1 = 1;
    protected float red;
    protected float green;
    protected float blue;
    protected float widthScale = 1.0f;
    protected float heightScale = 1.0f;
    protected boolean centerScale = true;
    protected boolean defaultHoverEffect = true;

    public ImageButtonWidget(@Nullable GpuTextureView textureView, Runnable onPress) {
        super(onPress);
        textureLocation = null;
        this.textureView = textureView;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
    }

    public ImageButtonWidget(@Nullable ResourceLocation textureLocation, Runnable onPress) {
        super(onPress);
        this.textureLocation = textureLocation;
        textureView = null;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
        resolveIntrinsicSize();
        setLayoutParams(new WidgetContainer.LayoutParams()
                .size(intrinsicWidth, intrinsicHeight)
                .widthMode(SizeMode.WRAP_CONTENT)
                .heightMode(SizeMode.WRAP_CONTENT));
    }

    private void resolveIntrinsicSize() {
        if (textureLocation == null) {
            intrinsicWidth = 0;
            intrinsicHeight = 0;
            return;
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(textureLocation);
            try (var inputStream = resource.open()) {
                var nativeImage = NativeImage.read(inputStream);
                intrinsicWidth = nativeImage.getWidth();
                intrinsicHeight = nativeImage.getHeight();
                nativeImage.close();
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

    public void resolveAndPrepareTexture() {
        if (textureView != null && !textureView.isClosed()) {
            return;
        }

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
    public void render(RenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        resolveAndPrepareTexture();

        if (textureView == null) return;

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;

        context.pose().pushPose();
        {
            if (centerScale) {
                context.pose().translate((getWidth() - scaledWidth) / 2.0f, (getHeight() - scaledHeight) / 2.0f, 0.0f);
            }

            var command = new ImageDrawCommand(
                    textureView, scaledWidth, scaledHeight, u0, v0, u1, v1, red, green, blue, finalAlpha
            );
            context.submit(command);
        }
        context.pose().popPose();
    }

    @Override
    public void setHovered(boolean hovered) {
        if (isHovered() == hovered)
            return;

        var pre = new ChangeHoverEffectEvent.Pre(this);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) return;

        super.setHovered(hovered);

        if (defaultHoverEffect) {
            if (hovered) {
                red = 1.0F;
                green = 1.0F;
                blue = 1.0F;
            } else {
                red = 0.75F;
                green = 0.75F;
                blue = 0.75F;
            }
        }

        var post = new ChangeHoverEffectEvent.Post(this);
        NeoForge.EVENT_BUS.post(post);
    }

    public ImageButtonWidget setTextureFilter(FilterMode mode, boolean useMipmap) {
        filterMode = mode;
        this.useMipmap = useMipmap;
        textureView = null;
        return this;
    }

    public ImageButtonWidget setTexture(@Nullable GpuTextureView textureView) {
        this.textureView = textureView;
        textureLocation = null;
        intrinsicWidth = 0;
        intrinsicHeight = 0;
        requestLayout();
        return this;
    }

    public ImageButtonWidget setTexture(@Nullable ResourceLocation textureLocation) {
        this.textureLocation = textureLocation;
        textureView = null;
        resolveIntrinsicSize();
        requestLayout();
        return this;
    }

    public ImageButtonWidget setUv(float u0, float v0, float u1, float v1) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        return this;
    }

    public ImageButtonWidget setColor(float r, float g, float b) {
        red = r;
        green = g;
        blue = b;
        return this;
    }

    public ImageButtonWidget setScale(float widthScale, float heightScale, boolean center) {
        this.widthScale = widthScale;
        this.heightScale = heightScale;
        centerScale = center;
        return this;
    }

    public ImageButtonWidget setDefaultHoverEffect(boolean enabled) {
        defaultHoverEffect = enabled;
        return this;
    }

    public static abstract class ChangeHoverEffectEvent extends Event implements ICancellableEvent {
        public final ImageButtonWidget button;

        public ChangeHoverEffectEvent(ImageButtonWidget button) {
            this.button = button;
        }

        public static final class Pre extends ChangeHoverEffectEvent {
            public Pre(ImageButtonWidget button) {
                super(button);
            }
        }

        public static final class Post extends ChangeHoverEffectEvent {
            public Post(ImageButtonWidget button) {
                super(button);
            }
        }
    }
}