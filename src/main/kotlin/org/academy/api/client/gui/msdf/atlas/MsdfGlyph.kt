package org.academy.api.client.gui.msdf.atlas

data class MsdfGlyph(
    val page: AtlasPage?,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val advance: Long,
    val bearingX: Long,
    val bearingY: Long,
    val planeLeft: Double,
    val planeBottom: Double,
    val planeRight: Double,
    val planeTop: Double
)