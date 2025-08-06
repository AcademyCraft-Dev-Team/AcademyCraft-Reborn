package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.framework.Widget;
import org.jetbrains.annotations.NotNull;

public class AutoScaleLabelWidget extends LabelWidget {
    public AutoScaleLabelWidget(@NotNull String text, float x, float y, float renderAreaWidth) {
        super(Component.literal(text), x, y, renderAreaWidth, Minecraft.getInstance().font.lineHeight);
        this.setVerticalAlignment(VerticalAlignment.MIDDLE);
        this.updateScale();
    }

    private void updateScale() {
        var font = Minecraft.getInstance().font;
        var availableWidth = this.getWidth();

        if (availableWidth <= 0 || getComponent().getString().isEmpty()) {
            super.setScale(1.0f);
            return;
        }

        var textOriginalWidth = font.width(getComponent());
        if (textOriginalWidth <= 0) {
            super.setScale(1.0f);
            return;
        }

        var newScale = 1.0f;
        if (textOriginalWidth > availableWidth) {
            newScale = availableWidth / (float) textOriginalWidth;
        }

        super.setScale(newScale);
    }

    @Override
    public @NotNull LabelWidget setText(@NotNull String text) {
        return this.setText(Component.literal(text));
    }

    @Override
    public @NotNull LabelWidget setText(@NotNull Component component) {
        super.setText(component);
        this.updateScale();
        return this;
    }

    @Override
    public @NotNull Widget setWidth(float width) {
        super.setWidth(width);
        this.updateScale();
        return this;
    }
}