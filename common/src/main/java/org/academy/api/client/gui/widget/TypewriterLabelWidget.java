package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.Tickable;

public class TypewriterLabelWidget extends LabelWidget implements Tickable {
    public final String fullText;
    public String displayedText = "";
    public float currentStep = 0.0f;
    public float displaySpeed = 100;
    public boolean isAnimating = false;
    public Runnable afterFinished;

    public TypewriterLabelWidget(String fullText, float x, float y) {
        super("", x, y);
        this.fullText = fullText;
    }

    public void start() {
        isAnimating = true;
        currentStep = 0.0f;
    }

    public void stop() {
        isAnimating = false;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks) {
        if (isAnimating) {
            currentStep += displaySpeed * (partialTicks / 20.0f);

            int currentLength = Math.min((int) Math.floor(currentStep), fullText.length());
            displayedText = fullText.substring(0, currentLength);

            if (currentLength >= fullText.length()) {
                isAnimating = false;
                if (afterFinished != null) {
                    afterFinished.run();
                }
            }
        }
        value = displayedText;

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
    }
}