package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;

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
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var baseAlpha = (color >> 24) & 0xFF;
        var finalAlpha = (int) (baseAlpha * getAbsoluteAlpha());
        if (finalAlpha <= 3) finalAlpha = 4;
        var finalColor = (color & 0x00FFFFFF) | (finalAlpha << 24);

        stack.pushPose();
        var font = Minecraft.getInstance().font;
        var finalScale = scale * globalScale;
        var textHeight = font.lineHeight;
        var scaledHeight = textHeight * finalScale;
        var offsetY = (scaledHeight - textHeight) / 2;
        stack.translate(x, y - offsetY, 0);
        stack.scale(finalScale, finalScale, 1.0f);
        Minecraft.getInstance().font.drawInBatch(value, 0, 0, finalColor, dropShadow,
                stack.lastMatrix(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );
        stack.popPose();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}