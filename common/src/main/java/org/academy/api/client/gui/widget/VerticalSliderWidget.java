package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;

public class VerticalSliderWidget extends AbstractSliderWidget {
    public enum Direction {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP
    }

    private Direction direction = Direction.BOTTOM_TO_TOP;

    public VerticalSliderWidget(float x, float y, float width, float height, float minValue, float maxValue, float initialValue) {
        super(x, y, width, height, minValue, maxValue, initialValue);
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return this.direction;
    }

    @Override
    protected float getThumbPosition() {
        float track = getTrackSize() - getThumbSize();
        if (maxValue - minValue == 0) return getY();
        float ratio = (getValue() - minValue) / (maxValue - minValue);
        if (direction == Direction.BOTTOM_TO_TOP) {
            ratio = 1.0f - ratio;
        }
        return getY() + ratio * track;
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        float track = getTrackSize() - getThumbSize();
        if (track <= 0) return;

        float ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);

        if (direction == Direction.BOTTOM_TO_TOP) {
            setValue(minValue + (1.0f - ratio) * (maxValue - minValue));
        } else {
            setValue(minValue + ratio * (maxValue - minValue));
        }
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
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        graphics.pose().pushPose();

        var matrix = graphics.pose().last().pose();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();

        float absoluteAlpha = getAbsoluteAlpha();
        int finalTrackColor = (getTrackColor() & 0x00FFFFFF) | ((int) (((getTrackColor() >> 24) & 0xFF) * absoluteAlpha) << 24);
        int finalThumbColor = (getThumbColor() & 0x00FFFFFF) | ((int) (((getThumbColor() >> 24) & 0xFF) * absoluteAlpha) << 24);

        if (showBackground) {
            RenderUtil.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), finalTrackColor, buffer);
        }

        var thumbTop = getThumbPosition();
        var thumbHeight = getThumbSize();
        graphics.pose().translate(0, 0, 1);

        RenderUtil.fill(matrix, getX(), thumbTop, getX() + getWidth(), thumbTop + thumbHeight, finalThumbColor, buffer);

        graphics.pose().popPose();
    }
}