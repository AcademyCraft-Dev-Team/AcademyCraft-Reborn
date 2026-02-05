package org.academy.api.client.gui.msdf.atlas;

import net.minecraft.util.Mth;
import org.academy.api.client.gui.msdf.atlas.allocator.Rect;
import org.academy.api.client.gui.msdf.core.*;
import org.academy.api.client.gui.msdf.util.FreeTypeShapeConverter;
import org.academy.api.client.gui.msdf.util.MsdfTextureUtil;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsdfAtlas implements AutoCloseable {
    private final List<AtlasPage> pages = new ArrayList<>();
    private final Map<Integer, MsdfGlyph> glyphCache = new HashMap<>();
    private final int pageSize;
    private final int glyphSize;
    private final double pxRange;
    private final int padding;

    public MsdfAtlas(int pageSize, int glyphSize, double pxRange) {
        this.pageSize = pageSize;
        this.glyphSize = glyphSize;
        this.pxRange = pxRange;
        padding = Mth.ceil(pxRange) + 2;
    }

    public @Nullable MsdfGlyph getOrGenerate(FT_Face face, int character) {
        if (glyphCache.containsKey(character)) return glyphCache.get(character);

        if (FreeType.FT_Load_Char(
                face,
                character,
                FreeType.FT_LOAD_NO_SCALE | FreeType.FT_LOAD_NO_HINTING | FreeType.FT_LOAD_NO_BITMAP
        ) != 0) return null;

        var slot = face.glyph();
        if (slot == null) return null;

        var metrics = slot.metrics();
        var advance = metrics.horiAdvance();
        var bearingX = metrics.horiBearingX();
        var bearingY = metrics.horiBearingY();

        Shape shape;
        try (var stack = MemoryStack.stackPush()) {
            shape = FreeTypeShapeConverter.loadShapeFromFtOutline(slot.outline(), stack);
        }

        if (shape.contours.isEmpty()) {
            var whitespaceGlyph = new MsdfGlyph(
                    null, 0, 0, 0, 0, advance, bearingX, bearingY, 0, 0, 0, 0
            );
            glyphCache.put(character, whitespaceGlyph);
            return whitespaceGlyph;
        }

        shape.setYAxisOrientation(MsdfBase.MSDFGEN_Y_AXIS_DEFAULT_ORIENTATION);
        shape.normalize();
        shape.orientContours();
        EdgeColoring.edgeColoringSimple(shape, 3.0, 0);

        var bounds = shape.getBounds(0, 0, 0);
        var scale = (double) glyphSize / face.units_per_EM();

        var slotSize = glyphSize + Mth.ceil(pxRange * 2);
        var slotSizeWithPadding = slotSize + padding;

        var texWidth = Mth.ceil((bounds.r - bounds.l) * scale + pxRange * 2);
        var texHeight = Mth.ceil((bounds.t - bounds.b) * scale + pxRange * 2);

        AtlasPage page = null;
        Rect rect = null;

        for (var p : pages) {
            var attempt = p.reserve(slotSizeWithPadding, slotSizeWithPadding);
            if (attempt.isPresent()) {
                page = p;
                rect = attempt.get();
                break;
            }
        }

        if (page == null) {
            page = new AtlasPage(pageSize, "msdf_atlas_page_" + pages.size());
            pages.add(page);
            rect = page.reserve(slotSizeWithPadding, slotSizeWithPadding)
                    .orElseThrow(() -> new IllegalStateException(
                            "Glyph is too large (" + slotSizeWithPadding + "x" + slotSizeWithPadding +
                                    ") for atlas page size (" + pageSize + "x" + pageSize + ")"
                    ));
        }

        var tx = -bounds.l + (pxRange / scale);
        var ty = -bounds.b + (pxRange / scale);

        var projection = new Projection(new Vector2(scale), new Vector2(tx, ty));
        var sdfTransform = new SDFTransformation(projection, new DistanceMapping(new Range(pxRange / scale)));

        var config = new MSDFGeneratorConfig();
        config.overlapSupport = true;

        var bitmap = new FloatBitmap(texWidth, texHeight, 3);
        Msdfgen.generateMSDF(bitmap.toRef(), shape, sdfTransform, config);

        try (var nativeImage = MsdfTextureUtil.convertToNativeImage(bitmap.toRef())) {
            page.upload(new Rect(rect.x(), rect.y(), texWidth, texHeight), nativeImage);
        }

        var u0 = (float) rect.x() / pageSize;
        var v0 = (float) rect.y() / pageSize;
        var u1 = (float) (rect.x() + texWidth) / pageSize;
        var v1 = (float) (rect.y() + texHeight) / pageSize;

        var pLeft = bounds.l - (pxRange / scale);
        var pBottom = bounds.b - (pxRange / scale);
        var pRight = bounds.r + (pxRange / scale);
        var pTop = bounds.t + (pxRange / scale);

        var glyph = new MsdfGlyph(
                page, u0, v0, u1, v1,
                advance, bearingX, bearingY,
                pLeft, pBottom, pRight, pTop
        );

        glyphCache.put(character, glyph);
        return glyph;
    }

    public List<AtlasPage> getPages() {
        return pages;
    }

    @Override
    public void close() {
        pages.forEach(AtlasPage::close);
        pages.clear();
        glyphCache.clear();
    }
}