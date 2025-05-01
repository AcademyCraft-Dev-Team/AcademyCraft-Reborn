package org.academy.api.client.gui.animation;

import net.minecraft.client.gui.GuiGraphics;

public interface Animation {
    void afterRender(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick);
    void beforeRender(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick);
}