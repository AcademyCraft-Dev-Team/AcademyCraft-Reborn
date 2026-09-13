package org.academy.api.client.gui.glyph.bitmap

import org.academy.api.client.gui.glyph.AtlasManager
import org.academy.api.client.gui.glyph.Constants
import org.academy.api.client.gui.glyph.allocator.Rect
import org.academy.api.client.thread.runOnRenderThread
import org.academy.api.client.gui.text.font.MsdfFont
import org.academy.api.client.gui.text.subrun.PackedGlyphID
import org.academy.api.client.gui.text.subrun.SubRunControl
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object BitmapStrikeCache {
    private val CACHE = ConcurrentHashMap<PackedGlyphID, BitmapGlyph>()

    fun clear() {
        CACHE.clear()
    }

    data class RawGlyph(
        val pixels: ByteArray,
        val width: Int,
        val height: Int,
        val bearingLeft: Float,
        val bearingTop: Float,
        val advance: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RawGlyph) return false
            return pixels.contentEquals(other.pixels) &&
                    width == other.width &&
                    height == other.height &&
                    bearingLeft == other.bearingLeft &&
                    bearingTop == other.bearingTop &&
                    advance == other.advance
        }

        override fun hashCode(): Int {
            var result = pixels.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + bearingLeft.hashCode()
            result = 31 * result + bearingTop.hashCode()
            result = 31 * result + advance.hashCode()
            return result
        }
    }

    fun quantize(px: Float): Float =
        (px * Constants.BITMAP_SUBPIXEL_STEPS).roundToInt() / Constants.BITMAP_SUBPIXEL_STEPS

    fun isSmallPx(quantizedRasterPx: Float): Boolean = SubRunControl.usesGamma(quantizedRasterPx)

    fun rasterRaw(
        font: MsdfFont,
        glyphIndex: Int,
        quantizedRasterPx: Float,
        phaseX: Int,
        phaseY: Int
    ): RawGlyph? {
        val permanent = quantizedRasterPx <= SubRunControl.BITMAP_PX_THRESHOLD
        val loadFlags = if (permanent) FreeType.FT_FT_LOAD_TARGET_LIGHT else FreeType.FT_LOAD_NO_HINTING
        val gammaLut = if (isSmallPx(quantizedRasterPx)) buildGammaLut() else null

        return runOnRenderThread {
            val face = font.getOrCreateBitmapFace()
            font.bitmapFaceLock().lock()
            try {
                if (glyphIndex == 0) return@runOnRenderThread null

                MemoryStack.stackPush().use { stack ->
                    val unitsPerStep = (64 / (1 shl Constants.BITMAP_SUBPIXEL_BITS)).toLong()
                    val shifted = FT_Vector.malloc(stack).set(phaseX * unitsPerStep, -phaseY * unitsPerStep)
                    FreeType.FT_Set_Transform(face, null, shifted)

                    var error = FreeType.FT_Set_Char_Size(
                        face, 0L, (quantizedRasterPx * 64.0).roundToLong(), 72, 72
                    )
                    if (error != 0) {
                        throw RuntimeException("FT_Set_Char_Size failed (error $error) for ${font.descriptor}")
                    }

                    error = FreeType.FT_Load_Glyph(
                        face, glyphIndex, loadFlags or FreeType.FT_LOAD_NO_BITMAP
                    )
                    if (error != 0) {
                        throw RuntimeException("FT_Load_Glyph failed (error $error) for glyph index $glyphIndex")
                    }

                    val slot = face.glyph() ?: return@runOnRenderThread null
                    val advance = slot.advance().x() / 64.0f

                    error = FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL)
                    if (error != 0) {
                        throw RuntimeException("FT_Render_Glyph failed (error $error) for glyph index $glyphIndex")
                    }

                    val bitmap = slot.bitmap()
                    val width = bitmap.width()
                    val height = bitmap.rows()
                    if (width <= 0 || height <= 0) return@runOnRenderThread null

                    val pixels = readAlpha(bitmap, width, height, gammaLut)
                    RawGlyph(
                        pixels, width, height,
                        slot.bitmap_left().toFloat(), slot.bitmap_top().toFloat(), advance
                    )
                }
            } catch (e: RuntimeException) {
                org.academy.AcademyCraft.getLogger()
                    .error("Failed to rasterize bitmap glyph index $glyphIndex", e)
                null
            } finally {
                font.bitmapFaceLock().unlock()
            }
        }
    }

    fun getGlyph(
        font: MsdfFont,
        glyphIndex: Int,
        rasterPx: Float,
        phaseX: Int,
        phaseY: Int
    ): BitmapGlyph? {
        val quantized = quantize(rasterPx)
        if (quantized < 1f) return null
        val key = PackedGlyphID(System.identityHashCode(font), glyphIndex, quantized, phaseX, phaseY)
        CACHE[key]?.let { return it }
        val glyph = rasterize(font, glyphIndex, quantized, phaseX, phaseY) ?: return null
        CACHE[key] = glyph
        return glyph
    }

    private fun rasterize(
        font: MsdfFont,
        glyphIndex: Int,
        quantizedRasterPx: Float,
        phaseX: Int,
        phaseY: Int
    ): BitmapGlyph? {
        val raw = rasterRaw(font, glyphIndex, quantizedRasterPx, phaseX, phaseY) ?: return null

        val width = raw.width
        val height = raw.height
        val padding = Constants.BITMAP_GLYPH_PADDING
        val reservation = AtlasManager.bitmap()
            .reserve(width + padding * 2, height + padding * 2) ?: return null

        val slotRect = reservation.rect
        val inkRect = Rect(slotRect.x + padding, slotRect.y + padding, width, height)
        upload(reservation, raw.pixels, inkRect)
        val texSize = reservation.page.size
        return BitmapGlyph(
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

    private fun readAlpha(bitmap: FT_Bitmap, width: Int, height: Int, gammaLut: IntArray?): ByteArray {
        val pitch = bitmap.pitch()
        val source = bitmap.buffer(pitch * height) ?: return ByteArray(0)
        val packed = ByteArray(width * height)
        var index = 0
        for (row in 0 until height) {
            val base = row * pitch
            for (col in 0 until width) {
                var value = source.get(base + col).toInt() and 0xFF
                if (gammaLut != null) value = gammaLut[value]
                packed[index++] = value.toByte()
            }
        }
        return packed
    }

    private fun upload(reservation: BitmapAtlas.Reservation, pixels: ByteArray, inkRect: Rect) {
        val buffer = MemoryUtil.memAlloc(pixels.size)
        try {
            buffer.put(pixels)
            buffer.flip()
            reservation.page.upload(inkRect, buffer)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun buildGammaLut(): IntArray {
        val lut = IntArray(256)
        val gamma = Constants.BITMAP_SMALL_PX_GAMMA
        for (i in 0 until 256) {
            lut[i] = (255.0 * (i / 255.0).pow(1.0 / gamma)).roundToInt()
        }
        return lut
    }
}
