package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.render.RenderContext;

public class AutoScaleLabelWidget extends LabelWidget {
    public AutoScaleLabelWidget(String text) {
        super(text);
    }

    public AutoScaleLabelWidget(Component component) {
        super(component);
    }

    @Override
    public void render(RenderContext context) {
        updateScale();
        super.render(context);
    }

    private void updateScale() {
        var font = Minecraft.getInstance().font;
        var availableWidth = getWidth();

        if (availableWidth <= 0 || getComponent().getString().isEmpty()) {
            scale = 1.0f;
            return;
        }

        var textOriginalWidth = font.width(getComponent());
        if (textOriginalWidth <= 0) {
            scale = 1.0f;
            return;
        }

        var newScale = 1.0f;
        if (textOriginalWidth > availableWidth)
            newScale = availableWidth / (float) textOriginalWidth;

        scale = newScale;
    }

    @Override
    public LabelWidget setText(String text) {
        return super.setText(text);
    }

    @Override
    public LabelWidget setText(Component component) {
        return super.setText(component);
    }
}