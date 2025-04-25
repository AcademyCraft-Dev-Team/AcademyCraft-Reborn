package org.academy.api.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import org.academy.api.client.gui.framework.AbstractWidget;

public class LabelWidget extends AbstractWidget {
    public String value;
    public int color = 0xFFFFFFFF;
    public boolean dropShadow = true;
    public float scale = 1.0f;

    public LabelWidget(String value, float x, float y) {
        super(x, y, Minecraft.getInstance().font.width(FormattedText.of(value)), Minecraft.getInstance().font.lineHeight);
        this.value = value;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        guiGraphics.pose().pushPose();
        Font font = Minecraft.getInstance().font;
        float textHeight = font.lineHeight;
        float scaledHeight = textHeight * scale;
        float offsetY = (scaledHeight - textHeight) / 2;
        guiGraphics.pose().translate(x, y - offsetY, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        Minecraft.getInstance().font.drawInBatch(value, 0, 0, color, dropShadow,
                guiGraphics.pose().last().pose(),
                guiGraphics.bufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );
        guiGraphics.pose().popPose();
    }
}