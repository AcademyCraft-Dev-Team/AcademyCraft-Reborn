package org.academy.api.client.gui.unit

/**
 * 缩放无关像素（scale-independent pixel）。
 *
 * 文本尺寸的统一单位，等价于 Android 的 `sp`。1sp = [Density.density] × [Density.fontScale]
 * 个物理像素，因此用户字号偏好在文本上生效，而布局尺寸（[Dp]）不受影响。
 */
@JvmInline
value class Sp(val value: Float) : Comparable<Sp> {
    operator fun plus(other: Sp): Sp = Sp(value + other.value)
    operator fun minus(other: Sp): Sp = Sp(value - other.value)
    operator fun times(factor: Float): Sp = Sp(value * factor)
    operator fun div(factor: Float): Sp = Sp(value / factor)
    operator fun unaryMinus(): Sp = Sp(-value)

    override fun compareTo(other: Sp): Int = value.compareTo(other.value)

    companion object {
        val ZERO: Sp = Sp(0f)
    }
}
