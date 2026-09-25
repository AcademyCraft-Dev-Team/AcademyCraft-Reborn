package org.academy.api.client.gui.text.glyph.msdf

import org.academy.api.client.gui.text.atlas.AtlasPage

data class MsdfGlyph(
    val page: AtlasPage,
    val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    val advance: Int,
    val planeLeft: Float, val planeBottom: Float, val planeRight: Float, val planeTop: Float
)
