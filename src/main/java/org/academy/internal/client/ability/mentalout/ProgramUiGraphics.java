package org.academy.internal.client.ability.mentalout;

import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.render.ScissorRect;
import org.academy.api.client.gui.util.GlyphCommandGenerator;
import org.academy.api.client.gui.widget.LabelWidget;

import java.util.ArrayList;
import java.util.List;

/** Small immediate-style adapter backed by the Academy UI command renderer. */
final class ProgramUiGraphics {
    static final float BODY_FONT_SIZE = 7.5f;
    static final float CAPTION_FONT_SIZE = 6.75f;
    static final float HEADING_FONT_SIZE = 8.5f;
    private static final String ELLIPSIS = "…";

    private final RenderContext context;

    ProgramUiGraphics(RenderContext context) {
        this.context = context;
    }

    RenderContext.PoseStack2D pose() {
        return context.pose();
    }

    void fill(int left, int top, int right, int bottom, int color) {
        var width = right - left;
        var height = bottom - top;
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        context.pose().pushPose();
        context.pose().translate(left, top);
        context.submit(new FillRectDrawCommand(
                width,
                height,
                red(color),
                green(color),
                blue(color),
                alpha(color) * context.getAccumulatedAlpha()
        ));
        context.pose().popPose();
    }

    void enableScissor(int left, int top, int right, int bottom) {
        context.enableScissor(new ScissorRect(left, top,
                Math.max(0, right - left), Math.max(0, bottom - top)));
    }

    void disableScissor() {
        context.disableScissor();
    }

    void text(String value, float x, float y, int color, float fontSize, float maxWidth) {
        var clipped = fit(value, maxWidth, fontSize);
        if (clipped.isEmpty()) return;
        // Commands sharing a draw order are regrouped by render pipeline. Keep glyphs one
        // layer above preceding translucent fills so batching cannot dim the text.
        context.drawOrder().advance(1);
        context.pose().pushPose();
        context.pose().translate(x, y);
        var commands = GlyphCommandGenerator.INSTANCE.generate(
                clipped,
                fontSize,
                0.0f,
                red(color),
                green(color),
                blue(color),
                alpha(color) * context.getAccumulatedAlpha()
        );
        for (var command : commands) context.submit(command);
        context.pose().popPose();
    }

    void centeredText(String value, float centerX, float y, int color, float fontSize, float maxWidth) {
        var clipped = fit(value, maxWidth, fontSize);
        var width = LabelWidget.Companion.getTextWidth(clipped, fontSize);
        text(clipped, centerX - width / 2.0f, y, color, fontSize, maxWidth);
    }

    static String fit(String value, float maxWidth, float fontSize) {
        if (value == null || value.isEmpty() || maxWidth <= 0.0f) return "";
        if (LabelWidget.Companion.getTextWidth(value, fontSize) <= maxWidth) return value;
        var ellipsisWidth = LabelWidget.Companion.getTextWidth(ELLIPSIS, fontSize);
        if (ellipsisWidth > maxWidth) return "";
        var low = 0;
        var high = value.codePointCount(0, value.length());
        while (low < high) {
            var mid = (low + high + 1) >>> 1;
            var end = value.offsetByCodePoints(0, mid);
            var candidate = value.substring(0, end) + ELLIPSIS;
            if (LabelWidget.Companion.getTextWidth(candidate, fontSize) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return value.substring(0, value.offsetByCodePoints(0, low)) + ELLIPSIS;
    }

    static List<String> wrap(String value, float maxWidth, float fontSize) {
        var output = new ArrayList<String>();
        if (value == null || value.isEmpty()) {
            output.add("");
            return output;
        }
        for (var paragraph : value.split("\\n", -1)) wrapParagraph(paragraph, maxWidth, fontSize, output);
        return List.copyOf(output);
    }

    static int wrappedHeight(String value, float maxWidth, float fontSize, float lineHeight) {
        return Math.round(wrap(value, maxWidth, fontSize).size() * lineHeight);
    }

    private static void wrapParagraph(
            String paragraph,
            float maxWidth,
            float fontSize,
            List<String> output
    ) {
        if (paragraph.isEmpty()) {
            output.add("");
            return;
        }
        var remaining = paragraph;
        while (!remaining.isEmpty()) {
            if (LabelWidget.Companion.getTextWidth(remaining, fontSize) <= maxWidth) {
                output.add(remaining.stripTrailing());
                return;
            }
            var offset = 0;
            var lastFittingOffset = 0;
            var lastPreferredBreak = 0;
            var overflowAtWhitespace = false;
            while (offset < remaining.length()) {
                var codePoint = remaining.codePointAt(offset);
                var nextOffset = offset + Character.charCount(codePoint);
                if (LabelWidget.Companion.getTextWidth(
                        remaining.substring(0, nextOffset), fontSize) > maxWidth) {
                    overflowAtWhitespace = Character.isWhitespace(codePoint);
                    break;
                }
                lastFittingOffset = nextOffset;
                if (preferredBreak(codePoint)) lastPreferredBreak = nextOffset;
                offset = nextOffset;
            }
            var breakOffset = overflowAtWhitespace && lastFittingOffset > 0
                    ? lastFittingOffset
                    : lastPreferredBreak > 0
                    ? lastPreferredBreak
                    : lastFittingOffset > 0
                    ? lastFittingOffset
                    : Character.charCount(remaining.codePointAt(0));
            output.add(remaining.substring(0, breakOffset).stripTrailing());
            remaining = remaining.substring(breakOffset).stripLeading();
        }
    }

    private static boolean preferredBreak(int codePoint) {
        if (Character.isWhitespace(codePoint)) return true;
        if (codePoint >= 0x2E80 && codePoint <= 0x9FFF
                || codePoint >= 0xF900 && codePoint <= 0xFAFF) return true;
        return "-/,.;:!?，。；：！？、）】》".indexOf(codePoint) >= 0;
    }

    private static float alpha(int color) {
        return (color >>> 24 & 0xFF) / 255.0f;
    }

    private static float red(int color) {
        return (color >>> 16 & 0xFF) / 255.0f;
    }

    private static float green(int color) {
        return (color >>> 8 & 0xFF) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0f;
    }
}
