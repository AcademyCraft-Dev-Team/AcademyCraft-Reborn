package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;

public class AutoScaleLabelWidget extends LabelWidget {
    private boolean centered;

    public AutoScaleLabelWidget(String value, float x, float y, float renderAreaWidth) {
        this(value, x, y, renderAreaWidth, false);
    }

    public AutoScaleLabelWidget(String value, float x, float y, float renderAreaWidth, boolean centered) {
        super(value, x, y);
        setWidth(renderAreaWidth);
        this.centered = centered;
        updateScaleAndDimensions();
    }

    private void updateScaleAndDimensions() {
        var font = Minecraft.getInstance().font;
        float newScale = 1.0f;
        float componentWidth = getWidth();

        if (value == null || value.isEmpty()) {
            scale = newScale;
            height = font.lineHeight * scale;
            return;
        }

        float textOriginalWidth = font.width(FormattedText.of(value));

        if (componentWidth > 0 && textOriginalWidth > 0) {
            if (textOriginalWidth * newScale > componentWidth) {
                newScale = componentWidth / textOriginalWidth;
            }
        }

        scale = Math.max(0.1f, newScale);
        height = font.lineHeight * scale;
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks) {
        if (!isVisible() || value == null || value.isEmpty()) return;

        if (animation != null) {
            animation.beforeRender(graphics, mouseX, mouseY, partialTicks);
        }

        graphics.pose().pushPose();
        var font = Minecraft.getInstance().font;

        float finalScale = scale * globalScale;
        float currentTextActualWidth = font.width(FormattedText.of(value)) * finalScale;

        float renderX = getX();
        if (centered && getWidth() > 0) {
            renderX = getX() + (getWidth() - currentTextActualWidth) / 2.0f;
        }

        float textOriginalHeight = font.lineHeight;
        float scaledTextRenderHeight = textOriginalHeight * finalScale;
        float yDrawingOffset = (scaledTextRenderHeight - textOriginalHeight) / 2.0f;

        graphics.pose().translate(renderX, getY() - yDrawingOffset, getZ());
        graphics.pose().scale(finalScale, finalScale, 1.0f);

        font.drawInBatch(value, 0, 0, color, dropShadow,
                graphics.pose().last().pose(),
                graphics.bufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );
        graphics.pose().popPose();

        if (animation != null) {
            animation.afterRender(graphics, mouseX, mouseY, partialTicks);
        }
    }

    public void setCentered(boolean value) {
        centered = value;
    }

    public boolean isCentered() {
        return centered;
    }

    public void setText(String text) {
        value = text;
        updateScaleAndDimensions();
    }

    @Override
    public void setWidth(float newRenderAreaWidth) {
        super.setWidth(newRenderAreaWidth);
        updateScaleAndDimensions();
    }
}