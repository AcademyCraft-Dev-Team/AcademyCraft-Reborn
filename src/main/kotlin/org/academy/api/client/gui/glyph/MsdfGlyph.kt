package org.academy.api.client.gui.glyph

/** 图集中的一个 MSDF 字形：UV + plane 边界（字体单位）+ advance。 */
data class MsdfGlyph(
    val page: AtlasPage,
    val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    val advance: Int,
    val planeLeft: Float, val planeBottom: Float, val planeRight: Float, val planeTop: Float
)
