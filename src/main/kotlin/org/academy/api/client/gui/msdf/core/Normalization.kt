package org.academy.api.client.gui.msdf.core

import kotlin.math.abs
import kotlin.math.sqrt

object ConvergentCurveOrdering {
    private fun simplifyDegenerateCurve(controlPoints: Array<Point>, order: IntArray) {
        if (order[0] == 3) {
            val cond1 = controlPoints[1] == controlPoints[0] || controlPoints[1] == controlPoints[3]
            val cond2 = controlPoints[2] == controlPoints[0] || controlPoints[2] == controlPoints[3]
            if (cond1 && cond2) {
                controlPoints[1] = controlPoints[3]
                order[0] = 1
            }
        }
        if (order[0] == 2) {
            if (controlPoints[1] == controlPoints[0] || controlPoints[1] == controlPoints[2]) {
                controlPoints[1] = controlPoints[2]
                order[0] = 1
            }
        }
        if (order[0] == 1) {
            if (controlPoints[0] == controlPoints[1]) {
                order[0] = 0
            }
        }
    }

    private fun convergentCurveOrderingInner(
        controlPoints: Array<Point>,
        aOrder: Int,
        bOrder: Int
    ): Int {
        val corner = controlPoints[4]
        var a1 = controlPoints[3] - corner
        var b1 = controlPoints[5] - corner

        var a2 = Vec2(0.0, 0.0)
        var b2 = Vec2(0.0, 0.0)
        var a3 = Vec2(0.0, 0.0)
        var b3 = Vec2(0.0, 0.0)

        if (aOrder >= 2) {
            a2 = (controlPoints[2] - controlPoints[3]) - a1
        }
        if (bOrder >= 2) {
            b2 = (controlPoints[6] - controlPoints[5]) - b1
        }
        if (aOrder >= 3) {
            a3 = ((controlPoints[1] - controlPoints[2]) - (controlPoints[2] - controlPoints[3])) - a2
            a2 *= 3.0
        }
        if (bOrder >= 3) {
            b3 = ((controlPoints[7] - controlPoints[6]) - (controlPoints[6] - controlPoints[5])) - b2
            b2 *= 3.0
        }

        a1 *= aOrder.toDouble()
        b1 *= bOrder.toDouble()

        if (!a1.isZero() && !b1.isZero()) {
            val asLen = a1.length()
            val bsLen = b1.length()

            val d3 = asLen * (a1 cross b2) + bsLen * (a2 cross b1)
            if (abs(d3) > 1e-12) return Arithmetic.sign(d3)

            val d4 = asLen * asLen * (a1 cross b3) + asLen * bsLen * (a2 cross b2) + bsLen * bsLen * (a3 cross b1)
            if (abs(d4) > 1e-12) return Arithmetic.sign(d4)

            val d5 = asLen * (a2 cross b3) + bsLen * (a3 cross b2)
            if (abs(d5) > 1e-12) return Arithmetic.sign(d5)

            return Arithmetic.sign(a3 cross b3)
        }

        var s = 1
        if (!a1.isZero()) {
            var temp = a1; a1 = b1; b1 = temp
            temp = a2; a2 = b2; b2 = temp
            temp = a3; a3 = b3; b3 = temp
            s = -1
        }

        if (!b1.isZero() && a1.isZero()) {
            val d = a3 cross b1
            if (abs(d) > 1e-12) return s * Arithmetic.sign(d)

            val d2 = a2 cross b2
            if (abs(d2) > 1e-12) return s * Arithmetic.sign(d2)

            val d3 = a3 cross b2
            if (abs(d3) > 1e-12) return s * Arithmetic.sign(d3)

            val d4 = a2 cross b3
            if (abs(d4) > 1e-12) return s * Arithmetic.sign(d4)

            return s * Arithmetic.sign(a3 cross b3)
        }

        if (a1.isZero() && b1.isZero()) {
            val d = sqrt(a2.length()) * (a2 cross b3) + sqrt(b2.length()) * (a3 cross b2)
            if (abs(d) > 1e-12) return Arithmetic.sign(d)

            return Arithmetic.sign(a3 cross b3)
        }

        return 0
    }

    fun ordering(a: EdgeSegment?, b: EdgeSegment?): Int {
        if (a == null || b == null) return 0

        var aOrder = a.type
        var bOrder = b.type

        if (!(aOrder in 1..3 && bOrder in 1..3)) return 0

        val controlPoints = Array(12) { Point(0.0, 0.0) }
        val aCpTmp = Array(4) { Point(0.0, 0.0) }

        for (i in 0..aOrder) {
            aCpTmp[i] = a.controlPoints()[i]
        }
        for (i in 0..bOrder) {
            controlPoints[4 + i] = b.controlPoints()[i]
        }

        if (aCpTmp[aOrder] != controlPoints[4]) return 0

        val bCpTmp = Array(4) { Point(0.0, 0.0) }
        for (i in 0..bOrder) {
            bCpTmp[i] = controlPoints[4 + i]
        }

        val aOrderArr = intArrayOf(aOrder)
        val bOrderArr = intArrayOf(bOrder)

        simplifyDegenerateCurve(aCpTmp, aOrderArr)
        simplifyDegenerateCurve(bCpTmp, bOrderArr)

        aOrder = aOrderArr[0]
        bOrder = bOrderArr[0]

        for (i in 0..bOrder) {
            controlPoints[4 + i] = bCpTmp[i]
        }

        if (aOrder >= 0) {
            System.arraycopy(aCpTmp, 0, controlPoints, 4 - aOrder, aOrder)
        }

        return convergentCurveOrderingInner(controlPoints, aOrder, bOrder)
    }
}

