package org.academy.api.client.gui.glyph

/**
 * 图集/光栅相关常量。选路阈值在
 * [org.academy.api.client.gui.text.subrun.SubRunControl]（Skia `SubRunControl`）。
 */
object Constants {
    const val DEFAULT_ATLAS_SIZE: Int = 2048
    const val DEFAULT_GLYPH_SIZE: Int = 64
    const val DEFAULT_PX_RANGE: Float = 4f

    /** Skia 风格亚像素量化：光栅尺寸量化到 1/4 px。 */
    const val BITMAP_SUBPIXEL_BITS: Int = 2
    const val BITMAP_SUBPIXEL_STEPS: Float = 4f

    /** 每个 R8 位图图集页的尺寸。 */
    const val BITMAP_ATLAS_SIZE: Int = 2048

    /**
     * 每个字形槽位周围留的空 texel gutter，使 LINEAR 采样不会渗到邻字形
     * （每边 1 texel 填充 + 采样核余量）。
     */
    const val BITMAP_GLYPH_PADDING: Int = 2

    /**
     * 极小字号位图的 gamma 加深强度，用于补偿低笔画覆盖率（Skia/Chrome 风格对比度）。
     * 实际应用的覆盖率指数为 `1 / this`，越大越深；1.0 关闭。
     */
    const val BITMAP_SMALL_PX_GAMMA: Float = 1.2f
}
