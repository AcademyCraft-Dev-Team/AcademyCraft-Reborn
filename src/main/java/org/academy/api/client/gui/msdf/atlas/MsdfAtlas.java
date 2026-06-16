package org.academy.api.client.gui.msdf.atlas;

import net.minecraft.util.Mth;
import org.academy.api.client.gui.msdf.atlas.allocator.Rect;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.msdfgen.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsdfAtlas implements AutoCloseable {
    private final int pageSize;
    private final int glyphSize;
    private final float pxRange;
    private final List<AtlasPage> pages = new ArrayList<>();
    private final Map<Integer, MsdfGlyph> glyphCache = new HashMap<>();
    private final int padding;

    public MsdfAtlas(int pageSize, int glyphSize, float pxRange) {
        this(pageSize, glyphSize, pxRange, 1);
    }

    public MsdfAtlas(int pageSize, int glyphSize, float pxRange, int padding) {
        this.pageSize = pageSize;
        this.glyphSize = glyphSize;
        this.pxRange = pxRange;
        this.padding = padding;
    }

    public List<AtlasPage> getPages() {
        return pages;
    }

    public @Nullable MsdfGlyph getOrGenerate(FT_Face face, long msdfFontHandle, int character) {
        if (glyphCache.containsKey(character)) return glyphCache.get(character);

        if (FreeType.FT_Load_Char(
                face,
                character,
                FreeType.FT_LOAD_NO_SCALE | FreeType.FT_LOAD_NO_HINTING | FreeType.FT_LOAD_NO_BITMAP
        ) != 0) return null;

        var slot = face.glyph();
        if (slot == null) return null;

        long shapeHandle;
        MSDFGenBitmap bitmap = null;
        PointerBuffer pixelsPtr = null;
        ByteBuffer rgbaBuf = null;

        try (var stack = MemoryStack.stackPush()) {
            var shapePtr = stack.callocPointer(1);
            var res = MSDFGenExt.msdf_ft_font_load_glyph(
                    msdfFontHandle,
                    character,
                    MSDFGenExt.MSDF_FONT_SCALING_NONE,
                    null,
                    shapePtr
            );
            if (res != MSDFGen.MSDF_SUCCESS) return null;
            shapeHandle = shapePtr.get();
        }

        try {
            MSDFGen.msdf_shape_normalize(shapeHandle);
            MSDFGen.msdf_shape_set_y_axis_orientation(shapeHandle, MSDFGen.MSDF_ORIENTATION_Y_DOWNWARD);
            MSDFGen.msdf_shape_orient_contours(shapeHandle);
            MSDFGen.msdf_shape_edge_colors_simple(shapeHandle, 3.0);

            try (var stack = MemoryStack.stackPush()) {
                var bounds = MSDFGenBounds.calloc(stack);
                MSDFGen.msdf_shape_bound(shapeHandle, bounds);

                var scale = (double) glyphSize / (double) face.units_per_EM();
                var rangeInEM = pxRange / scale;
                var tx = -bounds.l() + rangeInEM;
                var ty = -bounds.b() + rangeInEM;

                var texWidth = Mth.ceil((bounds.r() - bounds.l()) * scale + pxRange * 2);
                var texHeight = Mth.ceil((bounds.t() - bounds.b()) * scale + pxRange * 2);

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

                bitmap = MSDFGenBitmap.calloc(stack);
                if (MSDFGen.msdf_bitmap_alloc(
                        MSDFGen.MSDF_BITMAP_TYPE_MSDF,
                        texWidth,
                        texHeight,
                        bitmap
                ) != MSDFGen.MSDF_SUCCESS) return null;

                var transform = MSDFGenTransform.calloc(stack);
                transform.scale().set(scale, scale);
                transform.translation().set(tx, ty);
                transform.distance_mapping().set(-0.5 * rangeInEM, 0.5 * rangeInEM);

                if (MSDFGen.msdf_generate_msdf(
                        bitmap,
                        shapeHandle,
                        transform
                ) != MSDFGen.MSDF_SUCCESS) return null;

                pixelsPtr = MemoryUtil.memAllocPointer(1);
                MSDFGen.msdf_bitmap_get_pixels(bitmap, pixelsPtr);
                var pixelCount = texWidth * texHeight;
                var floatBuf = MemoryUtil.memFloatBuffer(pixelsPtr.get(), pixelCount * 3);

                rgbaBuf = MemoryUtil.memAlloc(pixelCount * 4);
                var rgbaArray = new byte[pixelCount * 4];
                var fi = 0;
                var byte255 = (byte) 255;
                while (floatBuf.hasRemaining()) {
                    var rf = floatBuf.get();
                    var gf = floatBuf.get();
                    var bf = floatBuf.get();

                    var r = rf <= 0f ? 0 : rf >= 1f ? 255 : (int) (rf * 255f);
                    var g = gf <= 0f ? 0 : gf >= 1f ? 255 : (int) (gf * 255f);
                    var b = bf <= 0f ? 0 : bf >= 1f ? 255 : (int) (bf * 255f);

                    rgbaArray[fi++] = (byte) r;
                    rgbaArray[fi++] = (byte) g;
                    rgbaArray[fi++] = (byte) b;
                    rgbaArray[fi++] = byte255;
                }
                rgbaBuf.put(rgbaArray);
                rgbaBuf.flip();

                page.upload(new Rect(rect.x(), rect.y(), texWidth, texHeight), rgbaBuf);

                var u0 = (float) rect.x() / pageSize;
                var v0 = (float) rect.y() / pageSize;
                var u1 = (float) (rect.x() + texWidth) / pageSize;
                var v1 = (float) (rect.y() + texHeight) / pageSize;

                var pLeft = (float) (bounds.l() - (pxRange / scale));
                var pBottom = (float) (bounds.b() - (pxRange / scale));
                var pRight = (float) (bounds.r() + (pxRange / scale));
                var pTop = (float) (bounds.t() + (pxRange / scale));

                var metrics = slot.metrics();
                var advance = (int) metrics.horiAdvance();

                var glyph = new MsdfGlyph(
                        page, u0, v0, u1, v1,
                        advance,
                        pLeft, pBottom, pRight, pTop
                );
                glyphCache.put(character, glyph);
                return glyph;
            }
        } finally {
            if (rgbaBuf != null) MemoryUtil.memFree(rgbaBuf);
            if (pixelsPtr != null) MemoryUtil.memFree(pixelsPtr);
            if (bitmap != null) MSDFGen.msdf_bitmap_free(bitmap);
            MSDFGen.msdf_shape_free(shapeHandle);
        }
    }

    @Override
    public void close() {
        pages.forEach(AtlasPage::close);
        pages.clear();
        glyphCache.clear();
    }
}
