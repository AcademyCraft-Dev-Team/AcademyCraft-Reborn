package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import org.academy.api.client.gui.framework.AbstractWidget;

public class LabelWidget extends AbstractWidget {
    public String value;
    public int color = 0xFFFFFFFF;
    public boolean dropShadow = true;
    public static float globalScale = 1.0f;
    public float scale = 1.0f;

    public LabelWidget(String newValue, float x, float y) {
        super(x, y, Minecraft.getInstance().font.width(FormattedText.of(newValue)), Minecraft.getInstance().font.lineHeight);
        value = newValue;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks) {
        if (!isVisible()) return;

        var baseAlpha = (color >> 24) & 0xFF;
        var finalAlpha = (int) (baseAlpha * getAbsoluteAlpha());
        // In Font.adjustColor, alpha <= 3 is forced to 255
        if (finalAlpha <= 3) finalAlpha = 4;
        var finalColor = (color & 0x00FFFFFF) | (finalAlpha << 24);

        graphics.pose().pushPose();
        var font = Minecraft.getInstance().font;
        var finalScale = scale * globalScale;
        var textHeight = font.lineHeight;
        var scaledHeight = textHeight * finalScale;
        var offsetY = (scaledHeight - textHeight) / 2;
        graphics.pose().translate(x, y - offsetY, 0);
        graphics.pose().scale(finalScale, finalScale, 1.0f);
        Minecraft.getInstance().font.drawInBatch(value, 0, 0, finalColor, dropShadow,
                graphics.pose().last().pose(),
                graphics.bufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );
        graphics.pose().popPose();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}