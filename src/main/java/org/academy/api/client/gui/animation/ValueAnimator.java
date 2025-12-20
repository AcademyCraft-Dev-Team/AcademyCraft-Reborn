package org.academy.api.client.gui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ValueAnimator extends Animator {
    private float startValue;
    private float endValue;
    private float animatedValue;
    private TimeInterpolator interpolator = EasingFunctions.LINEAR;
    private final List<Consumer<ValueAnimator>> updateListeners = new ArrayList<>();

    protected ValueAnimator() {
    }

    public static ValueAnimator ofFloat(float startValue, float endValue) {
        var anim = new ValueAnimator();
        anim.setFloatValues(startValue, endValue);
        return anim;
    }

    public void setFloatValues(float startValue, float endValue) {
        this.startValue = startValue;
        this.endValue = endValue;
        animatedValue = startValue;
    }

    public float getAnimatedValue() {
        return animatedValue;
    }

    protected float getEndValue() {
        return endValue;
    }

    @Override
    public ValueAnimator setDuration(long duration) {
        super.setDuration(duration);
        return this;
    }

    @Override
    public ValueAnimator setStartDelay(long startDelay) {
        super.setStartDelay(startDelay);
        return this;
    }

    public ValueAnimator setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
        return this;
    }

    public void addUpdateListener(Consumer<ValueAnimator> listener) {
        updateListeners.add(listener);
    }

    public void removeUpdateListener(Consumer<ValueAnimator> listener) {
        updateListeners.remove(listener);
    }

    @Override
    boolean tick(long currentTime) {
        if (startTime == -1) return true;
        if (currentTime < startTime) return false;

        var elapsedTime = currentTime - startTime;
        var fraction = duration > 0 ? (float) elapsedTime / duration : 1.0f;

        var finished = fraction >= 1.0f;
        fraction = Math.min(fraction, 1.0f);

        var interpolatedFraction = interpolator.getInterpolation(fraction);
        animatedValue = startValue + interpolatedFraction * (endValue - startValue);

        for (var listener : updateListeners) listener.accept(this);

        if (finished) end();

        return finished;
    }
}