package org.academy.api.client.gui.text.font

import lovely.cane.jmsdfgen.ImportFont
import net.minecraft.resources.Identifier
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

class FontFace(identifier: Identifier, val face: FT_Face) {
    val descriptor: FontDescriptor = FontDescriptor(identifier, FontStyles.fromFreeType(face.style_flags().toInt()))

    val metrics: FontMetrics = FontMetrics(
        face.units_per_EM(),
        face.ascender(),
        face.descender(),
        face.height()
    )

    private val bboxTop: Long = face.bbox().yMax()
    private val bboxBottom: Long = face.bbox().yMin()

    private val lineMetricsCache = ConcurrentHashMap<Int, FontLineMetrics>()

    val fontHandle: ImportFont.FontHandle = ImportFont.adoptFreetypeFont(face)

    val faceLock: ReentrantLock = ReentrantLock()

    fun lineMetrics(fontSize: Float): FontLineMetrics {
        val key = (fontSize * 1000f).toInt()
        return lineMetricsCache.getOrPut(key) { computeLineMetrics(fontSize) }
    }

    private fun computeLineMetrics(fontSize: Float): FontLineMetrics {
        val upem = metrics.unitsPerEm.toFloat().takeIf { it > 0f } ?: 1f
        val scale = fontSize / upem
        val fTop = -bboxTop.toFloat() * scale
        val fAscent = -metrics.ascender.toFloat() * scale
        val fDescent = -metrics.descender.toFloat() * scale
        val fBottom = -bboxBottom.toFloat() * scale
        val fLeading = (metrics.lineHeight + metrics.descender - metrics.ascender) * scale
        return FontLineMetrics(
            top = -floor(fTop),
            ascent = -round(fAscent),
            descent = round(fDescent),
            bottom = ceil(fBottom),
            leading = round(fLeading)
        )
    }

    fun hasGlyph(character: Int): Boolean {
        faceLock.lock()
        try {
            return FreeType.FT_Get_Char_Index(face, character.toLong()) != 0
        } finally {
            faceLock.unlock()
        }
    }

    fun glyphIndex(character: Int): Int {
        faceLock.lock()
        try {
            return ImportFont.getGlyphIndex(fontHandle, character.toLong()).index()
        } finally {
            faceLock.unlock()
        }
    }

    fun close() {
        faceLock.lock()
        try {
            FreeType.FT_Done_Face(face)
        } finally {
            faceLock.unlock()
        }
    }
}
