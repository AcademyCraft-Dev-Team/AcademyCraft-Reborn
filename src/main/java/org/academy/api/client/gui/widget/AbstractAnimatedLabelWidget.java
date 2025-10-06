package org.academy.api.client.gui.widget;

import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.TimeInterpolator;
import org.academy.api.client.gui.animation.ValueAnimator;

import javax.annotation.Nullable;

public abstract class AbstractAnimatedLabelWidget extends LabelWidget {
    @Nullable
    protected ValueAnimator animator;
    protected Component targetComponent;
    @Nullable
    protected Runnable onAnimationFinished;

    private long duration = 300L;
    @Nullable
    private TimeInterpolator interpolator;

    public AbstractAnimatedLabelWidget(String initialText, float x, float y) {
        this(Component.literal(initialText), x, y);
    }

    public AbstractAnimatedLabelWidget(Component initialText, float x, float y) {
        super(Component.empty(), x, y);
        targetComponent = initialText;
    }

    public void animateText(Component newTarget) {
        if (animator != null && animator.isRunning())
            animator.cancel();

        targetComponent = newTarget;
        animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration(duration);
        if (interpolator != null)
            animator.setInterpolator(interpolator);

        animator.addUpdateListener(this::onAnimationUpdate);
        registerFinishCallback(animator);
        animator.start();
    }

    protected abstract void onAnimationUpdate(ValueAnimator animator);

    private void registerFinishCallback(ValueAnimator valueAnimator) {
        if (onAnimationFinished == null)
            return;

        valueAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onAnimationFinished.run();
            }
        });
    }

    protected final void setAnimationProgressText(Component component) {
        this.component = component;
    }

    public void skipToEnd() {
        if (animator != null && animator.isRunning())
            animator.end();

        super.setText(targetComponent);
    }

    public AbstractAnimatedLabelWidget setOnAnimationFinished(@Nullable Runnable onAnimationFinished) {
        this.onAnimationFinished = onAnimationFinished;
        return this;
    }

    public AbstractAnimatedLabelWidget setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public AbstractAnimatedLabelWidget setInterpolator(@Nullable TimeInterpolator interpolator) {
        this.interpolator = interpolator;
        return this;
    }

    @Override
    public LabelWidget setText(Component component) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        targetComponent = component;
        super.setText(component);
        return this;
    }

    @Override
    public LabelWidget setText(String text) {
        return setText(Component.literal(text));
    }
}