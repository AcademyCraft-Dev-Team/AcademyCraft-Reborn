package org.academy.api.client.gui.text.subrun

/**
 * 打包的字形标识（对标 Skia `PackedGlyphID`）：字体 + 码点 + 量化光栅尺寸 + 4×4 亚像素。
 * 作为 strike/位图缓存的 key，保证同一 (字形, 尺寸, 相位) 只光栅化一次。
 */
data class PackedGlyphID(
    val fontHash: Int,
    val glyphIndex: Int,
    val rasterPx: Float,
    val phaseX: Int,
    val phaseY: Int
)
