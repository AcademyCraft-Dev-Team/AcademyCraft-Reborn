package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.util.ClientUtil;
import org.lwjgl.glfw.GLFW;

public class ImageButtonWidget extends ImageWidget {
    public Runnable onPress;
    public boolean defaultHoverEffect = false;
    public boolean previousHoveredState = false;

    public ImageButtonWidget(float x, float y, float width, float height,
                             RenderType renderType, Runnable onPress) {
        super(x, y, width, height, renderType);
        this.onPress = onPress;
        red = 0.75F;
        green = 0.75F;
        blue = 0.75F;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hovered && button == GLFW.GLFW_RELEASE && isAbsoluteEnabled()) {
            ClientUtil.playDownSound();
            if (onPress != null) {
                onPress.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canFocus() {
        return this.enabled;
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