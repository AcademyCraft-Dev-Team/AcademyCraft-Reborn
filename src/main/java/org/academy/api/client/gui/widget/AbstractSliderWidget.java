package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The abstract base class for slider widgets. It handles all the logic for converting
 * between a value and a position on the track, including orientation and direction.
 * It does not perform any rendering itself.
 */
public abstract class AbstractSliderWidget extends DragBarWidget {

    public enum Direction {
        /** For vertical sliders, represents a value increase from bottom to top. */
        BOTTOM_TO_TOP,
        /** For vertical sliders, represents a value increase from top to bottom. */
        TOP_TO_BOTTOM
    }

    protected float minValue;
    protected float maxValue;
    protected float currentValue;
    @Nullable
    protected Consumer<Float> onValueChanged;
    protected Direction direction = Direction.BOTTOM_TO_TOP;

    public AbstractSliderWidget(float x, float y, float width, float height, Orientation orientation,
                                float minValue, float maxValue, float initialValue) {
        super(x, y, width, height, orientation);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = MathUtil.clamp(initialValue, this.minValue, this.maxValue);
    }

    public float getValue() {
        return this.currentValue;
    }

    public void setValue(float value) {
        var newValue = MathUtil.clamp(value, this.minValue, this.maxValue);
        if (this.currentValue != newValue) {
            this.currentValue = newValue;
            if (this.onValueChanged != null) {
                this.onValueChanged.accept(this.currentValue);
            }
        }
    }

    @Override
    protected float getThumbPosition() {
        float range = this.maxValue - this.minValue;
        if (range <= 0) {
            return 0;
        }
        float valueFraction = (this.currentValue - this.minValue) / range;

        if (this.orientation == Orientation.VERTICAL && this.direction == Direction.BOTTOM_TO_TOP) {
            valueFraction = 1.0f - valueFraction;
        }

        float trackSize = this.getTrackSize() - this.getThumbSize();
        return valueFraction * trackSize;
    }

    @Override
    protected void updateTargetFromMouse(float mousePosition) {
        float trackSize = this.getTrackSize() - this.getThumbSize();
        if (trackSize <= 0) {
            return;
        }

        float position = mousePosition - this.dragOffset;
        float positionFraction = MathUtil.clamp(position / trackSize, 0f, 1f);

        if (this.orientation == Orientation.VERTICAL && this.direction == Direction.BOTTOM_TO_TOP) {
            positionFraction = 1.0f - positionFraction;
        }

        float newValue = this.minValue + positionFraction * (this.maxValue - this.minValue);
        this.setValue(newValue);
    }

    public AbstractSliderWidget setDirection(Direction direction) {
        this.direction = direction;
        return this;
    }

    public AbstractSliderWidget setOnValueChanged(Consumer<Float> onValueChanged) {
        this.onValueChanged = onValueChanged;
        return this;
    }

    @Override
    protected float getThumbSize() {
        return 8f;
    }
}