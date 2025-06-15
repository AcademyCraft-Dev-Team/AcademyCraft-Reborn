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
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        screen.renderBackground(graphics);
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (isAbsoluteMouseOver(mouseX, mouseY) && button == 0) {
            if (runnable != null) {
                runnable.run();
            }
            return true;
        }
        return false;
    }
}