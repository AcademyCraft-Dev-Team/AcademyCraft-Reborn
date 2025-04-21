package org.academy.api.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.jetbrains.annotations.NotNull;

public class BackgroundWidget extends AbstractWidget {
    private final Screen screen;

    public BackgroundWidget(@NotNull Screen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        screen.renderBackground(guiGraphics);
    }
}