package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.RenderUtil;

public class FillWidget extends AbstractWidget {
    public int color;

    public FillWidget(float x, float y, float width, float height, int color) {
        super(x, y, width, height);
        this.color = color;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;
        if (animation != null) {
            animation.beforeRender(guiGraphics, mouseX, mouseY, partialTick);
        }

        RenderUtil.fill(guiGraphics.pose().last().pose(), getX(), getY(),
                getX() + getWidth(), getY() + getHeight(), color, guiGraphics.bufferSource());

        if (animation != null) {
            animation.afterRender(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}