package org.academy.api.client.gui.msdf.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

object EdgeColor {
    const val RED = 1
    const val GREEN = 2
    const val YELLOW = 3
    const val BLUE = 4
    const val MAGENTA = 5
    const val CYAN = 6
    const val WHITE = 7
}

interface EdgeSelector {
    fun reset(origin: Point)
    fun addEdge(prevEdge: EdgeSegment, edge: EdgeSegment, nextEdge: EdgeSegment)
    fun merge(other: EdgeSelector)
}

sealed class EdgeSegment(open val color: Int = EdgeColor.WHITE) {
    abstract val type: Int

    val startPoint: Point by lazy { point(0.0) }
    val endPoint: Point by lazy { point(1.0) }
    val normalizedStartDir: Vec2 by lazy { direction(0.0).normalized() }
    val normalizedEndDir: Vec2 by lazy { direction(1.0).normalized() }

    abstract fun point(param: Double): Point
    abstract fun direction(param: Double): Vec2
    abstract fun directionChange(param: Double): Vec2
    abstract fun signedDistance(origin: Point): DistanceResult
    abstract fun scanlineIntersections(x: DoubleArray, dy: IntArray, y: Double): Int
    abstract fun bound(bounds: DoubleArray)
    abstract fun reverse(): EdgeSegment
    abstract fun moveStartPoint(to: Point): EdgeSegment
    abstract fun moveEndPoint(to: Point): EdgeSegment
    abstract fun splitInThirds(): Array<EdgeSegment>
    abstract fun controlPoints(): Array<Point>
    open fun length(): Double = 0.0

    open fun distanceToPerpendicularDistance(distance: SignedDistance, origin: Point, param: Double) {
        if (param < 0.0) {
            val aq = origin - startPoint
            val ts = aq dot normalizedStartDir
            if (ts < 0.0) {
                val pd = aq cross normalizedStartDir
                if (abs(pd) <= abs(distance.distance)) {
                    distance.distance = pd
                    distance.dot = 0.0
                }
            }
        } else if (param > 1.0) {
            val bq = origin - endPoint
            val ts = bq dot normalizedEndDir
            if (ts > 0.0) {
                val pd = bq cross normalizedEndDir
                if (abs(pd) <= abs(distance.distance)) {
                    distance.distance = pd
                    distance.dot = 0.0
                }
            }
        }
    }

    companion object {
        fun create(p0: Point, p1: Point, edgeColor: Int = EdgeColor.WHITE): EdgeSegment =
            LinearSegment(p0, p1, edgeColor)

        fun create(p0: Point, p1: Point, p2: Point, edgeColor: Int = EdgeColor.WHITE): EdgeSegment {
            if ((p1 - p0) cross (p2 - p1) == 0.0)
                return LinearSegment(p0, p2, edgeColor)
            return QuadraticSegment(p0, p1, p2, edgeColor)
        }

        fun create(p0: Point, p1: Point, p2: Point, p3: Point, edgeColor: Int = EdgeColor.WHITE): EdgeSegment {
            val p12 = p2 - p1
            if (((p1 - p0) cross p12) == 0.0 && (p12 cross (p3 - p2)) == 0.0)
                return LinearSegment(p0, p3, edgeColor)
            val mid = Point(1.5 * p1.x - 0.5 * p0.x, 1.5 * p1.y - 0.5 * p0.y)
            if (mid == Point(1.5 * p2.x - 0.5 * p3.x, 1.5 * p2.y - 0.5 * p3.y))
                return QuadraticSegment(p0, mid, p3, edgeColor)
            return CubicSegment(p0, p1, p2, p3, edgeColor)
        }
    }
}

