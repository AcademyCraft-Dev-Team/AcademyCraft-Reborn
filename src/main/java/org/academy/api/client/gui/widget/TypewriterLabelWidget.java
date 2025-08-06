package org.academy.api.client.gui.widget;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class TypewriterLabelWidget extends AbstractAnimatedLabelWidget {
    private Component targetComponent;
    private float charactersPerSecond = 100.0f;
    private int lastRenderedLength = -1;

    public TypewriterLabelWidget(@NotNull String fullText, float x, float y) {
        this(Component.literal(fullText), x, y);
    }

    public TypewriterLabelWidget(@NotNull Component targetComponent, float x, float y) {
        super("", x, y);
        this.targetComponent = targetComponent;
    }

    @Override
    protected void updateAnimation(float deltaTime) {
        this.animationStep += this.charactersPerSecond * deltaTime;

        int currentLength = Math.min((int) Math.floor(this.animationStep), this.getTotalAnimationSteps());
        this.updateTextForStep(currentLength);
        this.checkAnimationCompletion(currentLength);
    }

    @Override
    protected int getTotalAnimationSteps() {
        return this.targetComponent.getString().length();
    }

    @Override
    protected void updateTextForStep(int length) {
        if (length == this.lastRenderedLength) {
            return;
        }
        this.lastRenderedLength = length;

        var fullTextString = this.targetComponent.getString();
        var currentTextSlice = fullTextString.substring(0, Math.max(0, length));
        var animatedComponent = Component.literal(currentTextSlice).setStyle(this.targetComponent.getStyle());

        super.setText(animatedComponent);
    }

    @NotNull
    public TypewriterLabelWidget setTargetComponent(@NotNull Component component) {
        this.targetComponent = component;
        this.resetAnimation();
        return this;
    }

    @NotNull
    public TypewriterLabelWidget setCharactersPerSecond(float charactersPerSecond) {
        this.charactersPerSecond = charactersPerSecond;
        return this;
    }
}