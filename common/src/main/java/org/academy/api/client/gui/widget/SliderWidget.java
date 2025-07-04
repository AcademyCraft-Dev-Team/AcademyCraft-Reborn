package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;

public class SliderWidget extends AbstractSliderWidget {
    public enum Direction {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP
    }

    private Direction direction = Direction.BOTTOM_TO_TOP;

    public SliderWidget(float x, float y, float width, float height, Orientation orientation,
                        float minValue, float maxValue, float initialValue) {
        super(x, y, width, height, orientation, minValue, maxValue, initialValue);
    }

    public void setDirection(Direction newDirection) {
        direction = newDirection;
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    protected float getThumbPosition() {
        var track = getTrackSize() - getThumbSize();
        if (maxValue - minValue == 0) return orientation == Orientation.HORIZONTAL ? getX() : getY();
        var ratio = (getValue() - minValue) / (maxValue - minValue);

        if (orientation == Orientation.VERTICAL && direction == Direction.BOTTOM_TO_TOP) {
            ratio = 1.0f - ratio;
        }

        return (orientation == Orientation.HORIZONTAL ? getX() : getY()) + ratio * track;
    }

    @Override
    protected void updateTargetFromMouse(float mouse) {
        var track = getTrackSize() - getThumbSize();
        if (track <= 0) return;

        var ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);

        if (orientation == Orientation.VERTICAL && direction == Direction.BOTTOM_TO_TOP) {
            setValue(minValue + (1.0f - ratio) * (maxValue - minValue));
        } else {
            setValue(minValue + ratio * (maxValue - minValue));
        }
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        graphics.pose().pushPose();

        var matrix = graphics.pose().last().pose();
        var buffer = graphics.bufferSource();

        var absoluteAlpha = getAbsoluteAlpha();
        var finalTrackColor = (getTrackColor() & 0x00FFFFFF) | ((int) (((getTrackColor() >> 24) & 0xFF) * absoluteAlpha) << 24);
        var finalThumbColor = (getThumbColor() & 0x00FFFFFF) | ((int) (((getThumbColor() >> 24) & 0xFF) * absoluteAlpha) << 24);

        if (showBackground) {
            RenderUtil.fill(matrix, getX(), getY(), getX() + getWidth(), getY() + getHeight(), finalTrackColor, buffer);
        }

        var thumbStart = getThumbPosition();
        var thumbSize = getThumbSize();
        graphics.pose().translate(0, 0, 1);

        if (orientation == Orientation.HORIZONTAL) {
            RenderUtil.fill(matrix, thumbStart, getY(), thumbStart + thumbSize, getY() + getHeight(), finalThumbColor, buffer);
        } else {
            RenderUtil.fill(matrix, getX(), thumbStart, getX() + getWidth(), thumbStart + thumbSize, finalThumbColor, buffer);
        }

        graphics.pose().popPose();
    }
}