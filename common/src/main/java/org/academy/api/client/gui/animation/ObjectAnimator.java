package org.academy.api.client.gui.animation;

import java.util.function.Consumer;

public class ObjectAnimator extends ValueAnimator {
    private ObjectAnimator() {
    }

    public static ObjectAnimator ofFloat(Consumer<Float> target, float startValue, float endValue) {
        var anim = new ObjectAnimator();
        anim.setFloatValues(startValue, endValue);
        anim.addUpdateListener(animation -> target.accept(animation.getAnimatedValue()));
        return anim;
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