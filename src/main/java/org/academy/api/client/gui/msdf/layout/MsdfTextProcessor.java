package org.academy.api.client.gui.msdf.layout;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph;
import org.academy.api.client.gui.msdf.core.MsdfConstants;
import org.academy.api.client.gui.msdf.font.MsdfFont;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.msdf.font.MsdfKerningManager;

import java.util.ArrayList;
import java.util.List;

public final class MsdfTextProcessor {
    public static List<GlyphInstance> layout(String text, float fontSize) {
        var lines = new ArrayList<LineInfo>();
        var currentLine = new LineInfo();

        for (var i = 0; i < text.codePointCount(0, text.length()); i++) {
            var c = text.codePointAt(i);
            if (c == '\n') {
                lines.add(currentLine);
                currentLine = new LineInfo();
                continue;
            }

            var font = MsdfFontService.getFont(c);
            var glyph = font.getGlyph(c);
            if (glyph == null) continue;

            var metrics = font.getMetrics();
            var unitsPerEM = metrics.unitsPerEm();
            if (unitsPerEM == 0) continue;
            var fontUnitScale = fontSize / unitsPerEM;

            var ascender = metrics.ascender() * fontUnitScale;
            var lineHeight = metrics.lineHeight() * fontUnitScale;

            if (ascender > currentLine.maxAscender) currentLine.maxAscender = ascender;
            if (lineHeight > currentLine.maxLineHeight) currentLine.maxLineHeight = lineHeight;

            currentLine.characters.add(new CharInfo(c, font, glyph));
        }
        if (!currentLine.characters.isEmpty()) lines.add(currentLine);

        var rawInstances = new ArrayList<GlyphInstance>();
        var yOffset = 0f;
        var minY = Float.MAX_VALUE;
        var maxY = -Float.MAX_VALUE;

        for (var line : lines) {
            var baselineY = yOffset + line.maxAscender + MsdfConstants.DEFAULT_PX_RANGE;
            var currentX = 0f;
            long prevCode = 0;
            MsdfFont prevFontForLine = null;

            for (var ch : line.characters) {
                var font = ch.font;
                var glyph = ch.glyph;
                var metrics = font.getMetrics();
                var unitsPerEM = metrics.unitsPerEm();
                if (unitsPerEM == 0) continue;
                var fontUnitScale = fontSize / unitsPerEM;

                if (prevCode != 0 && prevFontForLine == font) {
                    currentX += MsdfKerningManager.getKerning(font.getFace(), prevCode, ch.codePoint) * fontUnitScale;
                }

                var page = glyph.page();
                if (page != null) {
                    var quadLeft = currentX + (glyph.bearingX() * fontUnitScale);
                    var quadTop = baselineY - (glyph.bearingY() * fontUnitScale);
                    var quadWidth = (float) (glyph.planeRight() - glyph.planeLeft()) * fontUnitScale;
                    var quadHeight = (float) (glyph.planeTop() - glyph.planeBottom()) * fontUnitScale;
                    var quadBottom = quadTop + quadHeight;

                    if (quadTop < minY) minY = quadTop;
                    if (quadBottom > maxY) maxY = quadBottom;

                    rawInstances.add(new GlyphInstance(
                            page.getTextureView(),
                            quadLeft, quadTop,
                            quadWidth, quadHeight,
                            glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1()
                    ));
                }

                currentX += glyph.advance() * fontUnitScale;
                prevCode = ch.codePoint;
                prevFontForLine = font;
            }
            yOffset += line.maxLineHeight;
        }

        var finalInstances = new ArrayList<GlyphInstance>(rawInstances.size());
        var yShift = -minY;
        for (var inst : rawInstances) {
            finalInstances.add(new GlyphInstance(
                    inst.textureView,
                    inst.x, inst.y + yShift,
                    inst.quadWidth, inst.quadHeight,
                    inst.u0, inst.v0, inst.u1, inst.v1
            ));
        }
        return finalInstances;
    }

    private static class LineInfo {
        float maxAscender = 0;
        float maxLineHeight = 0;
        List<CharInfo> characters = new ArrayList<>();
    }

    private static class CharInfo {
        int codePoint;
        MsdfFont font;
        MsdfGlyph glyph;

        CharInfo(int codePoint, MsdfFont font, MsdfGlyph glyph) {
            this.codePoint = codePoint;
            this.font = font;
            this.glyph = glyph;
        }
    }

    public record GlyphInstance(
            GpuTextureView textureView,
            float x, float y,
            float quadWidth, float quadHeight,
            float u0, float v0, float u1, float v1
    ) {
    }
}