package org.academy.internal.client.gui.text.debug

import org.academy.api.client.gui.text.atlas.AtlasManager
import org.academy.api.client.gui.text.atlas.GlyphSignal
import org.academy.api.client.gui.text.glyph.bitmap.GlyphStrikeCache
import org.academy.api.client.gui.text.shape.ShapingCache

object TextStats {
    data class Snapshot(
        val shapingCacheEntries: Int,
        val glyphStrikes: Int,
        val bitmapAtlasPages: Int,
        val msdfAtlasPages: Int,
        val glyphSignalVersion: Long
    )

    fun snapshot(): Snapshot = Snapshot(
        shapingCacheEntries = ShapingCache.size(),
        glyphStrikes = GlyphStrikeCache.size(),
        bitmapAtlasPages = AtlasManager.bitmapIfPresent()?.pageCount() ?: 0,
        msdfAtlasPages = AtlasManager.msdfPageCount(),
        glyphSignalVersion = GlyphSignal.version
    )

    fun describe(): String = snapshot().let {
        "shaping=${it.shapingCacheEntries} strikes=${it.glyphStrikes} " +
                "bitmapPages=${it.bitmapAtlasPages} msdfPages=${it.msdfAtlasPages} " +
                "signalVersion=${it.glyphSignalVersion}"
    }
}
