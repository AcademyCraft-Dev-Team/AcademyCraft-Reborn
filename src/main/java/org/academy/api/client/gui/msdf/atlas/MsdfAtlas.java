package org.academy.api.client.gui.msdf.atlas;

import lovely.cane.jmsdfgen.*;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.environment.UiEnvironment;
import org.academy.api.client.gui.msdf.atlas.allocator.Rect;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class MsdfAtlas {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private final int pageSize;
    private final int glyphSize;
    private final float pxRange;
    private final List<AtlasPage> pages = new ArrayList<>();
    private final Map<Integer, MsdfGlyph> glyphCache = new HashMap<>();
    private final int padding;
    private final Executor executor;

    public MsdfAtlas(int pageSize, int glyphSize, float pxRange, Executor executor) {
        this(pageSize, glyphSize, pxRange, 1, executor);
    }

    public MsdfAtlas(int pageSize, int glyphSize, float pxRange, int padding, Executor executor) {
        this.pageSize = pageSize;
        this.glyphSize = glyphSize;
        this.pxRange = pxRange;
        this.padding = padding;
        this.executor = executor;
    }

    public List<AtlasPage> getPages() {
        return pages;
    }

    public @Nullable MsdfGlyph getOrGenerate(FT_Face face, ImportFont.FontHandle fontHandle, int character) {
        if (glyphCache.containsKey(character)) return glyphCache.get(character);

        var slot = face.glyph();
        if (slot == null) return null;

        var shape = new Shape();

        if (ImportFont.loadGlyph(
                shape, fontHandle, character, ImportFont.FontCoordinateScaling.FONT_SCALING_NONE).isEmpty()
        ) return null;

        shape.normalize();
        // Shape.orientContours 本身是忠实移植自c++, 没有问题的;
        // 但是由于c++版本依赖Skia(实际上LWJGL的绑定也不含Skia)进行更准确的修正, java社区没有完整绑定的Skia
        // 所以会有部分文字错误, 需要特殊修复(再次声明这不是jmsdfgen的问题)
        if (!isCjk(character)) shape.orientContours();
        shape.setYAxisOrientation(YAxisOrientation.Y_DOWNWARD);

        EdgeColoring.edgeColoringSimple(shape, 3.0, 0);

        var bounds = shape.getBounds();
        double l = bounds.l, b = bounds.b, r = bounds.r, t = bounds.t;

        if (l >= r || b >= t) {
            l = 0;
            b = 0;
            r = 1;
            t = 1;
        }

        var scale = (double) glyphSize / (double) face.units_per_EM();
        var rangeInEM = pxRange / scale;

        l -= rangeInEM;
        b -= rangeInEM;
        r += rangeInEM;
        t += rangeInEM;

        var texWidth = Mth.ceil((r - l) * scale);
        var texHeight = Mth.ceil((t - b) * scale);
        var tx = -l;
        var ty = -b;

        var slotWidth = texWidth + padding;
        var slotHeight = texHeight + padding;

        AtlasPage page = null;
        Rect rect = null;

        for (var p : pages) {
            var opt = p.reserve(slotWidth, slotHeight);
            if (opt.isPresent()) {
                page = p;
                rect = opt.get();
                break;
            }
        }

        if (page == null) {
            var newPage = new AtlasPage(pageSize, "msdf_atlas_page_" + pages.size());
            pages.add(newPage);
            var newRect = newPage.reserve(slotWidth, slotHeight);
            if (newRect.isEmpty()) {
                throw new IllegalStateException(
                        "Glyph is too large (" + slotWidth + "x" + slotHeight +
                                ") for atlas page size (" + pageSize + "x" + pageSize + ")"
                );
            }
            page = newPage;
            rect = newRect.get();
        }

        var pixelCount = texWidth * texHeight;

        var u0 = (float) rect.x() / pageSize;
        var v0 = (float) rect.y() / pageSize;
        var u1 = (float) (rect.x() + texWidth) / pageSize;
        var v1 = (float) (rect.y() + texHeight) / pageSize;

        var pLeft = (float) (bounds.l - (pxRange / scale));
        var pBottom = (float) (bounds.b - (pxRange / scale));
        var pRight = (float) (bounds.r + (pxRange / scale));
        var pTop = (float) (bounds.t + (pxRange / scale));

        var metrics = slot.metrics();
        var advance = (int) metrics.horiAdvance();

        var glyph = new MsdfGlyph(
                page, u0, v0, u1, v1,
                advance,
                pLeft, pBottom, pRight, pTop
        );
        glyphCache.put(character, glyph);

        var finalPage = page;
        var upRect = new Rect(rect.x(), rect.y(), texWidth, texHeight);
        executor.execute(() -> {
            try {
                var bitmap = new Bitmap<>(texWidth, texHeight, 3, Float[]::new);
                var transform = new SDFTransformation(
                        new Projection(new Vector2(scale), new Vector2(tx, ty)), new Range(rangeInEM)
                );
                var config = new GeneratorConfig.MSDFGeneratorConfig();
                config.overlapSupport = true;
                MSDFGen.generateMSDF(bitmap.toBitmapSection(), shape, transform, config);
                MSDFErrorCorrection.msdfErrorCorrection(bitmap.toBitmapSection(), shape, transform, config);

                var rgbaArray = new byte[pixelCount * 4];
                var fi = 0;
                var constSection = bitmap.toBitmapConstSection();
                for (var y = 0; y < constSection.height; y++) {
                    for (var x = 0; x < constSection.width; x++) {
                        var index = constSection.getPixelIndex(x, y);
                        rgbaArray[fi++] = Arithmetic.pixelFloatToByte(constSection.pixels[index]);
                        rgbaArray[fi++] = Arithmetic.pixelFloatToByte(constSection.pixels[index + 1]);
                        rgbaArray[fi++] = Arithmetic.pixelFloatToByte(constSection.pixels[index + 2]);
                        rgbaArray[fi++] = (byte) 255;
                    }
                }

                var rgbaBuf = MemoryUtil.memAlloc(pixelCount * 4);
                rgbaBuf.put(rgbaArray);
                rgbaBuf.flip();
                UiEnvironment.get().runOnMainThread(() -> {
                    finalPage.upload(upRect, rgbaBuf);
                    MemoryUtil.memFree(rgbaBuf);
                });
            } catch (Exception e) {
                LOGGER.error("Failed to generate MSDF glyph U+{}", Integer.toHexString(character), e);
            }
        });

        return glyph;
    }

    public void close() {
        pages.forEach(AtlasPage::close);
        pages.clear();
        glyphCache.clear();
    }

    private static boolean isCjk(int codepoint) {
        var script = Character.UnicodeScript.of(codepoint);
        return script == Character.UnicodeScript.HAN ||
                script == Character.UnicodeScript.HIRAGANA ||
                script == Character.UnicodeScript.KATAKANA ||
                script == Character.UnicodeScript.HANGUL;
    }
}
