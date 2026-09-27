package org.academy.api.client.gui.unit

/**
 * 密度无关像素（density-independent pixel）。
 *
 * 布局尺寸的统一单位，等价于 Android 的 `dp`。1dp = [Density.density] 个物理像素。
 * 仅做算术与比较，不含换算；换算统一走 [Density.toPx]。
 */
@JvmInline
value class Dp(val value: Float) : Comparable<Dp> {
    operator fun plus(other: Dp): Dp = Dp(value + other.value)
    operator fun minus(other: Dp): Dp = Dp(value - other.value)
    operator fun times(factor: Float): Dp = Dp(value * factor)
    operator fun div(factor: Float): Dp = Dp(value / factor)
    operator fun unaryMinus(): Dp = Dp(-value)

    override fun compareTo(other: Dp): Int = value.compareTo(other.value)

    companion object {
        val ZERO: Dp = Dp(0f)
    }
}
