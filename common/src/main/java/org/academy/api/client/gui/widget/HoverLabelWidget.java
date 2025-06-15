package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;

public class HoverLabelWidget extends LabelWidget {
    private static final String ELLIPSIS = "...";
    public float maxWidth;
    public String originalValue;

    public HoverLabelWidget(String value, float x, float y, float maxWidth) {
        super(value, x, y);
        this.originalValue = value;
        this.maxWidth = maxWidth;
    }

    private void updateText() {
        Font font = Minecraft.getInstance().font;
        if (isHovered()) {
            float scale = 1.0f;
            while (font.width(FormattedText.of(originalValue)) * scale > maxWidth && scale > 0.1f) scale -= 0.1f;
            scale = Math.max(scale, 0.1f);
            this.scale = scale;
            this.value = originalValue;
        } else {
            String trimmedText = originalValue;
            while (font.width(FormattedText.of(trimmedText + ELLIPSIS)) > maxWidth && !trimmedText.isEmpty()) {
                trimmedText = trimmedText.substring(0, trimmedText.length() - 1);
            }
            this.value = trimmedText + ELLIPSIS;
            this.scale = 1f;
        }
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks) {
        updateText();
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}