package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;

public class EllipsisLabelWidget extends LabelWidget {
    private static final String ELLIPSIS = "...";

    public EllipsisLabelWidget(String value, float x, float y, float maxWidth) {
        super(value, x, y);
        Font font = Minecraft.getInstance().font;

        if (font.width(FormattedText.of(value)) > maxWidth) {
            String trimmedText = value;
            while (font.width(FormattedText.of(trimmedText + ELLIPSIS)) > maxWidth && !trimmedText.isEmpty()) {
                trimmedText = trimmedText.substring(0, trimmedText.length() - 1);
            }
            this.value = trimmedText + ELLIPSIS;
        }
    }
}