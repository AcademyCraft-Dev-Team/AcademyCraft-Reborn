package org.academy.api.client.gui.msdf.core

import kotlin.math.*

data class Vec2(val x: Double, val y: Double)

typealias Point = Vec2

operator fun Vec2.plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
operator fun Vec2.minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
operator fun Vec2.times(scalar: Double): Vec2 = Vec2(x * scalar, y * scalar)
operator fun Vec2.div(scalar: Double): Vec2 = Vec2(x / scalar, y / scalar)
operator fun Vec2.unaryMinus(): Vec2 = Vec2(-x, -y)

infix fun Vec2.dot(other: Vec2): Double = x * other.x + y * other.y
infix fun Vec2.cross(other: Vec2): Double = x * other.y - y * other.x

fun Vec2.length(): Double = sqrt(x * x + y * y)

fun Vec2.normalized(allowZero: Boolean = true): Vec2 {
    val len = length()
    return if (len != 0.0) Vec2(x / len, y / len) else Vec2(0.0, if (allowZero) 0.0 else 1.0)
}

fun Vec2.orthogonal(polarity: Boolean): Vec2 =
    if (polarity) Vec2(-y, x) else Vec2(y, -x)

fun Vec2.isZero(): Boolean = x == 0.0 && y == 0.0

object Arithmetic {
    fun clamp(n: Double, max: Double): Double = min(max, max(n, 0.0))

    fun sign(n: Double): Int = (if (0 < n) 1 else 0) - (if (n < 0) 1 else 0)

    fun nonZeroSign(n: Double): Int = 2 * (if (n > 0) 1 else 0) - 1
}

object EquationSolver {
    fun solveQuadratic(x: DoubleArray, a: Double, b: Double, c: Double): Int {
        if (a == 0.0 || abs(b) > Constants.SOLVE_QUADRATIC_LARGE_B_THRESHOLD * abs(a)) {
            if (b == 0.0) {
                return if (c == 0.0) -1 else 0
            }
            x[0] = -c / b
            return 1
        }
        var dscr = b * b - 4 * a * c
        if (dscr > 0) {
            dscr = sqrt(dscr)
            val div = 1.0 / (2 * a)
            x[0] = (-b + dscr) * div
            x[1] = (-b - dscr) * div
            return 2
        } else if (dscr == 0.0) {
            x[0] = -b / (2 * a)
            return 1
        } else {
            return 0
        }
    }

    private fun solveCubicNormed(x: DoubleArray, a: Double, b: Double, c: Double): Int {
        val a2 = a * a
        val q = (a2 - 3 * b) * Constants.INV_9
        val r = (a * (2 * a2 - 9 * b) + 27 * c) * Constants.INV_54
        val r2 = r * r
        val q3 = q * q * q
        val aShift = a * Constants.INV_3

        if (r2 < q3) {
            var t = r / sqrt(q3)
            if (t < -1) t = -1.0
            if (t > 1) t = 1.0
            t = acos(t)
            val sqrtQ = -2 * sqrt(q)
            x[0] = sqrtQ * cos(t * Constants.INV_3) - aShift
            x[1] = sqrtQ * cos((t + 2 * Math.PI) * Constants.INV_3) - aShift
            x[2] = sqrtQ * cos((t - 2 * Math.PI) * Constants.INV_3) - aShift
            return 3
        } else {
            val A = abs(r) + sqrt(r2 - q3)
            val u = (if (r < 0) 1 else -1) * cbrt(A)
            val v = if (u == 0.0) 0.0 else q / u
            x[0] = (u + v) - aShift
            if (u == v || abs(u - v) < Constants.SOLVE_CUBIC_DOUBLE_ROOT_EPSILON * abs(u + v)) {
                x[1] = -0.5 * (u + v) - aShift
                return 2
            }
            return 1
        }
    }

    fun solveCubic(x: DoubleArray, a: Double, b: Double, c: Double, d: Double): Int {
        if (a != 0.0) {
            val bn = b / a
            if (abs(bn) < 1e6) {
                return solveCubicNormed(x, bn, c / a, d / a)
            }
        }
        return solveQuadratic(x, b, c, d)
    }
}