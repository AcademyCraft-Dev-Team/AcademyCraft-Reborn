package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;

public class LabelWidget extends AbstractWidget {

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum VerticalAlignment {
        TOP,
        MIDDLE,
        BOTTOM
    }

    private Component component;
    private int color = 0xFFFFFFFF;
    private boolean dropShadow = true;
    public static float globalScale = 1.0f;
    private float scale = 1.0f;
    private Alignment alignment = Alignment.LEFT;
    private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;

    public LabelWidget(@NotNull String text, float x, float y) {
        this(Component.literal(text), x, y);
    }

    public LabelWidget(@NotNull Component component, float x, float y) {
        super(x, y, 0, 0);
        this.component = component;

        var font = Minecraft.getInstance().font;
        this.setWidth(font.width(this.component));
        this.setHeight(font.lineHeight);
    }

    public LabelWidget(@NotNull Component component, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.component = component;
    }

    @Override
    public void render(@NotNull MatrixStack stack, @NotNull MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || component.getString().isEmpty()) {
            return;
        }

        var baseAlpha = (color >> 24) & 0xFF;
        var finalAlpha = (int) (baseAlpha * getAbsoluteAlpha());
        if (finalAlpha < 4) {
            return;
        }
        var finalColor = (color & 0x00FFFFFF) | (finalAlpha << 24);

        var font = Minecraft.getInstance().font;
        var finalScale = this.scale * globalScale;

        var visualTextWidth = font.width(component) * finalScale;
        var visualTextHeight = font.lineHeight * finalScale;

        float renderX = 0;
        if (this.alignment == Alignment.CENTER) {
            renderX = (this.getWidth() - visualTextWidth) / 2.0f;
        } else if (this.alignment == Alignment.RIGHT) {
            renderX = this.getWidth() - visualTextWidth;
        }

        float renderY = 0;
        if (this.verticalAlignment == VerticalAlignment.MIDDLE) {
            renderY = (this.getHeight() - visualTextHeight) / 2.0f;
        } else if (this.verticalAlignment == VerticalAlignment.BOTTOM) {
            renderY = this.getHeight() - visualTextHeight;
        }

        stack.pushPose();
        stack.translate(renderX, renderY, 0);
        stack.scale(finalScale, finalScale, 1.0f);

        font.drawInBatch(component, 0, 0, finalColor, dropShadow,
                stack.lastMatrix(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );

        stack.popPose();
    }

    @NotNull
    public Component getComponent() {
        return component;
    }

    @NotNull
    public String getText() {
        return component.getString();
    }

    public int getColor() {
        return this.color;
    }

    public float getScale() {
        return this.scale;
    }

    @NotNull
    public Alignment getAlignment() {
        return alignment;
    }

    @NotNull
    public LabelWidget setText(@NotNull String text) {
        return setText(Component.literal(text));
    }

    @NotNull
    public LabelWidget setText(@NotNull Component component) {
        this.component = component;
        return this;
    }

    @NotNull
    public LabelWidget setColor(int color) {
        this.color = color;
        return this;
    }

    @NotNull
    public LabelWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    @NotNull
    public LabelWidget setScale(float scale) {
        this.scale = scale;
        return this;
    }

    @NotNull
    public LabelWidget setAlignment(@NotNull Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    @NotNull
    public LabelWidget setVerticalAlignment(@NotNull VerticalAlignment verticalAlignment) {
        this.verticalAlignment = verticalAlignment;
        return this;
    }
}