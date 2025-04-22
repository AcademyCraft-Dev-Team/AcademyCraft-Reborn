package org.academy.api.client.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;

public class VerticalScrollBarWidget extends DragBarWidget {
    public SmoothScrollPanelWidget panel;

    public VerticalScrollBarWidget(SmoothScrollPanelWidget panel, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.panel = panel;
    }

    @Override
    protected float getThumbSize() {
        float contentHeight = panel.getMaxScroll() + panel.getHeight();
        if (contentHeight <= 0) return getHeight();
        float ratio = panel.getHeight() / contentHeight;
        return MathUtil.clamp(ratio * getHeight(), 16f, getHeight());
    }

    @Override
    protected float getThumbPosition() {
        float track = getHeight() - getThumbSize();
        float ratio = panel.scrollOffset / panel.getMaxScroll();
        return getY() + ratio * track;
    }

    @Override
    protected float getTrackSize() {
        return getHeight();
    }

    @Override
    protected float getMouseRelative(float mouseX, float mouseY) {
        return mouseY - getAbsoluteY();
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

        PoseStack poseStack = graphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource buffer = graphics.bufferSource();

        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        if (showBackground) {
            RenderUtil.GeneralRenderer.fill(matrix, x, y, x + w, y + h, 0xFF202020, buffer);
        }

        float thumbTop = getThumbPosition();
        float thumbHeight = getThumbSize();
        RenderUtil.GeneralRenderer.fill(matrix, x, thumbTop, x + w, thumbTop + thumbHeight, 0xFFAAAAAA, buffer);

        graphics.flush();
    }
}