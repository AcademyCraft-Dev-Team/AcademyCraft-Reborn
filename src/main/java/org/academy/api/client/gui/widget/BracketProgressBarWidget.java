package org.academy.api.client.gui.widget;

import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.animation.ValueAnimator;

public class BracketProgressBarWidget extends AbstractAnimatedLabelWidget {
    private final char fillChar;
    private final int totalSlots;
    private float stepsPerSecond = 50.0f;
    private int lastRenderedStep = -1;

    public BracketProgressBarWidget(char fillChar, int totalSlots, float x, float y) {
        super(Component.empty(), x, y);
        this.fillChar = fillChar;
        this.totalSlots = totalSlots;
        targetComponent = Component.literal("[" + String.valueOf(fillChar).repeat(totalSlots) + "]");
    }

    @Override
    protected void onAnimationUpdate(ValueAnimator animator) {
        var totalAnimationSteps = totalSlots + 2;
        var fraction = animator.getAnimatedValue();
        var currentStep = Math.min((int) Math.floor(fraction * totalAnimationSteps), totalAnimationSteps);

        if (currentStep == lastRenderedStep)
            return;

        lastRenderedStep = currentStep;

        var builder = new StringBuilder();
        if (currentStep > 0)
            builder.append('[');

        var filledSlots = Math.max(0, currentStep - 1);
        if (filledSlots > 0)
            builder.append(String.valueOf(fillChar).repeat(Math.min(filledSlots, totalSlots)));

        if (currentStep >= totalSlots + 2)
            builder.append(']');

        setAnimationProgressText(Component.literal(builder.toString()));
    }

    public void startAnimation() {
        var totalAnimationSteps = totalSlots + 2;
        if (totalAnimationSteps > 0 && stepsPerSecond > 0.0f) {
            var newDuration = (long) (totalAnimationSteps / stepsPerSecond * 1000.0f);
            setDuration(newDuration);
        } else {
            setDuration(0);
        }

        animateText(targetComponent);
    }

    public void resetAnimation() {
        if (animator != null && animator.isRunning())
            animator.cancel();

        lastRenderedStep = -1;
        setAnimationProgressText(Component.empty());
    }

    public void restartAnimation() {
        resetAnimation();
        startAnimation();
    }

    public BracketProgressBarWidget setStepsPerSecond(float stepsPerSecond) {
        this.stepsPerSecond = stepsPerSecond;
        return this;
    }
}