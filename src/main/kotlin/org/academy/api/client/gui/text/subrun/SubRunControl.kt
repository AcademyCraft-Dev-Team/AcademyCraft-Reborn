package org.academy.api.client.gui.text.subrun

/**
 * 位图 / MSDF 的选路策略（对标 Skia `SubRunControl`）。
 *
 * 判定量是**设备字号** `D = fontSize × deviceScale`。分界取 MSDF 距离场可用性拐点：
 * `D_min = k × glyphSize / pxRange`（k=2，glyphSize=64，pxRange=4 → 32）。
 * 带旋转/斜切的 run 位图会重采样发虚，分界下调（更早用 MSDF）。
 */
object SubRunControl {
    /** 永久位图带的上界（物理 px）。 */
    const val BITMAP_PX_THRESHOLD = 32f
    /** 带变换 run 的分界（更早交给 MSDF）。 */
    const val TRANSFORMED_PX_THRESHOLD = 24f
    /** 极小字号 gamma 加深的上界（与分界解耦）。 */
    const val BITMAP_GAMMA_PX_MAX = 16f
    /** 分界迟滞，避免尺寸动画跨线时抖动。 */
    const val HYSTERESIS = 2f

    /** 设备字号是否应走 MSDF。 */
    fun isMsdf(deviceTextSize: Float, transformed: Boolean): Boolean {
        val threshold = if (transformed) TRANSFORMED_PX_THRESHOLD else BITMAP_PX_THRESHOLD
        return deviceTextSize > threshold
    }

    /** 是否走位图（含占位）。[previousBitmap] 用于迟滞。 */
    fun isBitmap(deviceTextSize: Float, transformed: Boolean, previousBitmap: Boolean): Boolean {
        val threshold = if (transformed) TRANSFORMED_PX_THRESHOLD else BITMAP_PX_THRESHOLD
        val bound = if (previousBitmap) threshold + HYSTERESIS else threshold - HYSTERESIS
        return deviceTextSize <= bound
    }

    /** 是否应施加极小字号 gamma 加深。 */
    fun usesGamma(rasterPx: Float): Boolean = rasterPx <= BITMAP_GAMMA_PX_MAX
}
