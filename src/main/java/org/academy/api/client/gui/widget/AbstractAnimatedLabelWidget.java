package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractAnimatedLabelWidget extends LabelWidget {
    protected float animationStep = 0.0f;
    protected boolean isAnimating = false;
    protected Runnable onAnimationFinished;
    private long lastUpdateTime = 0L;

    public AbstractAnimatedLabelWidget(@NotNull String initialText, float x, float y) {
        super(initialText, x, y);
    }

    /**
     * Calculates the animation progress based on elapsed time since the last frame.
     * @param deltaTime The time elapsed since the last update, in seconds.
     */
    protected abstract void updateAnimation(float deltaTime);

    /**
     * Returns the total number of steps required to complete the animation.
     */
    protected abstract int getTotalAnimationSteps();

    /**
     * Updates the displayed text to reflect a specific step in the animation.
     * @param step The step to display.
     */
    protected abstract void updateTextForStep(int step);

    @Override
    public void render(@NotNull MatrixStack stack, @NotNull MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (this.isAnimating) {
            long currentTime = System.nanoTime();
            if (this.lastUpdateTime == 0L) {
                this.lastUpdateTime = currentTime;
            }
            float deltaTime = (currentTime - this.lastUpdateTime) / 1_000_000_000.0f;
            this.lastUpdateTime = currentTime;

            this.updateAnimation(deltaTime);
        }
        super.render(stack, bufferSource, mouseX, mouseY, partialTick);
    }

    protected void finishAnimation() {
        this.isAnimating = false;
        if (onAnimationFinished != null) {
            onAnimationFinished.run();
        }
    }

    protected void checkAnimationCompletion(int currentStep) {
        if (currentStep >= getTotalAnimationSteps()) {
            finishAnimation();
        }
    }

    public void startAnimation() {
        this.isAnimating = true;
        this.lastUpdateTime = 0L;
    }

    public void stopAnimation() {
        this.isAnimating = false;
    }

    public void resetAnimation() {
        this.animationStep = 0.0f;
        this.updateTextForStep(0);
    }

    public void restartAnimation() {
        this.resetAnimation();
        this.startAnimation();
    }

    public void skipToEnd() {
        this.stopAnimation();
        this.updateTextForStep(getTotalAnimationSteps());
    }

    @NotNull
    public AbstractAnimatedLabelWidget setOnAnimationFinished(@Nullable Runnable onAnimationFinished) {
        this.onAnimationFinished = onAnimationFinished;
        return this;
    }

    @Override
    public @NotNull final LabelWidget setText(@NotNull Component component) {
        throw new UnsupportedOperationException("Cannot set text directly on an animated LabelWidget. Use animation controls.");
    }
}