data class LinearSegment(val p0: Point, val p1: Point, override val color: Int = EdgeColor.WHITE) :
    EdgeSegment(color) {

    override val type: Int = 1

    override fun point(param: Double) = Point(
        (1 - param) * p0.x + param * p1.x,
        (1 - param) * p0.y + param * p1.y
    )

    override fun direction(param: Double) = p1 - p0

    override fun directionChange(param: Double) = Vec2(0.0, 0.0)

    override fun length() = (p1 - p0).length()

    override fun controlPoints() = arrayOf(p0, p1)

    override fun signedDistance(origin: Point): DistanceResult {
        val aq = origin - p0
        val ab = p1 - p0
        val abLenSq = ab dot ab
        val param = (aq dot ab) / abLenSq

        val eq: Vec2
        val endpointDistance: Double
        if (param > 0.5) {
            eq = p1 - origin
            endpointDistance = eq.length()
        } else {
            eq = p0 - origin
            endpointDistance = eq.length()
        }

        if (param in 0.0..1.0) {
            val perp = ab.orthogonal(false)
            val ortho = (perp dot aq) / sqrt(abLenSq)
            if (abs(ortho) < endpointDistance)
                return DistanceResult(ortho, param, 0.0)
        }

        val distance = Arithmetic.nonZeroSign((aq cross ab)) * endpointDistance
        val dirNorm = ab.normalized()
        val eqNorm = if (endpointDistance > 0.0) eq.normalized() else Vec2(0.0, 0.0)
        val dot = abs(dirNorm dot eqNorm)
        return DistanceResult(distance, param, dot)
    }

    override fun scanlineIntersections(x: DoubleArray, dy: IntArray, y: Double): Int {
        if ((y >= p0.y && y < p1.y) || (y >= p1.y && y < p0.y)) {
            val param = (y - p0.y) / (p1.y - p0.y)
            x[0] = (1 - param) * p0.x + param * p1.x
            dy[0] = if (p1.y - p0.y > 0) 1 else -1
            return 1
        }
        return 0
    }

    override fun bound(bounds: DoubleArray) {
        fun update(p: Point) {
            if (p.x < bounds[0]) bounds[0] = p.x
            if (p.y < bounds[1]) bounds[1] = p.y
            if (p.x > bounds[2]) bounds[2] = p.x
            if (p.y > bounds[3]) bounds[3] = p.y
        }
        update(p0)
        update(p1)
    }

    override fun reverse() = LinearSegment(p1, p0, color)

    override fun moveStartPoint(to: Point) = LinearSegment(to, p1, color)

    override fun moveEndPoint(to: Point) = LinearSegment(p0, to, color)

    override fun splitInThirds(): Array<EdgeSegment> {
        val p01 = point(1.0 / 3.0)
        val p02 = point(2.0 / 3.0)
        return arrayOf(
            LinearSegment(p0, p01, color),
            LinearSegment(p01, p02, color),
            LinearSegment(p02, p1, color)
        )
    }
}

