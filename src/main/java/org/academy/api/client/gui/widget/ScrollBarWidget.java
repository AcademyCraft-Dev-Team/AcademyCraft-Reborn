package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;

public class ScrollBarWidget extends DragBarWidget {
    public ScrollPanelWidget panel;

    public ScrollBarWidget(ScrollPanelWidget newPanel, float x, float y, float width, float height, Orientation orientation) {
        super(x, y, width, height, orientation);
        panel = newPanel;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var matrix = stack.lastMatrix();

        var absoluteAlpha = getAbsoluteAlpha();
        var finalTrackColor = (getTrackColor() & 0x00FFFFFF) | ((int) (((getTrackColor() >> 24) & 0xFF) * absoluteAlpha) << 24);
        var finalThumbColor = (getThumbColor() & 0x00FFFFFF) | ((int) (((getThumbColor() >> 24) & 0xFF) * absoluteAlpha) << 24);

        stack.translate(0, 0, getZ());

        if (showBackground) {
            RenderUtil.fill(stack, bufferSource, getX(), getY(), getX() + getWidth(), getY() + getHeight(), finalTrackColor);
            stack.translate(0, 0, 1);
        }

        var thumbStart = getThumbPosition();
        var thumbSize = getThumbSize();
        if (orientation == Orientation.HORIZONTAL) {
            RenderUtil.fill(stack, bufferSource, thumbStart, getY(), thumbStart + thumbSize, getY() + getHeight(), finalThumbColor);
        } else {
            RenderUtil.fill(stack, bufferSource, getX(), thumbStart, getX() + getWidth(), thumbStart + thumbSize, finalThumbColor);
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