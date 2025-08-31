package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class AutoScaleLabelWidget extends LabelWidget {
    public AutoScaleLabelWidget(String text, float x, float y, float renderAreaWidth) {
        super(Component.literal(text), x, y, renderAreaWidth, Minecraft.getInstance().font.lineHeight);
        setVerticalAlignment(VerticalAlignment.MIDDLE);
        updateScale();
    }

    public AutoScaleLabelWidget(Component component, float x, float y, float renderAreaWidth) {
        super(component, x, y, renderAreaWidth, Minecraft.getInstance().font.lineHeight);
        setVerticalAlignment(VerticalAlignment.MIDDLE);
        updateScale();
    }

    private void updateScale() {
        var font = Minecraft.getInstance().font;
        var availableWidth = getWidth();

        if (availableWidth <= 0 || getComponent().getString().isEmpty()) {
            setScale(1.0f);
            return;
        }

        var textOriginalWidth = font.width(getComponent());
        if (textOriginalWidth <= 0) {
            setScale(1.0f);
            return;
        }

        var newScale = 1.0f;
        if (textOriginalWidth > availableWidth)
            newScale = availableWidth / (float) textOriginalWidth;

        setScale(newScale);
    }

    @Override
    public AutoScaleLabelWidget setText(String text) {
        return setText(Component.literal(text));
    }

    @Override
    public AutoScaleLabelWidget setText(Component component) {
        super.setText(component);
        updateScale();
        return this;
    }

    @Override
    public AutoScaleLabelWidget setWidth(float width) {
        super.setWidth(width);
        updateScale();
        return this;
    }
}