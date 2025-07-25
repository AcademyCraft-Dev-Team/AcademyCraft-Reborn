package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;

public class BackgroundWidget extends AbstractWidget {
    private final Screen screen;
    public Runnable runnable;

    public BackgroundWidget(@NotNull Screen newScreen) {
        super(0, 0, newScreen.width, newScreen.height);
        screen = newScreen;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        var graphics = new GuiGraphics(Minecraft.getInstance(), bufferSource);
        graphics.pose().last().pose().mul(stack.lastMatrix());
        screen.renderBackground(graphics, 0, 0, partialTick);
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