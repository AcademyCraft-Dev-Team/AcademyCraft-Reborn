package org.academy.api.client.gui.text.record

data class GlyphQuad(
    val x: Float, val y: Float,
    val width: Float, val height: Float,
    val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    val red: Float, val green: Float, val blue: Float, val alpha: Float,
    val fadeLeft: Float = 1f, val fadeRight: Float = 1f
)
