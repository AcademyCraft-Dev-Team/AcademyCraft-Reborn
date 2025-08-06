package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImageButtonWidget extends AbstractButtonWidget {
    protected float u0 = 0;
    protected float v0 = 0;
    protected float u1 = 1;
    protected float v1 = 1;
    protected float red;
    protected float green;
    protected float blue;
    protected RenderType renderType;
    protected float widthScale = 1.0f;
    protected float heightScale = 1.0f;
    protected boolean centerScale = true;
    protected boolean defaultHoverEffect = false;

    public ImageButtonWidget(float x, float y, float width, float height,
                             @Nullable RenderType renderType, Runnable onPress) {
        super(x, y, width, height, onPress);
        this.renderType = renderType;
        this.red = 0.75F;
        this.green = 0.75F;
        this.blue = 0.75F;
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!this.isVisible() || this.renderType == null) return;

        float scaledWidth = this.getWidth() * this.widthScale;
        float scaledHeight = this.getHeight() * this.heightScale;
        float renderX = 0;
        float renderY = 0;

        if (this.centerScale) {
            renderX = (this.getWidth() - scaledWidth) / 2f;
            renderY = (this.getHeight() - scaledHeight) / 2f;
        }

        float finalAlpha = this.getAbsoluteAlpha();

        RenderUtil.blitWithRenderType(stack, bufferSource, this.renderType, renderX, renderY, scaledWidth, scaledHeight,
                this.u0, this.v0, this.u1, this.v1, this.red, this.green, this.blue, finalAlpha);

    }

    @Override
    public void setHovered(boolean hovered) {
        if (this.isHovered() == hovered) {
            return;
        }

        var pre = new ChangeHoverEffectEvent.Pre(this);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) {
            return;
        }

        super.setHovered(hovered);

        if (this.defaultHoverEffect) {
            if (hovered) {
                this.red = 1.0F;
                this.green = 1.0F;
                this.blue = 1.0F;
            } else {
                this.red = 0.75F;
                this.green = 0.75F;
                this.blue = 0.75F;
            }
        }

        var post = new ChangeHoverEffectEvent.Post(this);
        NeoForge.EVENT_BUS.post(post);
    }

    public RenderType getRenderType() {
        return this.renderType;
    }

    @NotNull
    public ImageButtonWidget setRenderType(@Nullable RenderType renderType) {
        this.renderType = renderType;
        return this;
    }

    @NotNull
    public ImageButtonWidget setUV(float u0, float v0, float u1, float v1) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        return this;
    }

    @NotNull
    public ImageButtonWidget setColor(float r, float g, float b) {
        this.red = r;
        this.green = g;
        this.blue = b;
        return this;
    }

    @NotNull
    public ImageButtonWidget setScale(float widthScale, float heightScale, boolean center) {
        this.widthScale = widthScale;
        this.heightScale = heightScale;
        this.centerScale = center;
        return this;
    }

    @NotNull
    public ImageButtonWidget setDefaultHoverEffect(boolean enabled) {
        this.defaultHoverEffect = enabled;
        this.setHovered(this.isHovered());
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