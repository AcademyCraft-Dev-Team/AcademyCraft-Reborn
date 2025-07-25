package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.render.MatrixStack;

public class AutoScaleLabelWidget extends LabelWidget {
    private boolean centered;

    public AutoScaleLabelWidget(String value, float x, float y, float renderAreaWidth) {
        this(value, x, y, renderAreaWidth, false);
    }

    public AutoScaleLabelWidget(String value, float x, float y, float renderAreaWidth, boolean newCentered) {
        super(value, x, y);
        setWidth(renderAreaWidth);
        centered = newCentered;
        updateScaleAndDimensions();
    }

    private void updateScaleAndDimensions() {
        var font = Minecraft.getInstance().font;
        var newScale = 1.0f;
        var componentWidth = getWidth();

        if (value == null || value.isEmpty()) {
            scale = newScale;
            height = font.lineHeight * scale;
            return;
        }

        var textOriginalWidth = font.width(FormattedText.of(value));

        if (componentWidth > 0 && textOriginalWidth > 0) {
            if (textOriginalWidth * newScale > componentWidth) {
                newScale = componentWidth / textOriginalWidth;
            }
        }

        scale = Math.max(0.1f, newScale);
        height = font.lineHeight * scale;
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || value == null || value.isEmpty()) return;

        var baseAlpha = (color >> 24) & 0xFF;
        var finalAlpha = (int) (baseAlpha * getAbsoluteAlpha());
        if (finalAlpha <= 3) finalAlpha = 4;
        var finalColor = (color & 0x00FFFFFF) | (finalAlpha << 24);

        stack.pushPose();
        var font = Minecraft.getInstance().font;

        var finalScale = scale * globalScale;
        var currentTextActualWidth = font.width(FormattedText.of(value)) * finalScale;

        var renderX = getX();
        if (centered && getWidth() > 0) {
            renderX = getX() + (getWidth() - currentTextActualWidth) / 2.0f;
        }

        var textOriginalHeight = font.lineHeight;
        var scaledTextRenderHeight = textOriginalHeight * finalScale;
        var yDrawingOffset = (scaledTextRenderHeight - textOriginalHeight) / 2.0f;

        stack.translate(renderX, getY() - yDrawingOffset, getZ());
        stack.scale(finalScale, finalScale, 1.0f);

        font.drawInBatch(value, 0, 0, finalColor, dropShadow,
                stack.lastMatrix(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );
        stack.popPose();
    }

    public void setCentered(boolean newCentered) {
        centered = newCentered;
    }

    public boolean isCentered() {
        return centered;
    }

    public void setText(String newText) {
        value = newText;
        updateScaleAndDimensions();
    }

    @Override
    public Widget setWidth(float newRenderAreaWidth) {
        super.setWidth(newRenderAreaWidth);
        updateScaleAndDimensions();
        return this;
    }
}