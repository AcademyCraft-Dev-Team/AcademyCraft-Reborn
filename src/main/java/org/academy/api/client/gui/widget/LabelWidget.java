package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.render.WidgetRenderContext;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.util.GlyphCommandGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LabelWidget extends AbstractWidget {
    protected Component component;
    protected int color = 0xFFFFFFFF;
    protected boolean dropShadow = false;
    public static float globalScale = 1.0f;
    protected float scale = 1.0f;
    protected float lastFinalColor;
    @Nullable
    protected FormattedCharSequence lastText = null;
    protected List<DrawCommand> drawCommands = new ArrayList<>();

    public LabelWidget(String text) {
        this(Component.literal(text));
    }

    public LabelWidget(Component component) {
        this.component = component;
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var font = Minecraft.getInstance().font;
        var finalScale = scale * globalScale;

        var desiredWidth = font.width(component) * finalScale;
        var desiredHeight = font.lineHeight * finalScale;

        var lp = getLayoutParams();
        desiredWidth += lp.paddingLeft + lp.paddingRight;
        desiredHeight += lp.paddingTop + lp.paddingBottom;

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible() || component.getString().isEmpty()) {
            return;
        }

        var lp = getLayoutParams();
        var font = Minecraft.getInstance().font;
        var textToRender = component.getVisualOrderText();

        var finalScale = scale * globalScale;
        var visualTextWidth = font.width(textToRender) * finalScale;
        var visualTextHeight = font.lineHeight * finalScale;

        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var availableHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        var alignmentOffsetX = 0f;
        var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) {
            alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f;
        } else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0) {
            alignmentOffsetX = availableWidth - visualTextWidth;
        }

        var alignmentOffsetY = 0f;
        var verticalGravity = (lp.gravity >> Gravity.AXIS_Y_SHIFT) & 0x7;
        if (verticalGravity == Gravity.AXIS_SPECIFIED) {
            alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f;
        } else if ((verticalGravity & Gravity.AXIS_PULL_AFTER) != 0) {
            alignmentOffsetY = availableHeight - visualTextHeight;
        }

        var finalAlpha = getAlpha() * context.getAccumulatedAlpha();

        context.pose().pushPose();
        {
            context.pose().translate(lp.paddingLeft + alignmentOffsetX, lp.paddingTop + alignmentOffsetY + 1, 0);
            context.pose().scale(finalScale, finalScale, 1.0f);

            var baseAlphaComponent = ARGB.alpha(color);
            var effectiveAlpha = (int) (baseAlphaComponent * finalAlpha);
            var finalColor = (color & 0x00FFFFFF) | (effectiveAlpha << 24);

            if (finalColor != lastFinalColor || !textToRender.equals(lastText)) {
                drawCommands = GlyphCommandGenerator.generate(font, textToRender, 0, 0, finalColor, dropShadow);
                lastFinalColor = finalColor;
                lastText = textToRender;
            }

            for (var command : drawCommands) {
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

    public LabelWidget setText(String text) {
        return setText(Component.literal(text));
    }

    public LabelWidget setText(Component component) {
        if (!this.component.equals(component)) {
            this.component = component;
            requestLayout();
        }
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
        if (this.scale != scale) {
            this.scale = scale;
            requestLayout();
        }
        return this;
    }
}