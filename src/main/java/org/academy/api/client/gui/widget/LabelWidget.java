package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.academy.api.client.gui.util.GlyphCommandGenerator;

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

    protected Component component;
    protected int color = 0xFFFFFFFF;
    protected boolean dropShadow = false;
    public static float globalScale = 1.0f;
    protected float scale = 1.0f;
    protected Alignment alignment = Alignment.LEFT;
    protected VerticalAlignment verticalAlignment = VerticalAlignment.TOP;

    public LabelWidget(String text, float x, float y) {
        this(Component.literal(text), x, y);
    }

    public LabelWidget(Component component, float x, float y) {
        super(x, y, 0, 0);
        this.component = component;
        var font = Minecraft.getInstance().font;
        setWidth(font.width(component));
        setHeight(font.lineHeight);
    }

    public LabelWidget(Component component, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.component = component;
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || component.getString().isEmpty()) {
            return;
        }

        var font = Minecraft.getInstance().font;
        var textToRender = component.getVisualOrderText();

        var totalWidth = font.width(textToRender);
        var finalScale = scale * globalScale;
        var visualTextWidth = totalWidth * finalScale;
        var visualTextHeight = font.lineHeight * finalScale;

        var alignmentOffsetX = 0f;
        if (alignment == Alignment.CENTER) {
            alignmentOffsetX = (getWidth() - visualTextWidth) / 2.0f;
        } else if (alignment == Alignment.RIGHT) {
            alignmentOffsetX = getWidth() - visualTextWidth;
        }

        var alignmentOffsetY = 0f;
        if (verticalAlignment == VerticalAlignment.MIDDLE) {
            alignmentOffsetY = (getHeight() - visualTextHeight) / 2.0f;
        } else if (verticalAlignment == VerticalAlignment.BOTTOM) {
            alignmentOffsetY = getHeight() - visualTextHeight;
        }

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        context.pose().pushPose();
        {
            context.pose().translate(getX() + alignmentOffsetX, getY() + alignmentOffsetY, getZ());
            context.pose().scale(finalScale, finalScale, 1.0f);

            var baseAlphaComponent = ARGB.alpha(color);
            var effectiveAlpha = (int) (baseAlphaComponent * finalAlpha);
            var finalColor = (color & 0x00FFFFFF) | (effectiveAlpha << 24);

            var commands = GlyphCommandGenerator.generate(font, textToRender, 0, 0, finalColor, dropShadow);

            for (var command : commands) {
                context.submit(command);
            }
        }
        context.pose().popPose();
    }

    public Component getComponent() {
        return component;
    }

    public String getText() {
        return component.getString();
    }

    public int getColor() {
        return color;
    }

    public float getScale() {
        return scale;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public VerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public LabelWidget setText(String text) {
        return setText(Component.literal(text));
    }

    public LabelWidget setText(Component component) {
        this.component = component;
        return this;
    }

    public LabelWidget setColor(int color) {
        this.color = color;
        return this;
    }

    public LabelWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    public LabelWidget setScale(float scale) {
        this.scale = scale;
        return this;
    }

    public LabelWidget setAlignment(Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    public LabelWidget setVerticalAlignment(VerticalAlignment verticalAlignment) {
        this.verticalAlignment = verticalAlignment;
        return this;
    }
}