package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.common.util.MathUtil;

import java.util.function.Consumer;

public abstract class AbstractSliderWidget extends DragBarWidget {
    public float minValue;
    public float maxValue;
    private float currentValue;
    public Consumer<Float> onValueChanged;

    public AbstractSliderWidget(float x, float y, float width, float height, Orientation orientation,
                                float newMinValue, float newMaxValue, float initialValue) {
        super(x, y, width, height, orientation);
        minValue = newMinValue;
        maxValue = newMaxValue;
        currentValue = MathUtil.clamp(initialValue, newMinValue, newMaxValue);
    }

    public float getValue() {
        return currentValue;
    }

    public void setValue(float value) {
        var newValue = MathUtil.clamp(value, minValue, maxValue);
        if (currentValue != newValue) {
            currentValue = newValue;
            if (onValueChanged != null) {
                onValueChanged.accept(currentValue);
            }
        }
    }

    @Override
    protected float getThumbSize() {
        return 8f;
    }
}