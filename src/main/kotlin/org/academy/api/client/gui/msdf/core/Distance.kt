package org.academy.api.client.gui.msdf.core

import kotlin.math.abs

data class SignedDistance(
    var distance: Double = -Double.MAX_VALUE,
    var dot: Double = 0.0
) {
    companion object {
        fun lessThan(a: SignedDistance, b: SignedDistance): Boolean {
            return abs(a.distance) < abs(b.distance) ||
                    (abs(a.distance) == abs(b.distance) && a.dot < b.dot)
        }
    }
}

data class DistanceResult(
    val distance: Double,
    val param: Double,
    val dot: Double
)

data class MultiDistance(
    val r: Double = -Double.MAX_VALUE,
    val g: Double = -Double.MAX_VALUE,
    val b: Double = -Double.MAX_VALUE
)

data class Range(val lower: Double, val upper: Double) {
    constructor(symmetricalWidth: Double) : this(-0.5 * symmetricalWidth, 0.5 * symmetricalWidth)
}

class DistanceMapping(range: Range) {
    private val scale: Double
    private val translate: Double

    init {
        val rangeWidth = range.upper - range.lower
        scale = 1.0 / rangeWidth
        translate = -range.lower
    }

    fun apply(d: Double): Double = scale * (d + translate)
    fun apply(delta: Delta): Double = scale * delta.value

    data class Delta(val value: Double)
}