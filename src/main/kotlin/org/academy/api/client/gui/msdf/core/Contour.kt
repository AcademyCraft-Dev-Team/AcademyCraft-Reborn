package org.academy.api.client.gui.msdf.core

import kotlin.math.min
import kotlin.math.sqrt

class Contour(val edges: List<EdgeSegment>) {
    val winding: Int by lazy { computeWinding() }

    fun reverse(): Contour = Contour(edges.reversed().map { it.reverse() })

    fun bound(bounds: DoubleArray) {
        for (edge in edges) {
            edge.bound(bounds)
        }
    }

    fun boundMiters(bounds: DoubleArray, border: Double, miterLimit: Double, polarity: Int) {
        if (edges.isEmpty()) return

        var prevEdge = edges.last()
        for (edge in edges) {
            val prevDir = prevEdge.direction(1.0).normalized(false)
            val dir = edge.direction(0.0).unaryMinus().normalized(false)
            if (polarity * (prevDir cross dir) >= 0) {
                var miterLength = miterLimit
                val q = 0.5 * (1 - (prevDir dot dir))
                if (q > 0) {
                    miterLength = min(1 / sqrt(q), miterLimit)
                }
                val miter = Point(
                    edge.point(0.0).x + border * miterLength * (prevDir + dir).normalized(false).x,
                    edge.point(0.0).y + border * miterLength * (prevDir + dir).normalized(false).y
                )
                boundPoint(bounds, miter)
            }
            prevEdge = edge
        }
    }

    private fun boundPoint(bounds: DoubleArray, p: Point) {
        if (p.x < bounds[0]) bounds[0] = p.x
        if (p.y < bounds[1]) bounds[1] = p.y
        if (p.x > bounds[2]) bounds[2] = p.x
        if (p.y > bounds[3]) bounds[3] = p.y
    }

    private fun computeWinding(): Int {
        if (edges.isEmpty()) return 0

        var total = 0.0
        when (edges.size) {
            1 -> {
                val a = edges[0].point(0.0)
                val b = edges[0].point(1.0 / 3.0)
                val c = edges[0].point(2.0 / 3.0)
                total += (b.x - a.x) * (a.y + b.y)
                total += (c.x - b.x) * (b.y + c.y)
                total += (a.x - c.x) * (c.y + a.y)
            }

            2 -> {
                val a = edges[0].point(0.0)
                val b = edges[0].point(0.5)
                val c = edges[1].point(0.0)
                val d = edges[1].point(0.5)
                total += (b.x - a.x) * (a.y + b.y)
                total += (c.x - b.x) * (b.y + c.y)
                total += (d.x - c.x) * (c.y + d.y)
                total += (a.x - d.x) * (d.y + a.y)
            }

            else -> {
                var prev = edges.last().point(0.0)
                for (edge in edges) {
                    val cur = edge.point(0.0)
                    total += (cur.x - prev.x) * (prev.y + cur.y)
                    prev = cur
                }
            }
        }
        return Arithmetic.sign(total)
    }
}

data class Shape(val contours: List<Contour>, val inverseYAxis: Boolean = false) {
    fun normalize(): Shape = Normalizer.normalize(this)
    fun orient(): Shape = Orienter.orient(this)

    fun getBounds(border: Double = 0.0, miterLimit: Double = 0.0, polarity: Int = 1): Bounds {
        val LARGE_VALUE = 1e240
        val boundArray = doubleArrayOf(LARGE_VALUE, LARGE_VALUE, -LARGE_VALUE, -LARGE_VALUE)
        for (contour in contours) {
            contour.bound(boundArray)
        }
        if (border > 0) {
            boundArray[0] -= border; boundArray[1] -= border
            boundArray[2] += border; boundArray[3] += border
            if (miterLimit > 0) {
                for (contour in contours) {
                    contour.boundMiters(boundArray, border, miterLimit, polarity)
                }
            }
        }
        return Bounds(boundArray[0], boundArray[1], boundArray[2], boundArray[3])
    }

    fun getYAxisOrientation(): Int = if (inverseYAxis) 1 else 0
}

data class Bounds(val l: Double, val b: Double, val r: Double, val t: Double)