package org.academy.api.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import org.academy.api.client.gui.framework.AbstractWidget;

public class LabelWidget extends AbstractWidget {
    public String value;
    public int color = -524296;
    public boolean dropShadow = true;

    public LabelWidget(String value, float x, float y) {
        super(x, y, Minecraft.getInstance().font.width(FormattedText.of(value)), Minecraft.getInstance().font.lineHeight);
        this.value = value;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        Minecraft.getInstance().font.drawInBatch(value, x, y, color, dropShadow, guiGraphics.pose().last().pose(), guiGraphics.bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
    }
}