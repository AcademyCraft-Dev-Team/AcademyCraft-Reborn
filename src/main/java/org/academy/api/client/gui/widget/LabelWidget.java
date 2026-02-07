package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.msdf.core.MsdfConstants;
import org.academy.api.client.gui.msdf.font.MsdfFont;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.msdf.font.MsdfKerningManager;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.util.GlyphCommandGenerator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.gui.msdf.font.MsdfFontService.getDefaultFont;

public class LabelWidget extends AbstractWidget {
    protected final float baseFontSize = 12.0f;
    protected String text;
    protected float layoutScale = 1.0f;
    protected boolean dropShadow = false;
    protected float scale = 1.0f;
    @Nullable
    protected String lastText = null;
    private float red = 1, green = 1, blue = 1, lastFinalAlpha = 1;
    protected List<DrawCommand> drawCommands = new ArrayList<>();
    private boolean colorChanged = false;

    public LabelWidget(String text) {
        this.text = text;
    }

    protected float calculateLayoutScale(float baseTextWidth, float baseTextHeight, float constraintWidth, float constraintHeight) {
        var scaleX = 1.0f;
        var scaleY = 1.0f;

        if (constraintWidth > 0 && constraintWidth < Float.MAX_VALUE) {
            scaleX = constraintWidth / baseTextWidth;
        }

        if (constraintHeight > 0 && constraintHeight < Float.MAX_VALUE) {
            scaleY = constraintHeight / baseTextHeight;
        }

        var finalScale = Math.min(scaleX, scaleY);

        return Math.max(0.0f, Math.min(1.0f, finalScale));
    }

    protected float getTextWidth(String text) {
        var lines = text.split("\n", -1);
        float maxWidth = 0;

        for (var line : lines) {
            var lineWidth = 0f;
            var prevChar = 0;
            MsdfFont prevFont = null;

            for (var i = 0; i < line.codePointCount(0, line.length()); i++) {
                var c = line.codePointAt(i);
                var font = MsdfFontService.getFont(c);
                var glyph = font.getGlyph(c);
                if (glyph == null) continue;

                var metrics = font.getMetrics();
                var fontUnitScale = baseFontSize / metrics.unitsPerEm();

                if (prevChar != 0 && prevFont == font) {
                    lineWidth += MsdfKerningManager.getKerning(font.getFace(), prevChar, c) * fontUnitScale;
                }
                lineWidth += glyph.advance() * fontUnitScale;
                prevChar = c;
                prevFont = font;
            }
            maxWidth = Math.max(maxWidth, lineWidth);
        }

        return maxWidth;
    }

    protected float getTextHeight(String text) {
        var lines = text.split("\n", -1);
        var font = getDefaultFont();
        var metrics = font.getMetrics();
        var fontUnitScale = baseFontSize / metrics.unitsPerEm();

        var totalHeight = (metrics.ascender() - metrics.descender()) * fontUnitScale;
        if (lines.length > 1) {
            totalHeight += (lines.length - 1) * metrics.lineHeight() * fontUnitScale;
        }
        totalHeight += MsdfConstants.DEFAULT_PX_RANGE * 2 * fontUnitScale;

        return totalHeight;
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var lp = getLayoutParams();
        if (text.isEmpty()) {
            layoutScale = 1;
            setMeasuredDimension(resolveSize(lp.paddingLeft + lp.paddingRight, widthMeasureSpec),
                    resolveSize(lp.paddingTop + lp.paddingBottom, heightMeasureSpec));
            return;
        }

        var baseTextWidth = getTextWidth(text);
        var baseTextHeight = getTextHeight(text);

        var constraintWidth = (widthMeasureSpec.getMode() == MeasureSpec.Mode.UNSPECIFIED)
                ? Float.MAX_VALUE : (widthMeasureSpec.getSize() - lp.paddingLeft - lp.paddingRight);
        var constraintHeight = (heightMeasureSpec.getMode() == MeasureSpec.Mode.UNSPECIFIED)
                ? Float.MAX_VALUE : (heightMeasureSpec.getSize() - lp.paddingTop - lp.paddingBottom);

        layoutScale = calculateLayoutScale(baseTextWidth, baseTextHeight, constraintWidth, constraintHeight);

        var measuredWidth = baseTextWidth * layoutScale + lp.paddingLeft + lp.paddingRight;
        var measuredHeight = baseTextHeight * layoutScale + lp.paddingTop + lp.paddingBottom;

        setMeasuredDimension(
                resolveSize(measuredWidth, widthMeasureSpec),
                resolveSize(measuredHeight, heightMeasureSpec)
        );
    }

    @Override
    public void render(RenderContext context) {
        super.render(context);
        if (!isVisible() || text.isEmpty()) return;

        var lp = getLayoutParams();
        var baseTextWidth = getTextWidth(text);
        var baseTextHeight = getTextHeight(text);

        var finalScale = scale * layoutScale;

        var visualTextWidth = baseTextWidth * finalScale;
        var visualTextHeight = baseTextHeight * finalScale;

        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var availableHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        var alignmentOffsetX = 0f;
        var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f;
        else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetX = availableWidth - visualTextWidth;

        var alignmentOffsetY = 0f;
        var verticalGravity = (lp.gravity >> Gravity.AXIS_Y_SHIFT) & 0x7;
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f;
        else if ((verticalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetY = availableHeight - visualTextHeight;

        context.pose().pushPose();
        context.drawOrder().push();
        {
            context.drawOrder().advance();

            var textTopY = lp.paddingTop + alignmentOffsetY;
            context.pose().translate(lp.paddingLeft + alignmentOffsetX, textTopY, 0);
            context.pose().scale(finalScale, finalScale, 1.0f);

            var finalAlpha = getAlpha() * context.getAccumulatedAlpha();
            if (colorChanged || lastFinalAlpha != finalAlpha || !text.equals(lastText)) {
                drawCommands = GlyphCommandGenerator.generate(
                        text, baseFontSize, 0, red, green, blue, finalAlpha
                );
                lastText = text;
                colorChanged = false;
            }
            lastFinalAlpha = finalAlpha;

            for (var command : drawCommands) context.submit(command);
        }
        context.drawOrder().pop();
        context.pose().popPose();
    }

    public String getText() {
        return text;
    }

    public LabelWidget setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
            requestLayout();
        }
        return this;
    }

    public float getRed() {
        return red;
    }

    public void setRed(float red) {
        if (red != this.red) {
            this.red = red;
            colorChanged = true;
        }
    }

    public float getBlue() {
        return blue;
    }

    public void setBlue(float blue) {
        if (blue != this.blue) {
            this.blue = blue;
            colorChanged = true;
        }
    }

    public float getGreen() {
        return green;
    }

    public float getScale() {
        return scale;
    }

    public void setGreen(float green) {
        if (green != this.green) {
            this.green = green;
            colorChanged = true;
        }
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