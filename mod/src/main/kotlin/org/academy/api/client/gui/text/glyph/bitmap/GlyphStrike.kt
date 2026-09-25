package org.academy.api.client.gui.text.glyph.bitmap

import org.academy.api.client.gui.text.atlas.AtlasPage

data class GlyphStrike(
    val page: AtlasPage,
    val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    val bearingLeft: Float, val bearingTop: Float, val advance: Float,
    val widthPx: Int, val heightPx: Int,
    val rasterPx: Float
)
