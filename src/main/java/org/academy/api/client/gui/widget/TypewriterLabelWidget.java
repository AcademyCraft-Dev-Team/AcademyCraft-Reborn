package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.render.MatrixStack;

public class TypewriterLabelWidget extends LabelWidget {
    public final String fullText;
    public String displayedText = "";
    public float currentStep = 0.0f;
    public float displaySpeed = 100;
    public boolean isAnimating = false;
    public Runnable afterFinished;

    public TypewriterLabelWidget(String newFullText, float x, float y) {
        super("", x, y);
        fullText = newFullText;
    }

    public void start() {
        isAnimating = true;
        currentStep = 0.0f;
    }

    public void stop() {
        isAnimating = false;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (isAnimating) {
            currentStep += displaySpeed * (partialTick / 20.0f);

            var currentLength = Math.min((int) Math.floor(currentStep), fullText.length());
            displayedText = fullText.substring(0, currentLength);

            if (currentLength >= fullText.length()) {
                isAnimating = false;
                if (afterFinished != null) {
                    afterFinished.run();
                }
            }
        }
        value = displayedText;

        super.render(stack, bufferSource, mouseX, mouseY, partialTick);
    }
}