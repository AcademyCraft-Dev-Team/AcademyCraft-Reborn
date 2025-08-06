package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class HoverLabelWidget extends LabelWidget {
    private static final String ELLIPSIS = "...";

    private final Component originalComponent;
    private final Component truncatedComponent;
    private final float maxWidth;
    private float baseScale = 1.0f;

    public HoverLabelWidget(@NotNull String text, float x, float y, float maxWidth) {
        super(Component.empty(), x, y, maxWidth, Minecraft.getInstance().font.lineHeight);
        this.originalComponent = Component.literal(text);
        this.maxWidth = maxWidth;
        this.truncatedComponent = truncate(originalComponent, maxWidth);
        this.setText(this.truncatedComponent);
        this.setAlignment(Alignment.CENTER);
        this.setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Component truncate(Component source, float maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(source) <= maxWidth) {
            return source;
        }
        var originalText = source.getString();
        int ellipsisWidth = font.width(ELLIPSIS);
        var trimmedText = font.plainSubstrByWidth(originalText, (int) (maxWidth - ellipsisWidth));
        return Component.literal(trimmedText + ELLIPSIS);
    }

    @Override
    public void setHovered(boolean hovered) {
        if (this.isHovered() == hovered) {
            return;
        }
        super.setHovered(hovered);

        if (hovered) {
            super.setText(this.originalComponent);

            var font = Minecraft.getInstance().font;
            var textWidth = font.width(this.originalComponent);

            if (textWidth > this.maxWidth) {
                super.setScale(this.maxWidth / (float) textWidth);
            } else {
                super.setScale(1.0f);
            }
        } else {
            super.setText(this.truncatedComponent);
            super.setScale(this.baseScale);
        }
    }

    @NotNull
    public HoverLabelWidget setBaseScale(float scale) {
        this.baseScale = scale;
        if (!this.isHovered()) {
            this.setScale(scale);
        }
        return this;
    }

    @Override
    public @NotNull final LabelWidget setText(@NotNull String text) {
        throw new UnsupportedOperationException("Cannot set text directly on a HoverLabelWidget.");
    }

    @Override
    public @NotNull final LabelWidget setText(@NotNull Component component) {
        super.setText(component);
        return this;
    }
}