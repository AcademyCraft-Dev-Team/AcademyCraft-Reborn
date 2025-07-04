package org.academy.api.client.gui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ValueAnimator extends Animator {
    private float startValue;
    private float endValue;
    private float animatedValue;
    private TimeInterpolator interpolator = EasingFunctions.LINEAR;
    private List<Consumer<ValueAnimator>> updateListeners = null;

    protected ValueAnimator() {
    }

    public static ValueAnimator ofFloat(float startValue, float endValue) {
        ValueAnimator anim = new ValueAnimator();
        anim.setFloatValues(startValue, endValue);
        return anim;
    }

    public void setFloatValues(float startValue, float endValue) {
        this.startValue = startValue;
        this.endValue = endValue;
        this.animatedValue = startValue;
    }

    public float getAnimatedValue() {
        return animatedValue;
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
        if (updateListeners == null) {
            updateListeners = new ArrayList<>();
        }
        updateListeners.add(listener);
    }

    public void removeUpdateListener(Consumer<ValueAnimator> listener) {
        if (updateListeners != null) {
            updateListeners.remove(listener);
            if (updateListeners.isEmpty()) {
                updateListeners = null;
            }
        }
    }

    @Override
    boolean tick(long currentTime) {
        if (startTime == -1) {
            return true;
        }

        if (currentTime < startTime) {
            return false;
        }

        long elapsedTime = currentTime - startTime;
        float fraction = duration > 0 ? (float) elapsedTime / duration : 1.0f;

        boolean finished = fraction >= 1.0f;
        fraction = Math.min(fraction, 1.0f);

        float interpolatedFraction = interpolator.getInterpolation(fraction);
        animatedValue = startValue + interpolatedFraction * (endValue - startValue);

        if (updateListeners != null) {
            for (Consumer<ValueAnimator> listener : updateListeners) {
                listener.accept(this);
            }
        }

        if (finished) {
            end();
        }

        return finished;
    }
}