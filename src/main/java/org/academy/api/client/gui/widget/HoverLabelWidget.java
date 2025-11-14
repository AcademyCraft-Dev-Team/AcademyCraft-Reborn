package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.render.RenderContext;

public class HoverLabelWidget extends LabelWidget {
    private static final String ELLIPSIS = "...";

    private final Component originalComponent;
    private float baseScale = 1.0f;

    public HoverLabelWidget(String text) {
        this(Component.literal(text));
    }

    public HoverLabelWidget(Component component) {
        super(component);
        originalComponent = component;
    }

    @Override
    public void render(RenderContext context, double mouseX, double mouseY, float partialTick) {
        if (isHovered()) {
            component = originalComponent;
            updateScaleForHover();
        } else {
            component = truncate(originalComponent, getWidth());
            scale = baseScale;
        }
        super.render(context, mouseX, mouseY, partialTick);
    }

    private void updateScaleForHover() {
        var font = Minecraft.getInstance().font;
        var textWidth = font.width(originalComponent);
        var availableWidth = getWidth();

        if (textWidth > availableWidth && availableWidth > 0) {
            scale = availableWidth / (float) textWidth;
        } else {
            scale = 1.0f;
        }
    }

    private static Component truncate(Component source, float maxWidth) {
        var font = Minecraft.getInstance().font;
        if (maxWidth <= 0 || font.width(source) <= maxWidth) {
            return source;
        }
        var originalText = source.getString();
        var ellipsisWidth = font.width(ELLIPSIS);
        var trimmedText = font.plainSubstrByWidth(originalText, (int) (maxWidth - ellipsisWidth));
        return Component.literal(trimmedText + ELLIPSIS);
    }

    public HoverLabelWidget setBaseScale(float scale) {
        baseScale = scale;
        return this;
    }

    @Override
    public final LabelWidget setText(String text) {
        throw new UnsupportedOperationException("Cannot set text directly on a HoverLabelWidget.");
    }

    @Override
    public final LabelWidget setText(Component component) {
        throw new UnsupportedOperationException("Cannot set text directly on a HoverLabelWidget.");
    }
}