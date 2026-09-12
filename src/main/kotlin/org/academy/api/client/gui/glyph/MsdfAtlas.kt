package org.academy.api.client.gui.glyph

import com.mojang.blaze3d.systems.RenderSystem
import lovely.cane.jmsdfgen.Arithmetic
import lovely.cane.jmsdfgen.Bitmap
import lovely.cane.jmsdfgen.EdgeColoring
import lovely.cane.jmsdfgen.GeneratorConfig
import lovely.cane.jmsdfgen.ImportFont
import lovely.cane.jmsdfgen.MSDFErrorCorrection
import lovely.cane.jmsdfgen.MSDFGen
import lovely.cane.jmsdfgen.Projection
import lovely.cane.jmsdfgen.Range
import lovely.cane.jmsdfgen.SDFTransformation
import lovely.cane.jmsdfgen.Shape
import lovely.cane.jmsdfgen.Vector2
import lovely.cane.jmsdfgen.YAxisOrientation
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.academy.AcademyCraft
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.glyph.allocator.Rect
import org.academy.api.client.thread.RenderThread
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

/**
 * 每个字体一块 MSDF 图集：按字形索引生成（异步）并缓存 [MsdfGlyph]。
 *
 * 字形标识用字体 glyph index（AWT 布局选定），与位图 strike / 就绪状态一致。
 */
