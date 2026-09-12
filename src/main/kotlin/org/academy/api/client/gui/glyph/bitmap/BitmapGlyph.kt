package org.academy.api.client.gui.glyph.bitmap

import org.academy.api.client.gui.glyph.AtlasPage

/**
 * 一个精确像素的 FreeType 光栅字形，烘进共享 R8 位图图集。
 *
 * @param page R8 图集页
 * @param u0/v0/u1/v1 字形像素的图集 UV 矩形
 * @param bearingLeft pen origin -> ink left（光栅尺寸，物理 px）
 * @param bearingTop baseline -> ink top（正=向上，光栅尺寸，物理 px）
 * @param advance 水平 advance（光栅尺寸，物理 px，26.6 派生）
 * @param widthPx ink 宽度（光栅 px）
 * @param heightPx ink 高度（光栅 px）
 * @param rasterPx 光栅化时的量化 em 尺寸（物理 px）
 */
data class BitmapGlyph(
    val page: AtlasPage,
    val u0: Float, val v0: Float, val u1: Float, val v1: Float,
    val bearingLeft: Float, val bearingTop: Float, val advance: Float,
    val widthPx: Int, val heightPx: Int,
    val rasterPx: Float
)
