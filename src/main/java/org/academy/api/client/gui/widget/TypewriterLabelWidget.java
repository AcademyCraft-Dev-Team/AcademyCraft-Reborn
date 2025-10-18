package org.academy.api.client.gui.widget;

import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.animation.ValueAnimator;

public class TypewriterLabelWidget extends AbstractAnimatedLabelWidget {
    private float charactersPerSecond = 100.0f;
    private int lastRenderedLength = -1;

    public TypewriterLabelWidget(String fullText, float x, float y) {
        this(Component.literal(fullText), x, y);
    }

    public TypewriterLabelWidget(Component targetComponent, float x, float y) {
        super(targetComponent, x, y);
    }

    @Override
    protected void onAnimationUpdate(ValueAnimator animator) {
        var fullTextString = targetComponent.getString();
        if (fullTextString.isEmpty()) {
            setAnimationProgressText(Component.empty());
            return;
        }

        var fraction = animator.getAnimatedValue();
        var currentLength = (int) Math.floor(fraction * fullTextString.length());
        currentLength = Math.min(currentLength, fullTextString.length());

        if (currentLength == lastRenderedLength)
            return;

        lastRenderedLength = currentLength;

        var currentTextSlice = fullTextString.substring(0, currentLength);
        var animatedComponent = Component.literal(currentTextSlice).setStyle(targetComponent.getStyle());
        setAnimationProgressText(animatedComponent);
    }

    @Override
    public void animateText(Component newTarget) {
        lastRenderedLength = -1;
        var textLength = newTarget.getString().length();
        if (textLength > 0 && charactersPerSecond > 0.0f) {
            var newDuration = (long) (textLength / charactersPerSecond * 1000.0f);
            setDuration(newDuration);
        } else {
            setDuration(0);
        }
        super.animateText(newTarget);
    }

    public void startAnimation() {
        animateText(targetComponent);
    }

    public TypewriterLabelWidget setCharactersPerSecond(float charactersPerSecond) {
        this.charactersPerSecond = charactersPerSecond;
        return this;
    }
}