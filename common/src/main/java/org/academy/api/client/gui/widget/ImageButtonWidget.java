package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.AcademyCraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class ImageButtonWidget extends AbstractButtonWidget {
    public float u0 = 0;
    public float v0 = 0;
    public float u1 = 1;
    public float v1 = 1;
    public float red;
    public float green;
    public float blue;
    public float alpha = 1f;
    public RenderType renderType;
    public float widthScale = 1.0f;
    public float heightScale = 1.0f;
    public boolean centerScale = true;
    public boolean defaultHoverEffect = false;
    public boolean previousHoveredState = false;

    public ImageButtonWidget(float x, float y, float width, float height,
                             @Nullable RenderType renderType, Runnable onPress) {
        super(x, y, width, height, onPress);
        this.renderType = renderType;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (animation != null) {
            animation.beforeRender(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (!isVisible()) return;
        if (renderType == null) return;
        VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(renderType);

        guiGraphics.pose().pushPose();
        Matrix4f matrix4f = guiGraphics.pose().last().pose();

        float scaledWidth = getWidth() * widthScale;
        float scaledHeight = getHeight() * heightScale;

        matrix4f.translate(getX(), getY(), getZ());
        if (centerScale) {
            matrix4f.translate((getWidth() - scaledWidth) / 2f, (getHeight() - scaledHeight) / 2f, 0);
        }

        matrix4f.scale(scaledWidth, scaledHeight, 1);

        vertexConsumer.vertex(matrix4f, 0, 0, 0).color(red, green, blue, alpha).uv(u0, v0).endVertex();
        vertexConsumer.vertex(matrix4f, 0, 1, 0).color(red, green, blue, alpha).uv(u0, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 1, 0).color(red, green, blue, alpha).uv(u1, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 0, 0).color(red, green, blue, alpha).uv(u1, v0).endVertex();

        guiGraphics.pose().popPose();
        if (animation != null) {
            animation.afterRender(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        ChangeHoverEffectEvent.Pre pre = new ChangeHoverEffectEvent.Pre(this);
        AcademyCraft.EVENT_BUS.post(pre);
        if (pre.isCanceled()) return;

        if (isHovered() != this.previousHoveredState) {
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

        ChangeHoverEffectEvent.Post post = new ChangeHoverEffectEvent.Post(this);
        AcademyCraft.EVENT_BUS.post(post);

        this.previousHoveredState = isHovered();
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