package org.academy.api.client.gui.widgets;

import net.minecraft.client.renderer.texture.Tickable;

public class BracketProgressBarWidget extends LabelWidget implements Tickable {
    private final char fillChar;
    private final int totalSlots;
    public int currentStep = 0;
    public int updateInterval = 1;
    private int tickCounter = 0;
    public boolean isAnimating = false;
    public Runnable afterFinished;

    public BracketProgressBarWidget(char fillChar, int totalSlots, float x, float y) {
        super("", x, y);
        this.fillChar = fillChar;
        this.totalSlots = totalSlots;
    }

    public void start() {
        isAnimating = true;
        currentStep = 0;
        tickCounter = 0;
        this.value = "";
    }

    public void stop() {
        isAnimating = false;
    }

    @Override
    public void tick() {
        if (!isAnimating) return;
        if (++tickCounter < updateInterval) return;
        tickCounter = 0;
        currentStep++;

        int maxStep = totalSlots + 2;
        if (currentStep > maxStep) {
            currentStep = maxStep;
            isAnimating = false;
            if (afterFinished != null) afterFinished.run();
        }

        if (currentStep == 0) {
            value = "";
        } else if (currentStep == 1) {
            value = "[";
        } else {
            int hashes = currentStep - 2;
            if (hashes < 0) hashes = 0;
            if (hashes > totalSlots) hashes = totalSlots;
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < hashes; i++) sb.append(fillChar);
            sb.append(']');
            value = sb.toString();
        }
    }
}