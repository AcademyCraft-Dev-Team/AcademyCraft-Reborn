package org.academy.api.client.gui.text.font

import lovely.cane.jmsdfgen.ImportFont
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.glyph.GlyphStatus
import org.academy.api.client.gui.glyph.MsdfAtlas
import org.academy.api.client.gui.glyph.AtlasManager
import org.academy.api.client.gui.glyph.MsdfGlyph
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

class MsdfFont(identifier: Identifier, val face: FT_Face, executor: Executor) {
    val descriptor: FontDescriptor = FontDescriptor(identifier, FontStyle.of(face.style_flags().toInt()))

    val atlas: MsdfAtlas = AtlasManager.msdf(identifier, executor)

    val metrics: MsdfFontMetrics = MsdfFontMetrics(
        face.units_per_EM(),
        face.ascender(),
        face.descender(),
        face.height()
    )

    val fontHandle: ImportFont.FontHandle = ImportFont.adoptFreetypeFont(face)

    private val faceLock = ReentrantLock()

    // Dedicated face for the synchronous bitmap path so FT size changes never
    // interfere with concurrent MSDF outline generation on the background thread.
    private var bitmapFace: FT_Face? = null
    private val bitmapFaceLock = ReentrantLock()

    /** 按字形索引生成/取回 MSDF 字形（布局用 AWT 选定的 glyph index）。 */
    fun getGlyphByIndex(glyphIndex: Int, codePoint: Int): MsdfGlyph? =
        atlas.getOrGenerate(face, faceLock, fontHandle, glyphIndex, codePoint)

    /** 按字形索引查询就绪状态。 */
    fun isGlyphReadyByIndex(glyphIndex: Int): Boolean =
        atlas.status(glyphIndex) == GlyphStatus.READY

    /** 按码点生成（用于预生成）；内部映射到字形索引。 */
    fun getGlyph(character: Int): MsdfGlyph? {
        val glyphIndex = glyphIndexFor(character)
        return atlas.getOrGenerate(face, faceLock, fontHandle, glyphIndex, character)
    }

    fun isGlyphReady(character: Int): Boolean =
        atlas.status(glyphIndexFor(character)) == GlyphStatus.READY

    fun getOrCreateBitmapFace(): FT_Face {
        var current = bitmapFace
        if (current == null) {
            current = MsdfFontService.createBitmapFace(descriptor.identifier)
            bitmapFace = current
        }
        return current
    }

    fun bitmapFaceLock(): ReentrantLock = bitmapFaceLock

    fun hasGlyph(character: Int): Boolean {
        faceLock.lock()
        try {
            return FreeType.FT_Get_Char_Index(face, character.toLong()) != 0
        } finally {
            faceLock.unlock()
        }
    }

    private fun glyphIndexFor(character: Int): Int {
        faceLock.lock()
        try {
            return ImportFont.getGlyphIndex(fontHandle, character.toLong()).index()
        } finally {
            faceLock.unlock()
        }
    }

    // Kerning/advances are provided by java.awt layout (TextShaper), not by FreeType.

    fun close() {
        bitmapFaceLock.lock()
        try {
            bitmapFace?.let { FreeType.FT_Done_Face(it) }
            bitmapFace = null
        } finally {
            bitmapFaceLock.unlock()
        }
        faceLock.lock()
        try {
            FreeType.FT_Done_Face(face)
        } finally {
            faceLock.unlock()
        }
    }
}
