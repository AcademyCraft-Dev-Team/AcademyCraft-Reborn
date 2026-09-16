package org.academy.api.client.gui.text.glyph.msdf

import lovely.cane.jmsdfgen.*
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.academy.AcademyCraft
import org.academy.api.client.gui.text.atlas.AtlasPage
import org.academy.api.client.gui.text.atlas.AtlasRect
import org.academy.api.client.gui.text.atlas.GlyphSignal
import org.academy.api.client.gui.text.atlas.GlyphStatus
import org.academy.api.client.thread.RenderThread
import org.academy.api.client.thread.runOnRenderThread
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

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

    fun pageCount(): Int {
        atlasLock.lock()
        try {
            return pages.size
        } finally {
            atlasLock.unlock()
        }
    }

    fun glyphCount(): Int = glyphCache.size

    fun getOrGenerate(
        face: FT_Face,
        faceLock: ReentrantLock,
        fontHandle: ImportFont.FontHandle,
        glyphIndex: Int,
        codePoint: Int
    ): MsdfGlyph? {
        if (glyphIndex == 0) {
            LOGGER.error(
                "MSDF generation for glyph index 0 (missing glyph/.notdef), codepoint U+{} in font {}",
                String.format("%04X", codePoint), fontId
            )
        }
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
        // Shape.orientContours 本身是忠实移植自c++, 没有问题的;
        // 但是由于c++版本依赖Skia(实际上LWJGL的绑定也不含Skia)进行更准确的修正, java社区没有完整绑定的Skia
        // 所以会有部分文字错误, 需要特殊修复(再次声明这不是jmsdfgen的问题)
        if (!isCjk(codePoint)) shape.orientContours()
        shape.yAxisOrientation = YAxisOrientation.Y_DOWNWARD

        EdgeColoring.edgeColoringSimple(shape, 3.0, 0)

        val bounds = shape.bounds
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
        val upRect = AtlasRect(rect.x, rect.y, texWidth, texHeight)

        executor.execute {
            try {
                val bitmap = Bitmap(texWidth, texHeight, 3) { n -> Array(n) { 0f } }
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
                runOnRenderThread {
                    try {
                        page.upload(upRect, rgbaBuf)
                        glyphStatuses[glyphIndex] = GlyphStatus.READY
                    } catch (e: Exception) {
                        LOGGER.error("Failed to upload MSDF glyph index {}", glyphIndex, e)
                        glyphStatuses[glyphIndex] = GlyphStatus.FAILED
                    } finally {
                        MemoryUtil.memFree(rgbaBuf)
                    }
                    GlyphSignal.bump()
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to generate MSDF glyph index {}", glyphIndex, e)
                runOnRenderThread {
                    glyphStatuses[glyphIndex] = GlyphStatus.FAILED
                    GlyphSignal.bump()
                }
            }
        }

        return glyph
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
        var rect: AtlasRect? = null
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

        val targetRect = rect ?: error("no atlas rect")
        val glyph = MsdfGlyph(
            page,
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

    private data class GlyphReservation(val glyph: MsdfGlyph, val rect: AtlasRect?)

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
