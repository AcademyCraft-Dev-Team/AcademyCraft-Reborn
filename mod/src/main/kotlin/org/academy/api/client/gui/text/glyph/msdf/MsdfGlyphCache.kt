package org.academy.api.client.gui.text.glyph.msdf

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.text.atlas.AtlasManager
import org.academy.api.client.gui.text.atlas.GlyphStatus
import org.academy.api.client.gui.text.font.FontFace
import org.academy.api.client.gui.text.font.FontRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class MsdfGlyphCache private constructor(private val font: FontFace, executor: Executor) {
    val atlas: MsdfAtlas = AtlasManager.msdf(font.descriptor.identifier, executor)

    fun getGlyphByIndex(glyphIndex: Int, codePoint: Int): MsdfGlyph? =
        atlas.getOrGenerate(font.face, font.faceLock, font.fontHandle, glyphIndex, codePoint)

    fun isReady(glyphIndex: Int): Boolean = atlas.status(glyphIndex) == GlyphStatus.READY

    fun getGlyph(codePoint: Int): MsdfGlyph? =
        getGlyphByIndex(font.glyphIndex(codePoint), codePoint)

    companion object {
        private val caches = ConcurrentHashMap<Identifier, MsdfGlyphCache>()

        fun of(font: FontFace): MsdfGlyphCache =
            caches.computeIfAbsent(font.descriptor.identifier) {
                MsdfGlyphCache(font, FontRepository.glyphExecutor)
            }

        fun clear() {
            caches.clear()
        }
    }
}
