package org.academy.api.client.gui.widget;

import org.jetbrains.annotations.NotNull;

public class BracketProgressBarWidget extends AbstractAnimatedLabelWidget {
    private final char fillChar;
    private final int totalSlots;
    private float stepsPerSecond = 50.0f;
    private int lastRenderedStep = -1;

    public BracketProgressBarWidget(char fillChar, int totalSlots, float x, float y) {
        super("", x, y);
        this.fillChar = fillChar;
        this.totalSlots = totalSlots;
    }

    @Override
    protected void updateAnimation(float deltaTime) {
        this.animationStep += this.stepsPerSecond * deltaTime;

        int currentVisualStep = Math.min((int) Math.floor(this.animationStep), this.getTotalAnimationSteps());
        this.updateTextForStep(currentVisualStep);
        this.checkAnimationCompletion(currentVisualStep);
    }

    @Override
    protected int getTotalAnimationSteps() {
        return this.totalSlots + 2;
    }

    @Override
    protected void updateTextForStep(int step) {
        if (step == this.lastRenderedStep) {
            return;
        }
        this.lastRenderedStep = step;

        if (step <= 0) {
            super.setText("");
            return;
        }
        if (step == 1) {
            super.setText("[");
            return;
        }

        int filledSlots = Math.min(step - 2, this.totalSlots);
        var fill = String.valueOf(fillChar).repeat(Math.max(0, filledSlots));
        super.setText("[" + fill + "]");
    }

    @NotNull
    public BracketProgressBarWidget setStepsPerSecond(float stepsPerSecond) {
        this.stepsPerSecond = stepsPerSecond;
        return this;
    }
}