package org.academy.api.client.gui.widget;

import net.minecraft.util.ARGB;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.academy.api.common.util.MathUtil;

public class ScrollBarWidget extends DragBarWidget {
    protected final ScrollPanelWidget panel;

    public ScrollBarWidget(ScrollPanelWidget panel, Orientation orientation) {
        super(orientation);
        this.panel = panel;
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var finalAlpha = getAbsoluteAlpha() * context.getAccumulatedAlpha();

        context.pose().pushPose();

        if (showBackground) renderTrack(context, finalAlpha);

        renderThumb(context, finalAlpha);

        context.pose().popPose();
    }

    private void renderTrack(WidgetRenderContext context, float finalAlpha) {
        var trackAlpha = ARGB.alpha(trackColor) / 255.0f * finalAlpha;
        var r = ARGB.red(trackColor) / 255.0f;
        var g = ARGB.green(trackColor) / 255.0f;
        var b = ARGB.blue(trackColor) / 255.0f;
        var trackCommand = new FillRectDrawCommand(getWidth(), getHeight(), r, g, b, trackAlpha);
        context.submit(trackCommand);
    }

    private void renderThumb(WidgetRenderContext context, float finalAlpha) {
        var thumbStart = getThumbPosition();
        var thumbSize = getThumbSize();

        var thumbAlpha = ARGB.alpha(thumbColor) / 255.0f * finalAlpha;
        var r = ARGB.red(thumbColor) / 255.0f;
        var g = ARGB.green(thumbColor) / 255.0f;
        var b = ARGB.blue(thumbColor) / 255.0f;

        context.pose().pushPose();
        context.pose().translate(0.0f, 0.0f, 0.1f);

        if (orientation == Orientation.HORIZONTAL) {
            context.pose().translate(thumbStart, 0.0f, 0.0f);
            var thumbCommand = new FillRectDrawCommand(thumbSize, getHeight(), r, g, b, thumbAlpha);
            context.submit(thumbCommand);
        } else {
            context.pose().translate(0.0f, thumbStart, 0.0f);
            var thumbCommand = new FillRectDrawCommand(getWidth(), thumbSize, r, g, b, thumbAlpha);
            context.submit(thumbCommand);
        }

        context.pose().popPose();
    }

    @Override
    protected float getThumbSize() {
        var maxScroll = panel.getMaxScroll();

        var viewSize = orientation == Orientation.HORIZONTAL ? panel.getWidth() : panel.getHeight();
        var contentSize = maxScroll + viewSize;
        var ratio = viewSize / contentSize;
        return MathUtil.clamp(ratio * getTrackSize(), 16.0f, getTrackSize());
    }

    @Override
    protected float getThumbPosition() {
        var maxScroll = panel.getMaxScroll();
        if (maxScroll <= 0.0f)
            return 0.0f;

        var track = getTrackSize() - getThumbSize();
        var ratio = panel.getScrollY() / maxScroll;
        return ratio * track;
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        var maxScroll = panel.getMaxScroll();
        if (maxScroll <= 0.0f)
            return;

        var track = getTrackSize() - getThumbSize();
        if (track <= 0.0f)
            return;

        var ratio = MathUtil.clamp((mouse - dragOffset) / track, 0.0f, 1.0f);
        panel.setScrollTarget(ratio * maxScroll);
    }
}