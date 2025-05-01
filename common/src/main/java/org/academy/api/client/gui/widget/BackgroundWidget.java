package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.jetbrains.annotations.NotNull;

public class BackgroundWidget extends AbstractWidget {
    private final Screen screen;
    public Runnable runnable;

    public BackgroundWidget(@NotNull Screen screen) {
        super(0, 0, screen.width, screen.height);
        this.screen = screen;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        screen.renderBackground(guiGraphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFocused() && button == 0) {
            runnable.run();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canFocus() {
        return isEnabled();
    }
}