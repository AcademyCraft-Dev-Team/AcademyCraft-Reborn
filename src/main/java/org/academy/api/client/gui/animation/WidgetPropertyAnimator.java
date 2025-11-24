package org.academy.api.client.gui.animation;

import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.LabelWidget;
import org.academy.api.client.gui.widget.Widget;

import java.util.EnumMap;
import java.util.Map;

public class WidgetPropertyAnimator {
    private final Widget widget;
    private long duration = 300;
    private long startDelay = 0;
    private TimeInterpolator interpolator = EasingFunctions.LINEAR;
    private Runnable withEndAction = null;
    private Runnable withStartAction = null;

    private ValueAnimator animator;
    private boolean animationScheduled = false;

    private final Map<Property, Float> pendingValues = new EnumMap<>(Property.class);
    private final Map<Property, PropertyState> runningProperties = new EnumMap<>(Property.class);

    public WidgetPropertyAnimator(Widget widget) {
        this.widget = widget;
    }

    public WidgetPropertyAnimator x(float value) {
        return animateProperty(Property.X, value);
    }

    public WidgetPropertyAnimator y(float value) {
        return animateProperty(Property.Y, value);
    }

    public WidgetPropertyAnimator z(float value) {
        return animateProperty(Property.Z, value);
    }

    public WidgetPropertyAnimator translationX(float value) {
        return animateProperty(Property.TRANSLATION_X, value);
    }

    public WidgetPropertyAnimator translationY(float value) {
        return animateProperty(Property.TRANSLATION_Y, value);
    }

    public WidgetPropertyAnimator alpha(float value) {
        return animateProperty(Property.ALPHA, value);
    }

    public WidgetPropertyAnimator width(float value) {
        return animateProperty(Property.WIDTH, value);
    }

    public WidgetPropertyAnimator height(float value) {
        return animateProperty(Property.HEIGHT, value);
    }

    public WidgetPropertyAnimator scale(float value) {
        return animateProperty(Property.SCALE, value);
    }

    public WidgetPropertyAnimator setDuration(long duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        }
        this.duration = duration;
        return this;
    }

    public WidgetPropertyAnimator setStartDelay(long startDelay) {
        if (startDelay < 0) {
            throw new IllegalArgumentException("Animators cannot have negative start delay: " + startDelay);
        }
        this.startDelay = startDelay;
        return this;
    }

    public WidgetPropertyAnimator setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
        return this;
    }

    public WidgetPropertyAnimator withEndAction(Runnable runnable) {
        withEndAction = runnable;
        return this;
    }

    public WidgetPropertyAnimator withStartAction(Runnable runnable) {
        withStartAction = runnable;
        return this;
    }

    public void start() {
        startAnimation();
    }

    public void cancel() {
        if (animator != null) {
            animator.cancel();
        }
        pendingValues.clear();
        runningProperties.clear();
        animationScheduled = false;
    }

    private WidgetPropertyAnimator animateProperty(Property property, float value) {
        pendingValues.put(property, value);
        scheduleAnimation();
        return this;
    }

    private void scheduleAnimation() {
        if (!animationScheduled) {
            animationScheduled = true;
            AnimationManager.startAnimation(new StarterAnimator());
        }
    }

    private void startAnimation() {
        animationScheduled = false;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration(duration);
        animator.setStartDelay(startDelay);
        animator.setInterpolator(interpolator);

        runningProperties.clear();
        for (var entry : pendingValues.entrySet()) {
            var property = entry.getKey();
            var endValue = entry.getValue();
            var startValue = property.get(widget);
            runningProperties.put(property, new PropertyState(startValue, endValue));
        }
        pendingValues.clear();

        if (withStartAction != null) {
            animator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    withStartAction.run();
                }
            });
        }

        if (withEndAction != null) {
            animator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    withEndAction.run();
                }
            });
        }

        animator.addUpdateListener(this::onAnimationUpdate);
        animator.start();
    }

    private void onAnimationUpdate(ValueAnimator valueAnimator) {
        var fraction = valueAnimator.getAnimatedValue();
        for (var entry : runningProperties.entrySet()) {
            var property = entry.getKey();
            var state = entry.getValue();
            var value = state.startValue + (state.endValue - state.startValue) * fraction;
            property.set(widget, value);
        }
        widget.requestLayout();
    }

    private class StarterAnimator extends Animator {
        @Override
        void onStartInternal() {
            startAnimation();
            end();
        }

        @Override
        boolean tick(long currentTime) {
            return true;
        }
    }

    private record PropertyState(float startValue, float endValue) {
    }

    private enum Property {
        X {
            @Override
            float get(Widget w) { return w.getX(); }
            @Override
            void set(Widget w, float v) { w.layout(v, w.getY(), v + w.getWidth(), w.getY() + w.getHeight()); }
        },
        Y {
            @Override
            float get(Widget w) { return w.getY(); }
            @Override
            void set(Widget w, float v) { w.layout(w.getX(), v, w.getX() + w.getWidth(), v + w.getHeight()); }
        },
        Z {
            @Override
            float get(Widget w) { return w.getZ(); }
            @Override
            void set(Widget w, float v) { w.setZ(v); }
        },
        TRANSLATION_X {
            @Override
            float get(Widget w) { return w.getTranslationX(); }
            @Override
            void set(Widget w, float v) { w.setTranslationX(v); }
        },
        TRANSLATION_Y {
            @Override
            float get(Widget w) { return w.getTranslationY(); }
            @Override
            void set(Widget w, float v) { w.setTranslationY(v); }
        },
        ALPHA {
            @Override
            float get(Widget w) { return w.getAlpha(); }
            @Override
            void set(Widget w, float v) { w.setAlpha(v); }
        },
        WIDTH {
            @Override
            float get(Widget w) { return w.getWidth(); }
            @Override
            void set(Widget w, float v) { w.setWidth(v); }
        },
        HEIGHT {
            @Override
            float get(Widget w) { return w.getHeight(); }
            @Override
            void set(Widget w, float v) { w.setHeight(v); }
        },
        SCALE {
            @Override
            float get(Widget w) {
                return (w instanceof LabelWidget) ? ((LabelWidget) w).getScale() : 1.0f;
            }
            @Override
            void set(Widget w, float v) {
                if (w instanceof LabelWidget) ((LabelWidget) w).setScale(v);
                else if (w instanceof ImageWidget) ((ImageWidget) w).setScale(v, v, true);
            }
        };

        abstract float get(Widget w);
        abstract void set(Widget w, float v);
    }
}