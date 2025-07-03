package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;

public class HorizontalScrollBarWidget extends DragBarWidget {
    public ScrollPanelWidget panel;

    public HorizontalScrollBarWidget(ScrollPanelWidget panel, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.panel = panel;
    }

    @Override
    protected float getThumbSize() {
        var max = panel.getMaxScroll();
        if (max <= 0f) {
            return getWidth();
        }
        var contentWidth = max + panel.getWidth();
        var ratio = panel.getWidth() / contentWidth;
        return MathUtil.clamp(ratio * getWidth(), 16f, getWidth());
    }

    @Override
    protected float getThumbPosition() {
        var max = panel.getMaxScroll();
        if (max <= 0f) {
            return getX();
        }
        var track = getWidth() - getThumbSize();
        var ratio = panel.scrollOffset / max;
        return getX() + ratio * track;
    }

    @Override
    protected float getTrackSize() {
        return getWidth();
    }

    @Override
    protected float getMouseRelative(float mouseX, float mouseY) {
        return mouseX - getAbsoluteX();
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        var max = panel.getMaxScroll();
        if (max <= 0f) return;
        var track = getTrackSize() - getThumbSize();
        var ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);
        panel.scrollTarget = ratio * max;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var matrix = graphics.pose().last().pose();
        var buffer = graphics.bufferSource();

        if (showBackground) {
            RenderUtil.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), getTrackColor(), buffer);
        }

        var thumbLeft = getThumbPosition();
        var thumbWidth = getThumbSize();
        RenderUtil.fill(matrix, thumbLeft, getY(), thumbLeft + thumbWidth, getY() + getHeight(), getThumbColor(), buffer);

        graphics.flush();
    }
}