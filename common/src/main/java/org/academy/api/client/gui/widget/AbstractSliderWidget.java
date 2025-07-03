package org.academy.api.client.gui.widget;

import org.academy.api.common.util.MathUtil;

import java.util.function.Consumer;

public abstract class AbstractSliderWidget extends DragBarWidget {
    public float minValue;
    public float maxValue;
    private float currentValue;
    public Consumer<Float> onValueChanged;

    public AbstractSliderWidget(float x, float y, float width, float height, float minValue, float maxValue, float initialValue) {
        super(x, y, width, height);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = MathUtil.clamp(initialValue, minValue, maxValue);
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

    @Override
    protected void updateTargetFromMouse(float mouse) {
        var track = getTrackSize() - getThumbSize();
        if (track <= 0) return;
        var ratio = MathUtil.clamp((mouse - dragOffset) / track, 0f, 1f);
        setValue(minValue + ratio * (maxValue - minValue));
    }
}