package org.academy.api.client.gui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ValueAnimator extends Animator {
    public static final int INFINITE = -1;
    public static final int RESTART = 1;
    public static final int REVERSE = 2;

    private float startValue;
    private float endValue;
    private float animatedValue;

    private int repeatCount = 0;
    private int repeatMode = RESTART;

    private int currentIteration = 0;
    private boolean isReversing = false;

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

    public void setRepeatCount(int value) {
        repeatCount = value;
    }

    public void setRepeatMode(int value) {
        repeatMode = value;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getRepeatCount() {
        return repeatCount;
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
    void onStartInternal() {
        isReversing = false;
        currentIteration = 0;
        super.onStartInternal();
    }

    @Override
    boolean tick(long currentTime) {
        if (paused) {
            if (pauseBeginTime == -1) {
                pauseBeginTime = currentTime;
            }
            return false;
        } else {
            if (pauseBeginTime != -1) {
                var pausedDuration = currentTime - pauseBeginTime;
                startTime += pausedDuration;
                pauseBeginTime = -1;
            }
        }

        if (startTime == -1) return true;
        if (currentTime < startTime) return false;

        var elapsedTime = currentTime - startTime;
        var fraction = duration > 0 ? (float) elapsedTime / duration : 1.0f;

        var finished = false;
        if (fraction >= 1.0f) {
            if (repeatCount == INFINITE || currentIteration < repeatCount) {
                currentIteration++;

                if (repeatMode == REVERSE) {
                    isReversing = !isReversing;
                }

                startTime += duration;
                elapsedTime = currentTime - startTime;
                fraction = duration > 0 ? (float) elapsedTime / duration : 0f;
            } else {
                finished = true;
            }
        }

        fraction = Math.clamp(fraction, 0.0f, 1.0f);

        var effectiveFraction = isReversing ? (1.0f - fraction) : fraction;
        var interpolatedFraction = interpolator.getInterpolation(effectiveFraction);
        animatedValue = startValue + interpolatedFraction * (endValue - startValue);

        for (var listener : updateListeners) listener.accept(this);

        if (finished) end();

        return finished;
    }
}