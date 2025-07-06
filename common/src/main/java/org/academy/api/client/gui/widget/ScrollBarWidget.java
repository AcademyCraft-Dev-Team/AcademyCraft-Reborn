package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;

public class ScrollBarWidget extends DragBarWidget {
    public ScrollPanelWidget panel;

    public ScrollBarWidget(ScrollPanelWidget newPanel, float x, float y, float width, float height, Orientation orientation) {
        super(x, y, width, height, orientation);
        panel = newPanel;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var matrix = graphics.pose().last().pose();
        var buffer = graphics.bufferSource();

        var absoluteAlpha = getAbsoluteAlpha();
        var finalTrackColor = (getTrackColor() & 0x00FFFFFF) | ((int) (((getTrackColor() >> 24) & 0xFF) * absoluteAlpha) << 24);
        var finalThumbColor = (getThumbColor() & 0x00FFFFFF) | ((int) (((getThumbColor() >> 24) & 0xFF) * absoluteAlpha) << 24);

        matrix.translate(0, 0, getZ());

        if (showBackground) {
            RenderUtil.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), finalTrackColor, buffer);
            matrix.translate(0, 0, 1);
        }

        var thumbStart = getThumbPosition();
        var thumbSize = getThumbSize();
        if (orientation == Orientation.HORIZONTAL) {
            RenderUtil.fill(matrix, thumbStart, getY(), thumbStart + thumbSize, getY() + getHeight(), finalThumbColor, buffer);
        } else {
            RenderUtil.fill(matrix, getX(), thumbStart, getX() + getWidth(), thumbStart + thumbSize, finalThumbColor, buffer);
        }
    }

    @Override
    protected float getThumbSize() {
        var max = panel.getMaxScroll();
        if (max <= 0f) {
            return getTrackSize();
        }
        var contentSize = max + (orientation == Orientation.HORIZONTAL ? panel.getWidth() : panel.getHeight());
        var ratio = (orientation == Orientation.HORIZONTAL ? panel.getWidth() : panel.getHeight()) / contentSize;
        return MathUtil.clamp(ratio * getTrackSize(), 16f, getTrackSize());
    }

    @Override
    protected float getThumbPosition() {
        var max = panel.getMaxScroll();
        if (max <= 0f) {
            return orientation == Orientation.HORIZONTAL ? getX() : getY();
        }
        var track = getTrackSize() - getThumbSize();
        var ratio = panel.scrollOffset / max;
        return (orientation == Orientation.HORIZONTAL ? getX() : getY()) + ratio * track;
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        var max = panel.getMaxScroll();
        if (max <= 0f) return;
        var track = getTrackSize() - getThumbSize();
        var ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);
        panel.scrollTarget = ratio * max;
    }
}