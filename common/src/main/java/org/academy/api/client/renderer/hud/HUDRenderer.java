package org.academy.api.client.renderer.hud;

import net.minecraft.client.gui.GuiGraphics;

public interface HUDRenderer {
    void render(GuiGraphics guiGraphics, float partialTick);
}