data class QuadraticSegment(val p0: Point, val p1: Point, val p2: Point, override val color: Int = EdgeColor.WHITE) :
    EdgeSegment(color) {

    override val type: Int = 2

    override fun point(param: Double): Point {
        val u = 1 - param
        return Point(
            u * u * p0.x + 2 * u * param * p1.x + param * param * p2.x,
            u * u * p0.y + 2 * u * param * p1.y + param * param * p2.y
        )
    }

    override fun direction(param: Double): Vec2 {
        val v0 = p1 - p0
        val v1 = p2 - p1
        val tangent = v0 * (1 - param) + v1 * param
        if (tangent.isZero()) return v0 + v1
        return tangent
    }

    override fun directionChange(param: Double) = (p2 - p1) - (p1 - p0)

    override fun controlPoints() = arrayOf(p0, p1, p2)

    override fun length(): Double {
        val ab = p1 - p0
        val br = (p2 - p1) - ab
        val abab = ab dot ab
        val abbr = ab dot br
        val brbr = br dot br
        val abLen = sqrt(abab)
        val brLen = sqrt(brbr)
        val crs = ab cross br
        val h = sqrt(abab + abbr + abbr + brbr)
        return (brLen * ((abbr + brbr) * h - abbr * abLen) + crs * crs * ln((brLen * h + abbr + brbr) / (brLen * abLen + abbr))) / (brbr * brLen)
    }

    override fun signedDistance(origin: Point): DistanceResult {
        val qa = p0 - origin
        val ab = p1 - p0
        val br = (p2 - p1) - ab
        val a = br dot br
        val b = 3.0 * (ab dot br)
        val c = 2.0 * (ab dot ab) + (qa dot br)
        val d = qa dot ab

        val roots = DoubleArray(3)
        val solutions = EquationSolver.solveCubic(roots, a, b, c, d)

        var epDir = direction(0.0)
        var minDistance = Arithmetic.nonZeroSign((epDir cross qa)) * qa.length()
        var param = -(qa dot epDir) / (epDir dot epDir)

        val endPoint = p2 - origin
        val endDist = endPoint.length()
        if (endDist < abs(minDistance)) {
            epDir = direction(1.0)
            minDistance = Arithmetic.nonZeroSign((epDir cross endPoint)) * endDist
            param = ((origin - p1) dot epDir) / (epDir dot epDir)
        }

        for (i in 0 until solutions) {
            val t = roots[i]
            if (t in 0.0..1.0) {
                val qe = qa + ab * (2.0 * t) + br * (t * t)
                val dist = qe.length()
                if (dist <= abs(minDistance)) {
                    val tangent = ab + br * t
                    minDistance = Arithmetic.nonZeroSign((tangent cross qe)) * dist
                    param = t
                }
            }
        }

        if (param in 0.0..1.0) return DistanceResult(minDistance, param, 0.0)

        val dot = if (param < 0.5) {
            val dirNorm = direction(0.0).normalized()
            val qaNorm = if (qa.length() > 0.0) qa.normalized() else Vec2(0.0, 0.0)
            abs(dirNorm dot qaNorm)
        } else {
            val dirNorm = direction(1.0).normalized()
            val endNorm = if (endDist > 0.0) endPoint.normalized() else Vec2(0.0, 0.0)
            abs(dirNorm dot endNorm)
        }
        return DistanceResult(minDistance, param, dot)
    }

    override fun scanlineIntersections(x: DoubleArray, dy: IntArray, y: Double): Int {
        var total = 0
        var nextDY = if (y > p0.y) 1 else -1
        x[total] = p0.x
        if (p0.y == y) {
            if (p0.y < p1.y || (p0.y == p1.y && p0.y < p2.y)) dy[total++] = 1
            else nextDY = 1
        }
        val ab = p1 - p0
        val br = (p2 - p1) - ab
        val t = DoubleArray(2)
        val sol = EquationSolver.solveQuadratic(t, br.y, 2.0 * ab.y, p0.y - y)
        if (sol >= 2 && t[0] > t[1]) {
            val tmp = t[0]; t[0] = t[1]; t[1] = tmp
        }
        for (i in 0 until sol) {
            if (total >= 2) break
            if (t[i] in 0.0..1.0) {
                x[total] = p0.x + 2.0 * t[i] * ab.x + t[i] * t[i] * br.x
                if (nextDY * (ab.y + t[i] * br.y) >= 0) {
                    dy[total++] = nextDY
                    nextDY = -nextDY
                }
            }
        }
        if (p2.y == y) {
            if (nextDY > 0 && total > 0) {
                --total
                nextDY = -1
            }
            if ((p2.y < p1.y || (p2.y == p1.y && p2.y < p0.y)) && total < 2) {
                x[total] = p2.x
                if (nextDY < 0) {
                    dy[total++] = -1
                    nextDY = 1
                }
            }
        }
        if (nextDY != if (y >= p2.y) 1 else -1) {
            if (total > 0) --total
            else {
                if (abs(p2.y - y) < abs(p0.y - y)) x[total] = p2.x
                dy[total++] = nextDY
            }
        }
        return total
    }

    override fun bound(bounds: DoubleArray) {
        fun update(p: Point) {
            if (p.x < bounds[0]) bounds[0] = p.x
            if (p.y < bounds[1]) bounds[1] = p.y
            if (p.x > bounds[2]) bounds[2] = p.x
            if (p.y > bounds[3]) bounds[3] = p.y
        }
        update(p0)
        update(p2)
        val bot = (p1 - p0) - (p2 - p1)
        if (bot.x != 0.0) {
            val param = (p1.x - p0.x) / bot.x
            if (param in 0.0..1.0) update(point(param))
        }
        if (bot.y != 0.0) {
            val param = (p1.y - p0.y) / bot.y
            if (param in 0.0..1.0) update(point(param))
        }
    }

    override fun reverse() = QuadraticSegment(p2, p1, p0, color)

    override fun moveStartPoint(to: Point): QuadraticSegment {
        val origSDir = p0 - p1
        val origP1 = p1
        val denom = (p0 - p1) cross (p2 - p1)
        val newP1 = if (denom != 0.0) {
            val factor = ((p0 - p1) cross (to - p0)) / denom
            Point(p1.x + factor * (p2.x - p1.x), p1.y + factor * (p2.y - p1.y))
        } else p1
        val result = QuadraticSegment(to, newP1, p2, color)
        return if ((origSDir dot (to - newP1)) < 0) QuadraticSegment(to, origP1, p2, color) else result
    }

    override fun moveEndPoint(to: Point): QuadraticSegment {
        val origEDir = p2 - p1
        val origP1 = p1
        val denom = (p2 - p1) cross (p0 - p1)
        val newP1 = if (denom != 0.0) {
            val factor = ((p2 - p1) cross (to - p2)) / denom
            Point(p1.x + factor * (p0.x - p1.x), p1.y + factor * (p0.y - p1.y))
        } else p1
        val result = QuadraticSegment(p0, newP1, to, color)
        return if ((origEDir dot (to - newP1)) < 0) QuadraticSegment(p0, origP1, to, color) else result
    }

    override fun splitInThirds(): Array<EdgeSegment> {
        val p01 = point(1.0 / 3.0)
        val p12 = point(2.0 / 3.0)
        val midControl = Point(
            (mix(p0.x, p1.x, 5.0 / 9.0) + mix(p1.x, p2.x, 4.0 / 9.0)) * 0.5,
            (mix(p0.y, p1.y, 5.0 / 9.0) + mix(p1.y, p2.y, 4.0 / 9.0)) * 0.5
        )
        return arrayOf(
            QuadraticSegment(p0, mix(p0, p1, 1.0 / 3.0), p01, color),
            QuadraticSegment(p01, midControl, p12, color),
            QuadraticSegment(p12, mix(p1, p2, 2.0 / 3.0), p2, color)
        )
    }

    fun convertToCubic(): CubicSegment = CubicSegment(
        p0,
        mix(p0, p1, 2.0 / 3.0),
        mix(p1, p2, 1.0 / 3.0),
        p2,
        color
    )

    private fun mix(a: Double, b: Double, t: Double) = (1 - t) * a + t * b
    private fun mix(a: Point, b: Point, t: Double) = Point(mix(a.x, b.x, t), mix(a.y, b.y, t))
}

