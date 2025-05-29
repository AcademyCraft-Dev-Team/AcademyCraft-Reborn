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
        super.setWidth(renderAreaWidth);
        this.centered = centered;
        updateScaleAndDimensions();
    }

    private void updateScaleAndDimensions() {
        Font font = Minecraft.getInstance().font;
        float newScale = 1.0f;
        float componentWidth = this.getWidth();

        if (this.value == null || this.value.isEmpty()) {
            this.scale = newScale;
            this.height = font.lineHeight * this.scale;
            return;
        }

        float textOriginalWidth = font.width(FormattedText.of(this.value));

        if (componentWidth > 0 && textOriginalWidth > 0) {
            if (textOriginalWidth * newScale > componentWidth) {
                newScale = componentWidth / textOriginalWidth;
            }
        }

        this.scale = Math.max(0.1f, newScale);
        this.height = font.lineHeight * this.scale;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        if (!isVisible() || this.value == null || this.value.isEmpty()) return;

        if (animation != null) {
            animation.beforeRender(guiGraphics, mouseX, mouseY, partialTicks);
        }

        guiGraphics.pose().pushPose();
        Font font = Minecraft.getInstance().font;

        float finalScale = this.scale * globalScale;
        float currentTextActualWidth = font.width(FormattedText.of(this.value)) * finalScale;

        float renderX = this.getX();
        if (this.centered && this.getWidth() > 0) {
            renderX = this.getX() + (this.getWidth() - currentTextActualWidth) / 2.0f;
        }

        float textOriginalHeight = font.lineHeight;
        float scaledTextRenderHeight = textOriginalHeight * finalScale;
        float yDrawingOffset = (scaledTextRenderHeight - textOriginalHeight) / 2.0f;

        guiGraphics.pose().translate(renderX, this.getY() - yDrawingOffset, 0);
        guiGraphics.pose().scale(finalScale, finalScale, 1.0f);

        font.drawInBatch(this.value, 0, 0, this.color, this.dropShadow,
                guiGraphics.pose().last().pose(),
                guiGraphics.bufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );
        guiGraphics.pose().popPose();

        if (animation != null) {
            animation.afterRender(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public boolean isCentered() {
        return this.centered;
    }

    public void setText(String text) {
        this.value = text;
        updateScaleAndDimensions();
    }

    @Override
    public void setWidth(float newRenderAreaWidth) {
        super.setWidth(newRenderAreaWidth);
        updateScaleAndDimensions();
    }
}