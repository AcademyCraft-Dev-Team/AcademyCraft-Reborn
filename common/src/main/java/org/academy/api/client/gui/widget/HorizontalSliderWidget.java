package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.util.RenderUtil;
import org.joml.Matrix4f;

public class HorizontalSliderWidget extends AbstractSliderWidget {
    public HorizontalSliderWidget(float x, float y, float width, float height, float minValue, float maxValue, float initialValue) {
        super(x, y, width, height, minValue, maxValue, initialValue);
    }

    @Override
    protected float getThumbPosition() {
        float track = getTrackSize() - getThumbSize();
        if (maxValue - minValue == 0) return getX();
        float ratio = (getValue() - minValue) / (maxValue - minValue);
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
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        graphics.pose().pushPose();

        Matrix4f matrix = graphics.pose().last().pose();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();

        if (showBackground) {
            RenderUtil.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), getTrackColor(), buffer);
        }

        float thumbLeft = getThumbPosition();
        float thumbWidth = getThumbSize();
        graphics.pose().translate(0, 0, 1);

        RenderUtil.fill(matrix, thumbLeft, getY(), thumbLeft + thumbWidth, getY() + getHeight(), getThumbColor(), buffer);

        graphics.pose().popPose();
    }
}