data class CubicSegment(
    val p0: Point,
    val p1: Point,
    val p2: Point,
    val p3: Point,
    override val color: Int = EdgeColor.WHITE
) :
    EdgeSegment(color) {

    override val type: Int = 3

    override fun point(param: Double): Point {
        val u = 1 - param
        return Point(
            u * u * u * p0.x + 3 * u * u * param * p1.x + 3 * u * param * param * p2.x + param * param * param * p3.x,
            u * u * u * p0.y + 3 * u * u * param * p1.y + 3 * u * param * param * p2.y + param * param * param * p3.y
        )
    }

    override fun direction(param: Double): Vec2 {
        val v0 = p1 - p0
        val v1 = p2 - p1
        val v2 = p3 - p2
        val d1 = v0 * (1 - param) + v1 * param
        val d2 = v1 * (1 - param) + v2 * param
        val tangent = d1 * (1 - param) + d2 * param
        if (tangent.isZero()) {
            if (param == 0.0) return v0 + v1
            if (param == 1.0) return v1 + v2
        }
        return tangent
    }

    override fun directionChange(param: Double) =
        ((p2 - p1) - (p1 - p0)) * (1 - param) + ((p3 - p2) - (p2 - p1)) * param

    override fun controlPoints() = arrayOf(p0, p1, p2, p3)

    override fun signedDistance(origin: Point): DistanceResult {
        val qa = p0 - origin
        val ab = p1 - p0
        val br = (p2 - p1) - ab
        val as_ = ((p3 - p2) - (p2 - p1)) - br

        var epDir = direction(0.0)
        var minDistance = Arithmetic.nonZeroSign((epDir cross qa)) * qa.length()
        var param = -(qa dot epDir) / (epDir dot epDir)

        val endPoint = p3 - origin
        val endDist = endPoint.length()
        if (endDist < abs(minDistance)) {
            epDir = direction(1.0)
            minDistance = Arithmetic.nonZeroSign((epDir cross endPoint)) * endDist
            param = ((epDir - endPoint) dot epDir) / (epDir dot epDir)
        }

        for (i in 0..Constants.CUBIC_SEARCH_STARTS) {
            var t = i.toDouble() / Constants.CUBIC_SEARCH_STARTS
            var qe = qa + ab * (3.0 * t) + br * (3.0 * t * t) + as_ * (t * t * t)
            var d1 = ab * 3.0 + br * (6.0 * t) + as_ * (3.0 * t * t)
            var d2 = br * 6.0 + as_ * (6.0 * t)
            var improvedT = t - (qe dot d1) / ((d1 dot d1) + (qe dot d2))
            if (improvedT > 0.0 && improvedT < 1.0) {
                var remaining = Constants.CUBIC_SEARCH_STEPS
                do {
                    t = improvedT
                    qe = qa + ab * (3.0 * t) + br * (3.0 * t * t) + as_ * (t * t * t)
                    d1 = ab * 3.0 + br * (6.0 * t) + as_ * (3.0 * t * t)
                    if (--remaining == 0) break
                    d2 = br * 6.0 + as_ * (6.0 * t)
                    improvedT = t - (qe dot d1) / ((d1 dot d1) + (qe dot d2))
                } while (improvedT > 0.0 && improvedT < 1.0)
                val dist = qe.length()
                if (dist < abs(minDistance)) {
                    minDistance = Arithmetic.nonZeroSign((d1 cross qe)) * dist
                    param = t
                }
            }
        }

        if (param in 0.0..1.0) return DistanceResult(minDistance, param, 0.0)

        val dot = if (param < 0.5) {
            val dirNorm = direction(0.0).normalized()
            val qaNorm = if (qa.length() > 0.0) qa.normalized() else Vec2(0.0, 0.0)
            abs(dirNorm dot qaNorm)
        } else {
            val dirNorm = direction(1.0).normalized()
            val endNorm = if (endDist > 0.0) endPoint.normalized() else Vec2(0.0, 0.0)
            abs(dirNorm dot endNorm)
        }
        return DistanceResult(minDistance, param, dot)
    }

    override fun scanlineIntersections(x: DoubleArray, dy: IntArray, y: Double): Int {
        var total = 0
        var nextDY = if (y > p0.y) 1 else -1
        x[total] = p0.x
        if (p0.y == y) {
            if (p0.y < p1.y || (p0.y == p1.y && (p0.y < p2.y || (p0.y == p2.y && p0.y < p3.y)))) dy[total++] = 1
            else nextDY = 1
        }
        val ab = p1 - p0
        val br = (p2 - p1) - ab
        val as_ = ((p3 - p2) - (p2 - p1)) - br
        val t = DoubleArray(3)
        val sol = EquationSolver.solveCubic(t, as_.y, 3.0 * br.y, 3.0 * ab.y, p0.y - y)
        if (sol >= 2) {
            if (t[0] > t[1]) {
                val tmp = t[0]; t[0] = t[1]; t[1] = tmp
            }
            if (sol >= 3 && t[1] > t[2]) {
                val tmp = t[1]; t[1] = t[2]; t[2] = tmp
                if (t[0] > t[1]) {
                    val tmp2 = t[0]; t[0] = t[1]; t[1] = tmp2
                }
            }
        }
        for (i in 0 until sol) {
            if (total >= 3) break
            if (t[i] in 0.0..1.0) {
                x[total] = p0.x + 3.0 * t[i] * ab.x + 3.0 * t[i] * t[i] * br.x + t[i] * t[i] * t[i] * as_.x
                if (nextDY * (ab.y + 2.0 * t[i] * br.y + t[i] * t[i] * as_.y) >= 0) {
                    dy[total++] = nextDY
                    nextDY = -nextDY
                }
            }
        }
        if (p3.y == y) {
            if (nextDY > 0 && total > 0) {
                --total
                nextDY = -1
            }
            if ((p3.y < p2.y || (p3.y == p2.y && (p3.y < p1.y || (p3.y == p1.y && p3.y < p0.y)))) && total < 3) {
                x[total] = p3.x
                if (nextDY < 0) {
                    dy[total++] = -1
                    nextDY = 1
                }
            }
        }
        if (nextDY != if (y >= p3.y) 1 else -1) {
            if (total > 0) --total
            else {
                if (abs(p3.y - y) < abs(p0.y - y)) x[total] = p3.x
                dy[total++] = nextDY
            }
        }
        return total
    }

    override fun bound(bounds: DoubleArray) {
        fun update(p: Point) {
            if (p.x < bounds[0]) bounds[0] = p.x
            if (p.y < bounds[1]) bounds[1] = p.y
            if (p.x > bounds[2]) bounds[2] = p.x
            if (p.y > bounds[3]) bounds[3] = p.y
        }
        update(p0)
        update(p3)
        val a0 = p1 - p0
        val a1 = ((p2 - p1) - a0) * 2.0
        val a2 = Point(p3.x - 3 * p2.x + 3 * p1.x - p0.x, p3.y - 3 * p2.y + 3 * p1.y - p0.y)
        val params = DoubleArray(2)
        var solutions = EquationSolver.solveQuadratic(params, a2.x, a1.x, a0.x)
        for (i in 0 until solutions) if (params[i] in 0.0..1.0) update(point(params[i]))
        solutions = EquationSolver.solveQuadratic(params, a2.y, a1.y, a0.y)
        for (i in 0 until solutions) if (params[i] in 0.0..1.0) update(point(params[i]))
    }

    override fun reverse() = CubicSegment(p3, p2, p1, p0, color)

    override fun moveStartPoint(to: Point): CubicSegment {
        return CubicSegment(to, Point(p1.x + to.x - p0.x, p1.y + to.y - p0.y), p2, p3, color)
    }

    override fun moveEndPoint(to: Point): CubicSegment {
        return CubicSegment(p0, p1, Point(p2.x + to.x - p3.x, p2.y + to.y - p3.y), to, color)
    }

    override fun splitInThirds(): Array<EdgeSegment> {
        val p01 = point(1.0 / 3.0)
        val p12 = point(2.0 / 3.0)
        val cp1 = if (p0 == p1) p0 else mix(p0, p1, 1.0 / 3.0)
        val cp2 = mix(mix(p0, p1, 1.0 / 3.0), mix(p1, p2, 1.0 / 3.0), 1.0 / 3.0)
        val cp5 = mix(mix(p1, p2, 2.0 / 3.0), mix(p2, p3, 2.0 / 3.0), 2.0 / 3.0)
        val cp6 = if (p2 == p3) p3 else mix(p2, p3, 2.0 / 3.0)
        return arrayOf(
            CubicSegment(p0, cp1, cp2, p01, color),
            CubicSegment(
                p01,
                mix(
                    mix(mix(p0, p1, 1.0 / 3.0), mix(p1, p2, 1.0 / 3.0), 1.0 / 3.0),
                    mix(mix(p1, p2, 1.0 / 3.0), mix(p2, p3, 1.0 / 3.0), 1.0 / 3.0),
                    2.0 / 3.0
                ),
                mix(
                    mix(mix(p0, p1, 2.0 / 3.0), mix(p1, p2, 2.0 / 3.0), 2.0 / 3.0),
                    mix(mix(p1, p2, 2.0 / 3.0), mix(p2, p3, 2.0 / 3.0), 2.0 / 3.0),
                    1.0 / 3.0
                ),
                p12, color
            ),
            CubicSegment(p12, cp5, cp6, p3, color)
        )
    }

    private fun mix(a: Double, b: Double, t: Double) = (1 - t) * a + t * b
    private fun mix(a: Point, b: Point, t: Double) = Point(mix(a.x, b.x, t), mix(a.y, b.y, t))
}