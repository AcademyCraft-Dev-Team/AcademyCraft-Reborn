package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class HoverLabelWidget extends LabelWidget {
    private static final String ELLIPSIS = "...";

    private final Component originalComponent;
    private final Component truncatedComponent;
    private final float maxWidth;
    private float baseScale = 1.0f;

    public HoverLabelWidget(String text, float x, float y, float maxWidth) {
        super(Component.empty(), x, y, maxWidth, Minecraft.getInstance().font.lineHeight);
        originalComponent = Component.literal(text);
        this.maxWidth = maxWidth;
        truncatedComponent = truncate(originalComponent, maxWidth);
        setText(truncatedComponent);
        setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Component truncate(Component source, float maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(source) <= maxWidth) {
            return source;
        }
        var originalText = source.getString();
        var ellipsisWidth = font.width(ELLIPSIS);
        var trimmedText = font.plainSubstrByWidth(originalText, (int) (maxWidth - ellipsisWidth));
        return Component.literal(trimmedText + ELLIPSIS);
    }

    @Override
    public void setHovered(boolean hovered) {
        if (isHovered() == hovered) {
            return;
        }
        super.setHovered(hovered);

        if (hovered) {
            setText(originalComponent);

            var font = Minecraft.getInstance().font;
            var textWidth = font.width(originalComponent);

            if (textWidth > maxWidth) {
                setScale(maxWidth / (float) textWidth);
            } else {
                setScale(1.0f);
            }
        } else {
            setText(truncatedComponent);
            setScale(baseScale);
        }
    }

    public HoverLabelWidget setBaseScale(float scale) {
        baseScale = scale;
        if (!isHovered()) {
            setScale(scale);
        }
        return this;
    }

    @Override
    public final LabelWidget setText(String text) {
        throw new UnsupportedOperationException("Cannot set text directly on a HoverLabelWidget.");
    }
}