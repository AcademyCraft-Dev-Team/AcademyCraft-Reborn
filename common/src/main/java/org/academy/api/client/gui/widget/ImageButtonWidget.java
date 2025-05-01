package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;

public class ImageButtonWidget extends ImageWidget {
    public Runnable onPress;
    public boolean hoverEffect = true;

    public ImageButtonWidget(float x, float y, float width, float height,
                             RenderType renderType, Runnable onPress) {
        super(x, y, width, height, renderType);
        this.onPress = onPress;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (focused && button == 0) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            if (onPress != null) {
                onPress.run();
            }
            return true;
        }
        return false;
    }

    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(AcademyCraftSoundEvents.SELECT, 1.0F));
    }

    @Override
    public boolean canFocus() {
        return this.enabled;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        if (hoverEffect) {
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
    }
}