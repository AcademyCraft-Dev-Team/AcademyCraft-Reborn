package org.academy.api.client.gui.text.glyph.bitmap

import org.academy.api.client.gui.text.font.FontFace
import org.academy.api.client.gui.text.glyph.RasterPathPolicy
import org.academy.api.client.gui.text.glyph.RasterSpec
import org.academy.api.client.thread.runOnRenderThread
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object GlyphRasterizer {
    private val LOGGER = org.academy.AcademyCraft.getLogger()

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
        (px * RasterSpec.BITMAP_SUBPIXEL_STEPS).roundToInt() / RasterSpec.BITMAP_SUBPIXEL_STEPS

    fun rasterRaw(
        font: FontFace,
        glyphIndex: Int,
        quantizedRasterPx: Float,
        phaseX: Int,
        phaseY: Int
    ): RawGlyph? {
        val permanent = quantizedRasterPx <= RasterPathPolicy.BITMAP_PX_THRESHOLD
        val loadFlags = if (permanent) FreeType.FT_LOAD_TARGET_LIGHT else FreeType.FT_LOAD_NO_HINTING
        val gammaLut = if (RasterPathPolicy.usesGamma(quantizedRasterPx)) buildGammaLut() else null

        return runOnRenderThread {
            val face = BitmapFacePool.face(font)
            BitmapFacePool.lock(font).lock()
            try {
                if (glyphIndex == 0) {
                    LOGGER.error(
                        "Bitmap generation for glyph index 0 (missing glyph/.notdef) in font {}",
                        font.descriptor
                    )
                    return@runOnRenderThread null
                }

                MemoryStack.stackPush().use { stack ->
                    val unitsPerStep = (64 / (1 shl RasterSpec.BITMAP_SUBPIXEL_BITS)).toLong()
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
                LOGGER.error("Failed to rasterize bitmap glyph index $glyphIndex", e)
                null
            } finally {
                BitmapFacePool.lock(font).unlock()
            }
        }
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

    private fun buildGammaLut(): IntArray {
        val lut = IntArray(256)
        val gamma = RasterSpec.BITMAP_SMALL_PX_GAMMA
        for (i in 0 until 256) {
            lut[i] = (255.0 * (i / 255.0).pow(1.0 / gamma)).roundToInt()
        }
        return lut
    }
}
