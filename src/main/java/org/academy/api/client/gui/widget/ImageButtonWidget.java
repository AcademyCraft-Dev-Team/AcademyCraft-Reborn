package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.Nullable;

public class ImageButtonWidget extends AbstractButtonWidget {
    public float u0 = 0;
    public float v0 = 0;
    public float u1 = 1;
    public float v1 = 1;
    public float red;
    public float green;
    public float blue;
    public RenderType renderType;
    public float widthScale = 1.0f;
    public float heightScale = 1.0f;
    public boolean centerScale = true;
    public boolean defaultHoverEffect = false;
    public boolean previousHoveredState = false;

    public ImageButtonWidget(float x, float y, float width, float height,
                             @Nullable RenderType newRenderType, Runnable onPress) {
        super(x, y, width, height, onPress);
        renderType = newRenderType;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || renderType == null) return;

        stack.pushPose();
        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;
        var renderX = getX();
        var renderY = getY();

        if (centerScale) {
            renderX += (getWidth() - scaledWidth) / 2f;
            renderY += (getHeight() - scaledHeight) / 2f;
        }

        var finalAlpha = getAbsoluteAlpha();

        RenderUtil.blitWithRenderType(stack, bufferSource, renderType, renderX, renderY, scaledWidth, scaledHeight, u0, v0, u1, v1, red, green, blue, finalAlpha);

        stack.popPose();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        var pre = new ChangeHoverEffectEvent.Pre(this);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) return;

        if (isHovered() != previousHoveredState) {
            if (defaultHoverEffect) {
                if (isHovered()) {
                    red = 1.0F;
                    green = 1.0F;
                    blue = 1.0F;
                } else {
                    red = 0.75F;
                    green = 0.75F;
                    blue = 0.75F;
                }
            }
        }

        var post = new ChangeHoverEffectEvent.Post(this);
        NeoForge.EVENT_BUS.post(post);

        previousHoveredState = isHovered();
    }

    public static abstract class ChangeHoverEffectEvent extends Event implements ICancellableEvent {
        public final ImageButtonWidget button;

        public ChangeHoverEffectEvent(ImageButtonWidget newButton) {
            button = newButton;
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