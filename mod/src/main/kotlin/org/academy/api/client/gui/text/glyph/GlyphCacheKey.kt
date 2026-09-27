package org.academy.api.client.gui.text.glyph

data class GlyphCacheKey(
    val fontHash: Int,
    val glyphIndex: Int,
    val rasterPx: Float,
    val phaseX: Int,
    val phaseY: Int
)
