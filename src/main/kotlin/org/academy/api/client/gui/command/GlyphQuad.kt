package org.academy.api.client.gui.command

/**
 * 一个字形四边形在「文本块本地坐标（最终 GUI 像素）」下的描述，颜色为非预乘。
 * 一整段文本 = 一组 [GlyphQuad]，由 [TextDrawCommand] 作为一个多实例命令提交。
 */
data class GlyphQuad(
    val x: Float, val y: Float,
    val width: Float, val height: Float,
    val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    val red: Float, val green: Float, val blue: Float, val alpha: Float,
    val fadeLeft: Float = 1f, val fadeRight: Float = 1f
)
