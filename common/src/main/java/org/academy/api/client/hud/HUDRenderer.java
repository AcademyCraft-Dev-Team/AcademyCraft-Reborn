package org.academy.api.client.hud;

import net.minecraft.client.gui.GuiGraphics;

public interface HUDRenderer {
    void render(GuiGraphics guiGraphics, float partialTick);
}