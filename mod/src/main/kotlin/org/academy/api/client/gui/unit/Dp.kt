package org.academy.api.client.gui.unit

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
