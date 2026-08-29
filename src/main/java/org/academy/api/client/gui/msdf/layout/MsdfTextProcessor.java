package org.academy.api.client.gui.msdf.layout;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph;
import org.academy.api.client.gui.msdf.font.MsdfFont;
import org.academy.api.client.gui.msdf.font.MsdfFontService;

import java.util.ArrayList;
import java.util.List;

public final class MsdfTextProcessor {
    private MsdfTextProcessor() {
    }

    public static LayoutResult layout(String text, float fontSize) {
        var lines = new ArrayList<LineInfo>();
        var currentLine = new LineInfo(0);

        var i = 0;
        while (i < text.length()) {
            var c = text.codePointAt(i);
            if (c == '\n') {
                currentLine.codeUnitEnd = i;
                lines.add(currentLine);
                currentLine = new LineInfo(i + 1);
                i++;
                continue;
            }

            var font = MsdfFontService.getFont(c);
            var glyph = font.getGlyph(c);
            if (glyph == null) {
                i += Character.charCount(c);
                continue;
            }

            var metrics = font.metrics;
            var unitsPerEM = metrics.unitsPerEm();
            if (unitsPerEM == 0) {
                i += Character.charCount(c);
                continue;
            }
            var fontUnitScale = fontSize / unitsPerEM;

            var ascender = metrics.ascender() * fontUnitScale;
            var descender = -metrics.descender() * fontUnitScale;
            var lineHeight = metrics.lineHeight() * fontUnitScale;

            if (ascender > currentLine.maxAscender) currentLine.maxAscender = ascender;
            if (descender > currentLine.maxDescender) currentLine.maxDescender = descender;
            if (lineHeight > currentLine.maxLineHeight) currentLine.maxLineHeight = lineHeight;

            currentLine.characters.add(new CharInfo(c, font, glyph, i));
            i += Character.charCount(c);
        }
        currentLine.codeUnitEnd = text.length();
        lines.add(currentLine);

        inheritEmptyLineMetrics(lines, fontSize);

        var rawBaselines = new float[lines.size()];
        var yOffset = 0f;
        for (var k = 0; k < lines.size(); k++) {
            rawBaselines[k] = yOffset + lines.get(k).maxAscender;
            yOffset += lines.get(k).maxLineHeight;
        }

        var rawInstances = new ArrayList<GlyphInstance>();
        var minY = Float.MAX_VALUE;
        var maxY = -Float.MAX_VALUE;
        var maxRight = 0f;

        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            var line = lines.get(lineIndex);
            var baselineY = rawBaselines[lineIndex];
            var currentX = 0f;
            var prevCode = 0L;
            MsdfFont prevFontForLine = null;

            for (var ch : line.characters) {
                var font = ch.font;
                var glyph = ch.glyph;
                var metrics = font.metrics;
                var unitsPerEM = metrics.unitsPerEm();
                if (unitsPerEM == 0) continue;
                var fontUnitScale = fontSize / unitsPerEM;

                if (prevCode != 0L && prevFontForLine == font) {
                    currentX += font.getKerning(
                            prevCode,
                            ch.codePoint
                    ) * fontUnitScale;
                }

                var penX = currentX;
                var page = glyph.page();
                var quadLeft = currentX + glyph.planeLeft() * fontUnitScale;
                var quadTop = baselineY - glyph.planeTop() * fontUnitScale;
                var quadWidth = (glyph.planeRight() - glyph.planeLeft()) * fontUnitScale;
                var quadHeight = (glyph.planeTop() - glyph.planeBottom()) * fontUnitScale;
                var quadBottom = quadTop + quadHeight;

                if (quadTop < minY) minY = quadTop;
                if (quadBottom > maxY) maxY = quadBottom;
                var inkAscender = baselineY - quadTop;
                if (inkAscender > line.inkAscender) line.inkAscender = inkAscender;
                var inkDescender = quadBottom - baselineY;
                if (inkDescender > line.inkDescender) line.inkDescender = inkDescender;
                var quadRight = quadLeft + quadWidth;
                if (quadRight > maxRight) maxRight = quadRight;

                var advance = glyph.advance() * fontUnitScale;
                rawInstances.add(new GlyphInstance(
                        page.textureView,
                        quadLeft, quadTop,
                        quadWidth, quadHeight,
                        glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(),
                        ch.codeUnitStart, penX, advance
                ));

                currentX += advance;
                prevCode = ch.codePoint;
                prevFontForLine = font;
            }
        }

