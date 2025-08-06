package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;

public class ScrollBarWidget extends DragBarWidget {
    protected final ScrollPanelWidget panel;

    public ScrollBarWidget(ScrollPanelWidget panel, float x, float y, float width, float height, Orientation orientation) {
        super(x, y, width, height, orientation);
        this.panel = panel;
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!this.isVisible()) return;

        stack.pushPose();

        if (this.showBackground) {
            int finalTrackColor = RenderUtil.applyAlpha(this.getTrackColor(), this.getAbsoluteAlpha());
            RenderUtil.fill(stack, bufferSource, 0, 0, this.getWidth(), this.getHeight(), finalTrackColor);
        }

        var thumbStart = this.getThumbPosition();
        var thumbSize = this.getThumbSize();
        int finalThumbColor = RenderUtil.applyAlpha(this.getThumbColor(), this.getAbsoluteAlpha());

        stack.translate(0, 0, 1);
        if (this.orientation == Orientation.HORIZONTAL) {
            RenderUtil.fill(stack, bufferSource, thumbStart, 0, thumbSize, this.getHeight(), finalThumbColor);
        } else {
            RenderUtil.fill(stack, bufferSource, 0, thumbStart, this.getWidth(), thumbSize, finalThumbColor);
        }

        stack.popPose();
    }

    @Override
    protected float getThumbSize() {
        var maxScroll = this.panel.getMaxScroll();
        if (maxScroll <= 0f) {
            return this.getTrackSize();
        }
        var viewSize = this.orientation == Orientation.HORIZONTAL ? this.panel.getWidth() : this.panel.getHeight();
        var contentSize = maxScroll + viewSize;
        var ratio = viewSize / contentSize;
        return MathUtil.clamp(ratio * this.getTrackSize(), 16f, this.getTrackSize());
    }

    @Override
    protected float getThumbPosition() {
        var maxScroll = this.panel.getMaxScroll();
        if (maxScroll <= 0f) {
            return 0;
        }
        var track = this.getTrackSize() - this.getThumbSize();
        var ratio = this.panel.getScrollY() / maxScroll;
        return ratio * track;
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        var maxScroll = this.panel.getMaxScroll();
        if (maxScroll <= 0f) return;

        var track = this.getTrackSize() - this.getThumbSize();
        if (track <= 0f) return;

        var ratio = MathUtil.clamp((mouse - this.dragOffset) / track, 0f, 1f);
        this.panel.setScrollTargetY(ratio * maxScroll);
    }
}