package org.academy.api.client.gui.msdf.layout;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph;
import org.academy.api.client.gui.msdf.font.MsdfFont;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.msdf.font.MsdfKerningManager;

import java.util.ArrayList;
import java.util.List;

public final class MsdfTextProcessor {
    private MsdfTextProcessor() {
    }

    public static LayoutResult layout(String text, float fontSize) {
        var lines = new ArrayList<LineInfo>();
        var currentLine = new LineInfo();

        var i = 0;
        while (i < text.length()) {
            var c = text.codePointAt(i);
            if (c == '\n') {
                lines.add(currentLine);
                currentLine = new LineInfo();
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
        if (!currentLine.characters.isEmpty()) lines.add(currentLine);

        var rawInstances = new ArrayList<GlyphInstance>();
        var yOffset = 0f;
        var minY = Float.MAX_VALUE;
        var maxY = -Float.MAX_VALUE;
        var maxRight = 0f;
        var lastBaselineRaw = 0f;
        LineInfo lastLine = lines.isEmpty() ? null : lines.get(lines.size() - 1);

        for (var line : lines) {
            var baselineY = yOffset + line.maxAscender;
            lastBaselineRaw = baselineY;
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
                    currentX += MsdfKerningManager.getKerning(
                            font.face,
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
            yOffset += line.maxLineHeight;
        }

        if (rawInstances.isEmpty()) return new LayoutResult(List.of(), 0f, 0f);

        var finalInstances = new ArrayList<GlyphInstance>(rawInstances.size());
        var yShift = -minY;
        for (var inst : rawInstances) {
            finalInstances.add(new GlyphInstance(
                    inst.textureView,
                    inst.x, inst.y + yShift,
                    inst.quadWidth, inst.quadHeight,
                    inst.u0, inst.v0, inst.u1, inst.v1,
                    inst.glyphIndex, inst.penX, inst.advance
            ));
        }

        var shiftedBaseline = lastBaselineRaw + yShift;
        var blockHeight = shiftedBaseline + lastLine.maxDescender;
        if (maxY + yShift > blockHeight) blockHeight = maxY + yShift;
        if (blockHeight < 0) blockHeight = 0f;

        return new LayoutResult(finalInstances, blockHeight, maxRight);
    }

    private static class LineInfo {
        float maxAscender = 0f;
        float maxDescender = 0f;
        float maxLineHeight = 0f;
        List<CharInfo> characters = new ArrayList<>();
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

    public record LayoutResult(List<GlyphInstance> instances, float height, float width) {
    }
}
