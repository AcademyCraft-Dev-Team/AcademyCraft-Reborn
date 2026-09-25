package org.academy.api.client.gui.util

import net.minecraft.client.gui.GuiGraphicsExtractor

fun border(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
    graphics.fill(x + 1, y, x + width - 1, y + 1, color)
    graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color)
    graphics.fill(x, y + 1, x + 1, y + height - 1, color)
    graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color)
}
