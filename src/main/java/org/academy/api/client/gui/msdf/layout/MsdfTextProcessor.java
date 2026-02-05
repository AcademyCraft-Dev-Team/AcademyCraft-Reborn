package org.academy.api.client.gui.msdf.layout;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.gui.msdf.core.MsdfConstants;
import org.academy.api.client.gui.msdf.font.MsdfFont;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.msdf.font.MsdfKerningManager;

import java.util.ArrayList;
import java.util.List;

public final class MsdfTextProcessor {
    public static List<GlyphInstance> layout(String text, float fontSize) {
        var instances = new ArrayList<GlyphInstance>();

        var defaultFont = MsdfFontService.getDefaultFont();
        var defaultMetrics = defaultFont.getMetrics();
        var defaultScale = fontSize / defaultMetrics.unitsPerEm();

        var currentX = 0f;
        var currentY = (defaultMetrics.ascender() + MsdfConstants.DEFAULT_PX_RANGE) * defaultScale;
        var lineHeight = defaultMetrics.lineHeight() * defaultScale;

        var prevChar = 0;
        MsdfFont prevFont = null;

        for (var i = 0; i < text.codePointCount(0, text.length()); i++) {
            var c = text.codePointAt(i);
            if (c == '\n') {
                currentX = 0;
                currentY += lineHeight;
                prevChar = 0;
                prevFont = null;
                continue;
            }

            var font = MsdfFontService.getFont(c);
            var glyph = font.getGlyph(c);
            if (glyph == null) continue;

            var metrics = font.getMetrics();
            var unitsPerEM = metrics.unitsPerEm();
            if (unitsPerEM == 0) continue;
            var fontUnitScale = fontSize / unitsPerEM;

            if (prevChar != 0 && prevFont == font) {
                currentX += MsdfKerningManager.getKerning(font.getFace(), prevChar, c) * fontUnitScale;
            }

            if (glyph.page() != null) {
                var quadLeft = currentX + (glyph.bearingX() * fontUnitScale);
                var quadTop = currentY - (glyph.bearingY() * fontUnitScale);

                var quadWidth = (float) (glyph.planeRight() - glyph.planeLeft()) * fontUnitScale;
                var quadHeight = (float) (glyph.planeTop() - glyph.planeBottom()) * fontUnitScale;

                instances.add(new GlyphInstance(
                                glyph.page().getTextureView(),
                                quadLeft, quadTop,
                                quadWidth, quadHeight,
                                glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1()
                        )
                );
            }

            currentX += glyph.advance() * fontUnitScale;
            prevChar = c;
            prevFont = font;
        }
        return instances;
    }

    public record GlyphInstance(
            GpuTextureView textureView,
            float x, float y,
            float quadWidth, float quadHeight,
            float u0, float v0, float u1, float v1
    ) {
    }
}