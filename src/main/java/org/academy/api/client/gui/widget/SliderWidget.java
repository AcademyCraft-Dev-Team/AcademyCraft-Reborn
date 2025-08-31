package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.Orientation;

public class SliderWidget extends AbstractSliderWidget {

    public SliderWidget(float x, float y, float width, float height, Orientation orientation,
                        float minValue, float maxValue, float initialValue) {
        super(x, y, width, height, orientation, minValue, maxValue, initialValue);
    }
/*
    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        float absoluteAlpha = this.getAbsoluteAlpha();

        if (this.showBackground) {
            int finalTrackColor = RenderUtil.applyAlpha(this.getTrackColor(), absoluteAlpha);
            RenderUtil.fill(stack, bufferSource, 0, 0, this.getWidth(), this.getHeight(), finalTrackColor);
        }

        int finalThumbColor = RenderUtil.applyAlpha(this.getThumbColor(), absoluteAlpha);
        float thumbStart = this.getThumbPosition();
        float thumbSize = this.getThumbSize();

        stack.pushPose();
        stack.translate(0, 0, 1);

        if (this.orientation == Orientation.HORIZONTAL) {
            RenderUtil.fill(stack, bufferSource, thumbStart, 0, thumbSize, this.getHeight(), finalThumbColor);
        } else {
            RenderUtil.fill(stack, bufferSource, 0, thumbStart, this.getWidth(), thumbSize, finalThumbColor);
        }

        stack.popPose();
    }*/
}