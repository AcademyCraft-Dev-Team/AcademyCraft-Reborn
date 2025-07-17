package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.render.MatrixStack;

public class BracketProgressBarWidget extends LabelWidget {
    private final char fillChar;
    private final int totalSlots;
    public float currentStep = 0.0f;
    public float progressSpeed = 50f;
    public boolean isAnimating = false;
    public Runnable afterFinished;

    public BracketProgressBarWidget(char fillChar, int totalSlots, float x, float y) {
        super("", x, y);
        this.fillChar = fillChar;
        this.totalSlots = totalSlots;
    }

    public void start() {
        isAnimating = true;
        currentStep = 0.0f;
        this.value = "";
    }

    public void stop() {
        isAnimating = false;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (isAnimating) {
            currentStep += progressSpeed * (partialTick / 20.0f);

            var step = Math.min((int) Math.floor(currentStep), totalSlots + 2);

            if (step >= totalSlots + 2) {
                isAnimating = false;
                if (afterFinished != null) afterFinished.run();
            }

            if (step == 0) {
                value = "";
            } else if (step == 1) {
                value = "[";
            } else {
                var hashes = step - 2;
                if (hashes < 0) hashes = 0;
                if (hashes > totalSlots) hashes = totalSlots;
                value = '[' +
                        String.valueOf(fillChar).repeat(Math.max(0, hashes)) +
                        ']';
            }
        }

        super.render(stack, bufferSource, mouseX, mouseY, partialTick);
    }
}