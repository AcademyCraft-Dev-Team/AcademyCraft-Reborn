package org.academy.api.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.Tickable;

public class BracketProgressBarWidget extends LabelWidget implements Tickable {
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
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        if (isAnimating) {
            currentStep += progressSpeed * (partialTicks / 20.0f);

            int step = Math.min((int) Math.floor(currentStep), totalSlots + 2);

            if (step >= totalSlots + 2) {
                isAnimating = false;
                if (afterFinished != null) afterFinished.run();
            }

            if (step == 0) {
                value = "";
            } else if (step == 1) {
                value = "[";
            } else {
                int hashes = step - 2;
                if (hashes < 0) hashes = 0;
                if (hashes > totalSlots) hashes = totalSlots;
                value = '[' +
                        String.valueOf(fillChar).repeat(Math.max(0, hashes)) +
                        ']';
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
    }
}