package org.academy.api.client.gui.unit

/**
 * 单位换算的唯一来源：dp→px、sp→px。
 *
 * [density] 即物理像素 / GUI 逻辑像素（Minecraft 的 `Window.guiScale`）；
 * [fontScale] 是用户字号偏好，仅作用于 [Sp]（文本），不影响 [Dp]（布局）。
 */
data class Density(
    val density: Float,
    val fontScale: Float = 1f
) {
    /** [Dp] → 物理像素。 */
    fun toPx(dp: Dp): Float = dp.value * density

    /** [Sp] → 物理像素。 */
    fun toPx(sp: Sp): Float = sp.value * density * fontScale

    /** 物理像素 → [Dp]。 */
    fun toDp(px: Float): Dp = Dp(px / density)

    /** 物理像素 → [Sp]。 */
    fun toSp(px: Float): Sp = Sp(px / (density * fontScale))

    companion object {
        val UNIT: Density = Density(1f)
    }
}