object Normalizer {
    fun normalize(shape: Shape): Shape {
        val newContours = shape.contours.map { contour ->
            if (contour.edges.size == 1) {
                val parts = contour.edges.first().splitInThirds()
                Contour(parts.toList())
            } else if (contour.edges.isNotEmpty()) {
                val edges = contour.edges.toMutableList()
                for (i in edges.indices) {
                    val prevIdx = if (i == 0) edges.size - 1 else i - 1
                    val prevEdge = edges[prevIdx]
                    val curEdge = edges[i]

                    val prevDir = prevEdge.direction(1.0).normalized(false)
                    val curDir = curEdge.direction(0.0).normalized(false)

                    if ((prevDir dot curDir) < Constants.CORNER_DOT_EPSILON - 1) {
                        val factor = Constants.DECONVERGE_OVERSHOOT *
                                sqrt(1 - (Constants.CORNER_DOT_EPSILON - 1) * (Constants.CORNER_DOT_EPSILON - 1)) /
                                (Constants.CORNER_DOT_EPSILON - 1)
                        var axis = (curDir - prevDir).normalized(false) * factor
                        if (ConvergentCurveOrdering.ordering(prevEdge, curEdge) < 0) axis = -axis

                        edges[prevIdx] = deconvergeEdge(prevEdge, 1, axis.orthogonal(true))
                        edges[i] = deconvergeEdge(curEdge, 0, axis.orthogonal(false))
                    }
                }
                Contour(edges)
            } else {
                contour
            }
        }
        return Shape(newContours, shape.inverseYAxis)
    }

    private fun deconvergeEdge(edge: EdgeSegment, param: Int, vector: Vec2): EdgeSegment {
        val current: EdgeSegment = if (edge is QuadraticSegment) edge.convertToCubic() else edge
        if (current is CubicSegment) {
            val p: Array<Point> = current.controlPoints()
            return when (param) {
                0 -> {
                    val dir0 = p[1] - p[0]
                    val len0 = dir0.length()
                    if (len0 > 0) {
                        current.copy(p1 = Point(p[1].x + len0 * vector.x, p[1].y + len0 * vector.y))
                    } else current
                }

                1 -> {
                    val dir1 = p[2] - p[3]
                    val len1 = dir1.length()
                    if (len1 > 0) {
                        current.copy(p2 = Point(p[2].x + len1 * vector.x, p[2].y + len1 * vector.y))
                    } else current
                }

                else -> current
            }
        }
        return current
    }
}

object Orienter {
    fun orient(shape: Shape): Shape {
        val orientations = MutableList(shape.contours.size) { 0 }
        val ratio = 0.5 * (sqrt(5.0) - 1)

        for (i in shape.contours.indices) {
            val contour = shape.contours[i]
            if (orientations[i] == 0 && contour.edges.isNotEmpty()) {
                val firstEdge = contour.edges.first()
                val y0 = firstEdge.point(0.0).y
                var y1 = y0
                for (edge in contour.edges) {
                    if (y0 == y1) y1 = edge.point(1.0).y
                }
                for (edge in contour.edges) {
                    if (y0 == y1) y1 = edge.point(ratio).y
                }
                val y = mix(y0, y1, ratio)

                data class IntersectionData(val x: Double, var direction: Int, val contourIndex: Int)

                val intersections = mutableListOf<IntersectionData>()
                for (j in shape.contours.indices) {
                    for (edge in shape.contours[j].edges) {
                        val xArr = DoubleArray(3)
                        val dyArr = IntArray(3)
                        val n = edge.scanlineIntersections(xArr, dyArr, y)
                        for (k in 0 until n) {
                            intersections.add(IntersectionData(xArr[k], dyArr[k], j))
                        }
                    }
                }

                if (intersections.isNotEmpty()) {
                    intersections.sortBy { it.x }
                    for (j in 1 until intersections.size) {
                        if (intersections[j].x == intersections[j - 1].x) {
                            intersections[j].direction = 0
                            intersections[j - 1].direction = 0
                        }
                    }
                    var parity = 0
                    for (intersection in intersections) {
                        if (intersection.direction != 0) {
                            val index = intersection.contourIndex
                            var value = orientations[index]
                            value += 2 * (parity xor (if (intersection.direction > 0) 1 else 0)) - 1
                            orientations[index] = value
                            parity = 1 - parity
                        }
                    }
                }
            }
        }

        val orientedContours = shape.contours.mapIndexed { index, contour ->
            if (orientations[index] < 0) contour.reverse() else contour
        }
        return Shape(orientedContours, shape.inverseYAxis)
    }

    private fun mix(a: Double, b: Double, t: Double) = (1 - t) * a + t * b
}