package org.academy.api.client.gui.animation;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ObjectAnimator extends ValueAnimator {
    @Nullable
    private Supplier<Float> getter;
    private boolean autoUpdateStartValue = false;

    private ObjectAnimator() {
    }

    public static ObjectAnimator ofFloat(Consumer<Float> target, float startValue, float endValue) {
        var anim = new ObjectAnimator();
        anim.setFloatValues(startValue, endValue);
        anim.addUpdateListener(animation -> target.accept(animation.getAnimatedValue()));
        target.accept(startValue);
        return anim;
    }

    public static ObjectAnimator ofFloat(Supplier<Float> getter, Consumer<Float> setter, float endValue) {
        var anim = new ObjectAnimator();
        anim.getter = getter;
        anim.setFloatValues(getter.get(), endValue);
        anim.addUpdateListener(animation -> setter.accept(animation.getAnimatedValue()));
        anim.autoUpdateStartValue = true;
        return anim;
    }

    @Override
    public void start() {
        if (autoUpdateStartValue && getter != null) {
            setFloatValues(getter.get(), getEndValue());
        }
        super.start();
    }

    @Override
    void onStartInternal() {
        if (autoUpdateStartValue && getter != null) {
            setFloatValues(getter.get(), getEndValue());
        }
        super.onStartInternal();
    }

    @Override
    public ObjectAnimator setDuration(long duration) {
        super.setDuration(duration);
        return this;
    }

    @Override
    public ObjectAnimator setStartDelay(long startDelay) {
        super.setStartDelay(startDelay);
        return this;
    }

    @Override
    public ObjectAnimator setInterpolator(TimeInterpolator interpolator) {
        super.setInterpolator(interpolator);
        return this;
    }
}