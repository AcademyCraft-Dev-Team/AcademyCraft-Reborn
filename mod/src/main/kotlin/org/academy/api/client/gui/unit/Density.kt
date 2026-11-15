package org.academy.api.client.gui.unit

data class Density(
    val density: Float,
    val fontScale: Float = 1f
) {
    fun toPx(dp: Dp): Float = dp.value * density

    fun toPx(sp: Sp): Float = sp.value * density * fontScale

    fun toDp(px: Float): Dp = Dp(px / density)

    fun toSp(px: Float): Sp = Sp(px / (density * fontScale))

    companion object {
        val UNIT: Density = Density(1f)
    }
}
