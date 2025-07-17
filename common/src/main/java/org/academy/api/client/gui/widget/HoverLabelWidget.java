package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import org.academy.api.client.render.MatrixStack;

public class HoverLabelWidget extends LabelWidget {
    private static final String ELLIPSIS = "...";
    public float maxWidth;
    public String originalValue;

    public HoverLabelWidget(String value, float x, float y, float newMaxWidth) {
        super(value, x, y);
        originalValue = value;
        maxWidth = newMaxWidth;
    }

    private void updateText() {
        var font = Minecraft.getInstance().font;
        if (isHovered()) {
            var newScale = 1.0f;
            while (font.width(FormattedText.of(originalValue)) * newScale > maxWidth && newScale > 0.1f) newScale -= 0.1f;
            newScale = Math.max(newScale, 0.1f);
            scale = newScale;
            value = originalValue;
        } else {
            var trimmedText = originalValue;
            while (font.width(FormattedText.of(trimmedText + ELLIPSIS)) > maxWidth && !trimmedText.isEmpty()) {
                trimmedText = trimmedText.substring(0, trimmedText.length() - 1);
            }
            value = trimmedText + ELLIPSIS;
            scale = 1f;
        }
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        updateText();
        super.render(stack, bufferSource, mouseX, mouseY, partialTick);
    }
}