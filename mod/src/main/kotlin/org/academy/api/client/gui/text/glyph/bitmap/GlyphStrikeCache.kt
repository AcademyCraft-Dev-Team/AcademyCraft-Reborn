package org.academy.api.client.gui.text.glyph.bitmap

import org.academy.api.client.gui.text.atlas.AtlasManager
import org.academy.api.client.gui.text.font.FontFace
import org.academy.api.client.gui.text.glyph.GlyphCacheKey
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.ConcurrentHashMap

object GlyphStrikeCache {
    private val CACHE = ConcurrentHashMap<GlyphCacheKey, GlyphStrike>()

    fun clear() {
        CACHE.clear()
    }

    fun size(): Int = CACHE.size

    fun getGlyph(
        font: FontFace,
        glyphIndex: Int,
        rasterPx: Float,
        phaseX: Int,
        phaseY: Int
    ): GlyphStrike? {
        val quantized = GlyphRasterizer.quantize(rasterPx)
        if (quantized < 1f) return null
        val key = GlyphCacheKey(System.identityHashCode(font), glyphIndex, quantized, phaseX, phaseY)
        CACHE[key]?.let { return it }
        val glyph = rasterize(font, glyphIndex, quantized, phaseX, phaseY) ?: return null
        CACHE[key] = glyph
        return glyph
    }

    private fun rasterize(
        font: FontFace,
        glyphIndex: Int,
        quantizedRasterPx: Float,
        phaseX: Int,
        phaseY: Int
    ): GlyphStrike? {
        val raw = GlyphRasterizer.rasterRaw(font, glyphIndex, quantizedRasterPx, phaseX, phaseY) ?: return null

        val width = raw.width
        val height = raw.height
        val atlas = AtlasManager.bitmap()
        val reservation = atlas.reserve(width, height) ?: return null

        val inkRect = reservation.inkRect
        upload(atlas, reservation, raw.pixels)
        val texSize = reservation.page.size
        return GlyphStrike(
            reservation.page,
            inkRect.x.toFloat() / texSize,
            inkRect.y.toFloat() / texSize,
            (inkRect.x + width).toFloat() / texSize,
            (inkRect.y + height).toFloat() / texSize,
            raw.bearingLeft, raw.bearingTop, raw.advance,
            width, height,
            quantizedRasterPx
        )
    }

    private fun upload(atlas: BitmapAtlas, reservation: BitmapAtlas.Reservation, pixels: ByteArray) {
        val buffer = MemoryUtil.memAlloc(pixels.size)
        try {
            buffer.put(pixels)
            buffer.flip()
            atlas.upload(reservation, buffer)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }
}
