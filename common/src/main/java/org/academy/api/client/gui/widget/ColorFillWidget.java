package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.RenderUtil;

public class ColorFillWidget extends AbstractWidget {
    public int color;

    public ColorFillWidget(float x, float y, float width, float height, int color) {
        super(x, y, width, height);
        this.color = color;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        RenderUtil.fill(graphics.pose().last().pose(), getX(), getY(), getX() + getWidth(), getY() + getHeight(), color, graphics.bufferSource());
    }
}