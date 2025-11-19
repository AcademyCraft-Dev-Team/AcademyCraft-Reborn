package org.academy.api.client.gui.widget;

import net.minecraft.util.Mth;
import org.academy.api.client.gui.layout.Orientation;
import org.jspecify.annotations.Nullable;

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
        super(orientation);
        this.minValue = minValue;
        this.maxValue = maxValue;
        currentValue = Mth.clamp(initialValue, this.minValue, this.maxValue);
    }

    public float getValue() {
        return currentValue;
    }

    public void setValue(float value) {
        var newValue = Mth.clamp(value, minValue, maxValue);
        if (currentValue != newValue) {
            currentValue = newValue;
            if (onValueChanged != null) {
                onValueChanged.accept(currentValue);
            }
        }
    }

    @Override
    protected float getThumbPosition() {
        var range = maxValue - minValue;
        if (range <= 0) {
            return 0;
        }
        var valueFraction = (currentValue - minValue) / range;

        if (orientation == Orientation.VERTICAL && direction == Direction.BOTTOM_TO_TOP) {
            valueFraction = 1.0f - valueFraction;
        }

        var trackSize = getTrackSize() - getThumbSize();
        return valueFraction * trackSize;
    }

    @Override
    protected void updateTargetFromMouse(float mousePosition) {
        var trackSize = getTrackSize() - getThumbSize();
        if (trackSize <= 0) {
            return;
        }

        var position = mousePosition - dragOffset;
        var positionFraction = Mth.clamp(position / trackSize, 0f, 1f);

        if (orientation == Orientation.VERTICAL && direction == Direction.BOTTOM_TO_TOP) {
            positionFraction = 1.0f - positionFraction;
        }

        var newValue = minValue + positionFraction * (maxValue - minValue);
        setValue(newValue);
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