class MsdfAtlas(
    private val fontId: Identifier,
    private val pageSize: Int,
    private val glyphSize: Int,
    private val pxRange: Float,
    private val padding: Int,
    private val executor: Executor
) {
    constructor(fontId: Identifier, pageSize: Int, glyphSize: Int, pxRange: Float, executor: Executor) :
            this(fontId, pageSize, glyphSize, pxRange, 1, executor)

    private val pages = ArrayList<AtlasPage>()
    private val glyphCache = ConcurrentHashMap<Int, MsdfGlyph>()
    private val glyphStatuses = ConcurrentHashMap<Int, GlyphStatus>()
    private val atlasLock = ReentrantLock()

    fun status(glyphIndex: Int): GlyphStatus =
        glyphStatuses.getOrDefault(glyphIndex, GlyphStatus.PENDING)

    fun getPages(): List<AtlasPage> {
        atlasLock.lock()
        try {
            return pages.toList()
        } finally {
            atlasLock.unlock()
        }
    }

    fun getOrGenerate(
        face: FT_Face,
        faceLock: ReentrantLock,
        fontHandle: ImportFont.FontHandle,
        glyphIndex: Int,
        codePoint: Int
    ): MsdfGlyph? {
        glyphCache[glyphIndex]?.let { return it }

        val shape = Shape()

        val advance: Int
        faceLock.lock()
        try {
            if (ImportFont.loadGlyph(
                    shape, fontHandle, ImportFont.GlyphIndex(glyphIndex),
                    ImportFont.FontCoordinateScaling.FONT_SCALING_NONE
                ).isEmpty
            ) return null
            val slot = face.glyph() ?: return null
            advance = slot.metrics().horiAdvance().toInt()
        } finally {
            faceLock.unlock()
        }

        shape.normalize()
        // Shape.orientContours 忠实移植自 c++，但 c++ 版依赖 Skia 做更准的修正；
        // java 无完整 Skia 绑定时部分文字会出错，需按 CJK 跳过。
        if (!isCjk(codePoint)) shape.orientContours()
        shape.setYAxisOrientation(YAxisOrientation.Y_DOWNWARD)

        EdgeColoring.edgeColoringSimple(shape, 3.0, 0)

        val bounds = shape.getBounds()
        var l = bounds.l
        var b = bounds.b
        var r = bounds.r
        var t = bounds.t

        if (l >= r || b >= t || !l.isFinite() || !r.isFinite() || !b.isFinite() || !t.isFinite()) {
            l = 0.0
            b = 0.0
            r = 1.0
            t = 1.0
        }

        val scale = glyphSize.toDouble() / face.units_per_EM()
        val rangeInEM = pxRange / scale

        l -= rangeInEM
        b -= rangeInEM
        r += rangeInEM
        t += rangeInEM

        val texWidth = Mth.ceil((r - l) * scale)
        val texHeight = Mth.ceil((t - b) * scale)
        val tx = -l
        val ty = -b

        val slotWidth = texWidth + padding
        val slotHeight = texHeight + padding

        val reservation = runOnRenderThread {
            reserveSlot(glyphIndex, slotWidth, slotHeight, texWidth, texHeight, advance, l, b, r, t)
        }

        val glyph = reservation.glyph
        val rect = reservation.rect ?: return glyph

        val pixelCount = texWidth * texHeight
        val page = glyph.page
        val upRect = Rect(rect.x, rect.y, texWidth, texHeight)

        executor.execute {
            try {
                val bitmap = Bitmap<Float>(texWidth, texHeight, 3) { n -> Array(n) { 0f } }
                val transform = SDFTransformation(
                    Projection(Vector2(scale), Vector2(tx, ty)), Range(rangeInEM)
                )
                val config = GeneratorConfig.MSDFGeneratorConfig()
                config.overlapSupport = true
                MSDFGen.generateMSDF(bitmap.toBitmapSection(), shape, transform, config)
                MSDFErrorCorrection.msdfErrorCorrection(bitmap.toBitmapSection(), shape, transform, config)

                val rgbaArray = ByteArray(pixelCount * 4)
                var fi = 0
                val constSection = bitmap.toBitmapConstSection()
                for (y in 0 until constSection.height) {
                    for (x in 0 until constSection.width) {
                        val index = constSection.getPixelIndex(x, y)
                        rgbaArray[fi++] = Arithmetic.pixelFloatToByte(constSection.pixels[index])
                        rgbaArray[fi++] = Arithmetic.pixelFloatToByte(constSection.pixels[index + 1])
                        rgbaArray[fi++] = Arithmetic.pixelFloatToByte(constSection.pixels[index + 2])
                        rgbaArray[fi++] = 255.toByte()
                    }
                }

                val rgbaBuf = MemoryUtil.memAlloc(pixelCount * 4)
                rgbaBuf.put(rgbaArray)
                rgbaBuf.flip()
                UiEnvironment.get().runOnMainThread {
                    try {
                        page.upload(upRect, rgbaBuf)
                        glyphStatuses[glyphIndex] = GlyphStatus.READY
                    } catch (e: Exception) {
                        LOGGER.error("Failed to upload MSDF glyph index {}", glyphIndex, e)
                        glyphStatuses[glyphIndex] = GlyphStatus.FAILED
                    } finally {
                        MemoryUtil.memFree(rgbaBuf)
                    }
                    GlyphReadiness.bump()
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to generate MSDF glyph index {}", glyphIndex, e)
                UiEnvironment.get().runOnMainThread {
                    glyphStatuses[glyphIndex] = GlyphStatus.FAILED
                    GlyphReadiness.bump()
                }
            }
        }

        return glyph
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

    @RenderThread
    private fun reserveSlot(
        glyphIndex: Int, slotWidth: Int, slotHeight: Int, texWidth: Int, texHeight: Int,
        advance: Int, planeLeft: Double, planeBottom: Double, planeRight: Double, planeTop: Double
    ): GlyphReservation {
        atlasLock.lock()
        try {
            glyphCache[glyphIndex]?.let { return GlyphReservation(it, null) }
            return allocateSlot(
                glyphIndex, slotWidth, slotHeight, texWidth, texHeight, advance,
                planeLeft, planeBottom, planeRight, planeTop
            )
        } finally {
            atlasLock.unlock()
        }
    }

    @RenderThread
    private fun allocateSlot(
        glyphIndex: Int, slotWidth: Int, slotHeight: Int, texWidth: Int, texHeight: Int,
        advance: Int, planeLeft: Double, planeBottom: Double, planeRight: Double, planeTop: Double
    ): GlyphReservation {
        var page: AtlasPage? = null
        var rect: Rect? = null
        for (p in pages) {
            val candidate = p.reserve(slotWidth, slotHeight)
            if (candidate != null) {
                page = p
                rect = candidate
                break
            }
        }

        if (page == null) {
            val newPage = AtlasPage(pageSize, "msdf_atlas_page_" + pages.size)
            pages.add(newPage)
            val newRect = newPage.reserve(slotWidth, slotHeight)
                ?: throw IllegalStateException(
                    "Glyph is too large ($slotWidth x $slotHeight) for atlas page size ($pageSize x $pageSize)"
                )
            page = newPage
            rect = newRect
        }

        val targetPage = page ?: error("no atlas page")
        val targetRect = rect ?: error("no atlas rect")
        val glyph = MsdfGlyph(
            targetPage,
            targetRect.x.toFloat() / pageSize,
            targetRect.y.toFloat() / pageSize,
            (targetRect.x + texWidth).toFloat() / pageSize,
            (targetRect.y + texHeight).toFloat() / pageSize,
            advance,
            planeLeft.toFloat(),
            planeBottom.toFloat(),
            planeRight.toFloat(),
            planeTop.toFloat()
        )
        glyphCache[glyphIndex] = glyph
        glyphStatuses[glyphIndex] = GlyphStatus.PENDING
        return GlyphReservation(glyph, rect)
    }

    private data class GlyphReservation(val glyph: MsdfGlyph, val rect: Rect?)

    fun close() {
        atlasLock.lock()
        try {
            pages.forEach { it.close() }
            pages.clear()
            glyphCache.clear()
            glyphStatuses.clear()
        } finally {
            atlasLock.unlock()
        }
    }

    companion object {
        private val LOGGER = AcademyCraft.getLogger()

        private fun isCjk(codepoint: Int): Boolean {
            val script = Character.UnicodeScript.of(codepoint)
            return script == Character.UnicodeScript.HAN ||
                    script == Character.UnicodeScript.HIRAGANA ||
                    script == Character.UnicodeScript.KATAKANA ||
                    script == Character.UnicodeScript.HANGUL
        }
    }
}