        var yShift = rawInstances.isEmpty() ? 0f : -minY;

        var lineLayouts = new ArrayList<LineLayout>(lines.size());
        for (var k = 0; k < lines.size(); k++) {
            var line = lines.get(k);
            var baselineY = rawBaselines[k] + yShift;
            lineLayouts.add(new LineLayout(
                    k,
                    line.codeUnitStart,
                    line.codeUnitEnd,
                    baselineY - line.inkAscender,
                    baselineY + line.inkDescender,
                    !line.characters.isEmpty()
            ));
        }

        if (rawInstances.isEmpty()) return new LayoutResult(lineLayouts, List.of(), 0f, 0f);

        var finalInstances = new ArrayList<GlyphInstance>(rawInstances.size());
        for (var inst : rawInstances) {
            finalInstances.add(new GlyphInstance(
                    inst.textureView,
                    inst.x, inst.y + yShift,
                    inst.quadWidth, inst.quadHeight,
                    inst.u0, inst.v0, inst.u1, inst.v1,
                    inst.glyphIndex, inst.penX, inst.advance
            ));
        }

        var lastLine = lines.getLast();
        var blockHeight = rawBaselines[lines.size() - 1] + yShift + lastLine.maxDescender;
        if (maxY + yShift > blockHeight) blockHeight = maxY + yShift;
        if (blockHeight < 0) blockHeight = 0f;

        return new LayoutResult(lineLayouts, finalInstances, blockHeight, maxRight);
    }

    /**
     * Fills metrics of empty lines so the line grid stays consistent:
     * each empty line inherits from its nearest non-empty predecessor
     * (or successor when leading); a text without any glyphs at all
     * falls back to the default font's metrics.
     */
    private static void inheritEmptyLineMetrics(ArrayList<LineInfo> lines, float fontSize) {
        LineInfo reference = null;
        for (var line : lines) {
            if (!line.characters.isEmpty()) {
                reference = line;
                break;
            }
        }
        if (reference == null) {
            var metrics = MsdfFontService.getFont(MsdfFontService.DEFAULT_FONT_ID).metrics;
            var scale = metrics.unitsPerEm() == 0 ? 0f : fontSize / metrics.unitsPerEm();
            for (var line : lines) {
                line.maxAscender = metrics.ascender() * scale;
                line.maxDescender = -metrics.descender() * scale;
                line.maxLineHeight = metrics.lineHeight() * scale;
                line.inkAscender = line.maxAscender;
                line.inkDescender = line.maxDescender;
            }
            return;
        }
        LineInfo previous = null;
        for (var line : lines) {
            if (!line.characters.isEmpty()) {
                previous = line;
            } else {
                var source = previous != null ? previous : reference;
                line.maxAscender = source.maxAscender;
                line.maxDescender = source.maxDescender;
                line.maxLineHeight = source.maxLineHeight;
                line.inkAscender = source.inkAscender;
                line.inkDescender = source.inkDescender;
            }
        }
    }

    private static class LineInfo {
        final int codeUnitStart;
        int codeUnitEnd;
        float maxAscender = 0f;
        float maxDescender = 0f;
        float maxLineHeight = 0f;
        float inkAscender = 0f;
        float inkDescender = 0f;
        List<CharInfo> characters = new ArrayList<>();

        LineInfo(int codeUnitStart) {
            this.codeUnitStart = codeUnitStart;
        }
    }

    private static class CharInfo {
        int codePoint;
        MsdfFont font;
        MsdfGlyph glyph;
        int codeUnitStart;

        CharInfo(int codePoint, MsdfFont font, MsdfGlyph glyph, int codeUnitStart) {
            this.codePoint = codePoint;
            this.font = font;
            this.glyph = glyph;
            this.codeUnitStart = codeUnitStart;
        }
    }

    public record GlyphInstance(GpuTextureView textureView, float x, float y, float quadWidth, float quadHeight,
                                float u0, float v0, float u1, float v1, int glyphIndex, float penX, float advance) {
    }

    public record LineLayout(int index, int codeUnitStart, int codeUnitEnd,
                             float bandTop, float bandBottom, boolean hasGlyphs) {
    }

    public record LayoutResult(List<LineLayout> lines, List<GlyphInstance> instances, float height, float width) {
    }
}
