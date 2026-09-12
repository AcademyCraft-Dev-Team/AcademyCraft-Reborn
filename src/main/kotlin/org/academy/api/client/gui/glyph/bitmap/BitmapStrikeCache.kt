package org.academy.api.client.gui.glyph.bitmap

import com.mojang.blaze3d.systems.RenderSystem
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.glyph.AtlasManager
import org.academy.api.client.gui.glyph.Constants
import org.academy.api.client.gui.glyph.allocator.Rect
import org.academy.api.client.gui.text.font.MsdfFont
import org.academy.api.client.gui.text.subrun.PackedGlyphID
import org.academy.api.client.gui.text.subrun.SubRunControl
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 同步 FreeType A8 字形光栅化，喂给共享 [BitmapAtlas]。
 *
 * 光栅化很便宜（UI 尺寸下亚毫秒），直接在调用线程生成，不走 MSDF 的异步队列。
 * 缓存 key 把 em 尺寸量化到 1/4 px，避免动画分数尺寸反复生成。
 */
object BitmapStrikeCache {
    private val CACHE = ConcurrentHashMap<PackedGlyphID, BitmapGlyph>()

    /** 清空缓存。字体重载后旧字体 identityHash 条目永久滞留，需显式清理。 */
    fun clear() {
        CACHE.clear()
    }

    /** 原始光栅字形：紧凑的 alpha 行。 */
    data class RawGlyph(
        val pixels: ByteArray,
        val width: Int,
        val height: Int,
        val bearingLeft: Float,
        val bearingTop: Float,
        val advance: Float
    )

    /** 把 [px] 量化到 1/[Constants.BITMAP_SUBPIXEL_STEPS] 像素。 */
    fun quantize(px: Float): Float =
        Math.round(px * Constants.BITMAP_SUBPIXEL_STEPS) / Constants.BITMAP_SUBPIXEL_STEPS

    /** 是否应施加极小字号 gamma 加深（比位图/MSDF 分界更小的一段）。 */
    fun isSmallPx(quantizedRasterPx: Float): Boolean = SubRunControl.usesGamma(quantizedRasterPx)

    /**
     * 以生产设置（轻微 hinting、gamma 加深、亚像素相位）光栅化一个字形。
     * @return 无轮廓（如空格）时返回 null
     */
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
                // Layout (AWT) already selected the glyph; rasterize by its font glyph index.
                if (glyphIndex == 0) return@runOnRenderThread null

                MemoryStack.stackPush().use { stack ->
                    val unitsPerStep = (64 / (1 shl Constants.BITMAP_SUBPIXEL_BITS)).toLong()
                    val shifted = FT_Vector.malloc(stack).set(phaseX * unitsPerStep, -phaseY * unitsPerStep)
                    FreeType.FT_Set_Transform(face, null, shifted)

                    var error = FreeType.FT_Set_Char_Size(
                        face, 0L, Math.round(quantizedRasterPx * 64.0), 72, 72
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

    /**
     * 取回 [glyphIndex] 在量化 [rasterPx]（物理 em 像素）与亚像素相位下的字形，缓存于
     * (font, glyphIndex, size, phaseX, phaseY)。
     */
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
        // Guttered slot: glyph pixels sit in the inner rect, UVs map only that rect.
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
        // Coverage exponent 1/gamma darkens strokes (raises coverage), matching Skia/Chrome contrast.
        for (i in 0 until 256) {
            lut[i] = Math.round(255.0 * Math.pow(i / 255.0, 1.0 / gamma)).toInt()
        }
        return lut
    }

    private fun <T> runOnRenderThread(task: () -> T): T {
        if (RenderSystem.isOnRenderThread()) return task()
        val future = CompletableFuture<T>()
        UiEnvironment.get().runOnMainThread {
            try {
                future.complete(task())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return future.join()
    }
}
