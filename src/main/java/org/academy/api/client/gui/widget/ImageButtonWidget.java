package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.jetbrains.annotations.Nullable;

public class ImageButtonWidget extends AbstractButtonWidget {
    @Nullable
    protected ResourceLocation textureLocation;
    @Nullable
    protected transient GpuTextureView textureView;
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

    public ImageButtonWidget(float x, float y, float width, float height, @Nullable GpuTextureView textureView, Runnable onPress) {
        super(x, y, width, height, onPress);
        textureLocation = null;
        this.textureView = textureView;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
    }

    public ImageButtonWidget(float x, float y, float width, float height, @Nullable ResourceLocation textureLocation, Runnable onPress) {
        super(x, y, width, height, onPress);
        this.textureLocation = textureLocation;
        textureView = null;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
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
            if (filterMode != null && texture instanceof SimpleTexture simpleTexture) {
                simpleTexture.setFilter(useMipmap, filterMode == FilterMode.LINEAR);
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

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;

        context.pose().pushPose();
        {
            context.pose().translate(getX(), getY(), getZ());
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
        return this;
    }

    public ImageButtonWidget setTexture(@Nullable ResourceLocation textureLocation) {
        this.textureLocation = textureLocation;
        textureView = null;
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