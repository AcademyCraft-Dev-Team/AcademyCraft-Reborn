package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.MatrixStack;
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
        if (!isVisible()) return;
        if (renderType == null) return;
        var vertexConsumer = bufferSource.getBuffer(renderType);

        stack.pushPose();
        var matrix4f = stack.lastMatrix();

        var scaledWidth = getWidth() * widthScale;
        var scaledHeight = getHeight() * heightScale;

        stack.translate(getX(), getY(), getZ());
        if (centerScale) {
            stack.translate((getWidth() - scaledWidth) / 2f, (getHeight() - scaledHeight) / 2f, 0);
        }

        stack.scale(scaledWidth, scaledHeight, 1);

        var finalAlpha = getAbsoluteAlpha();
        vertexConsumer.vertex(matrix4f, 0, 0, 0).color(red, green, blue, finalAlpha).uv(u0, v0).endVertex();
        vertexConsumer.vertex(matrix4f, 0, 1, 0).color(red, green, blue, finalAlpha).uv(u0, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 1, 0).color(red, green, blue, finalAlpha).uv(u1, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 0, 0).color(red, green, blue, finalAlpha).uv(u1, v0).endVertex();

        stack.popPose();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        var pre = new ChangeHoverEffectEvent.Pre(this);
        AcademyCraft.EVENT_BUS.post(pre);
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
        AcademyCraft.EVENT_BUS.post(post);

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