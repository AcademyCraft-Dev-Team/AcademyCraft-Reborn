package org.academy.api.client.gui.unit

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
