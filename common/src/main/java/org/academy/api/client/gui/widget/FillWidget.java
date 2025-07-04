package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.RenderUtil;

public class FillWidget extends AbstractWidget {
    public int color;

    public FillWidget(float x, float y, float width, float height, int color) {
        super(x, y, width, height);
        this.color = color;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        int baseAlpha = FastColor.ARGB32.alpha(color);
        int finalAlpha = (int)(baseAlpha * getAbsoluteAlpha());
        int finalColor = (color & 0x00FFFFFF) | (finalAlpha << 24);

        RenderUtil.fill(graphics.pose().last().pose(), getX(), getY(),
                getX() + getWidth(), getY() + getHeight(), finalColor, graphics.bufferSource());
    }
}