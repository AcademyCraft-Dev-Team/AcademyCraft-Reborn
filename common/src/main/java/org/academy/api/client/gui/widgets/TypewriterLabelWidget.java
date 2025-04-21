package org.academy.api.client.gui.widgets;

import net.minecraft.client.renderer.texture.Tickable;

public class TypewriterLabelWidget extends LabelWidget implements Tickable {
    public final String fullText;
    public String displayedText = "";
    public float displayProgress = 0.0f;
    public float displaySpeed = 0.05f;
    public boolean isAnimating = false;
    public Runnable afterFinished;

    public TypewriterLabelWidget(String fullText, float x, float y) {
        super("", x, y);
        this.fullText = fullText;
    }

    public void start() {
        isAnimating = true;
    }

    public void stop() {
        isAnimating = false;
    }

    @Override
    public void tick() {
        if (isAnimating) {
            displayProgress += displaySpeed;

            if (displayProgress > 1.0f) {
                displayProgress = 1.0f;
            }

            int currentLength = (int) Math.floor(displayProgress * fullText.length());

            displayedText = fullText.substring(0, currentLength);

            if (displayProgress == 1.0f) {
                isAnimating = false;
                if (afterFinished != null) {
                    afterFinished.run();
                }
            }
        }
        value = displayedText;
    }
}