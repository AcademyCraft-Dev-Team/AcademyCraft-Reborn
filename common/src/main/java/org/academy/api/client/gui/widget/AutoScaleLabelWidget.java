package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;

public class AutoScaleLabelWidget extends LabelWidget {
    public AutoScaleLabelWidget(String value, float x, float y, float maxWidth) {
        super(value, x, y);
        Font font = Minecraft.getInstance().font;
        float scale = 1.0f;

        while (font.width(FormattedText.of(value)) * scale > maxWidth && scale > 0.1f) {
            scale -= 0.1f;
        }
        this.scale = scale;
        this.width = font.width(FormattedText.of(value)) * scale;
        this.height = font.lineHeight * scale;
    }
}