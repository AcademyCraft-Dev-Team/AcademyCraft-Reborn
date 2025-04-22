package org.academy.api.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;

public class HorizontalScrollBarWidget extends DragBarWidget {
    public SmoothScrollPanelWidget panel;

    public HorizontalScrollBarWidget(SmoothScrollPanelWidget panel, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.panel = panel;
    }

    @Override
    protected float getThumbSize() {
        float contentWidth = panel.getMaxScroll() + panel.getWidth();
        if (contentWidth <= 0) return getWidth();
        float ratio = panel.getWidth() / contentWidth;
        return MathUtil.clamp(ratio * getWidth(), 16f, getWidth());
    }

    @Override
    protected float getThumbPosition() {
        float track = getWidth() - getThumbSize();
        float ratio = panel.scrollOffset / panel.getMaxScroll();
        return getAbsoluteX() + ratio * track;
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
        float track = getTrackSize() - getThumbSize();
        float ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);
        panel.scrollTarget = ratio * panel.getMaxScroll();
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        Matrix4f matrix = graphics.pose().last().pose();
        MultiBufferSource buffer = graphics.bufferSource();

        if (showBackground) {
            RenderUtil.GeneralRenderer.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF202020, buffer);
        }

        float thumbLeft = getThumbPosition();
        float thumbWidth = getThumbSize();
        RenderUtil.GeneralRenderer.fill(matrix, thumbLeft, getY(), thumbLeft + thumbWidth, getY() + getHeight(), 0xFFAAAAAA, buffer);

        graphics.flush();
    }
}