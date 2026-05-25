package org.academy.api.client.gui.msdf.core

import kotlin.math.abs
import kotlin.math.sin

object EdgeColoring {
    private fun symmetricalTrichotomy(position: Int, n: Int): Int {
        return (3 + 2.875 * position / (n - 1) - 1.4375 + 0.5).toInt() - 3
    }

    private fun isCorner(aDir: Vec2, bDir: Vec2, crossThreshold: Double): Boolean {
        return (aDir dot bDir) <= 0 || abs(aDir cross bDir) > crossThreshold
    }

    private fun seedExtract2(seed: LongArray): Int {
        val v = (seed[0] and 1L).toInt()
        seed[0] = seed[0] shr 1
        return v
    }

    private fun seedExtract3(seed: LongArray): Int {
        val v = (seed[0] % 3).toInt()
        seed[0] /= 3
        return v
    }

    private fun initColor(seed: LongArray): Int {
        val colors = intArrayOf(EdgeColor.CYAN, EdgeColor.MAGENTA, EdgeColor.YELLOW)
        return colors[seedExtract3(seed)]
    }

    private fun switchColor(color: Int, seed: LongArray): Int {
        val shifted = color shl (1 + seedExtract2(seed))
        return (shifted or (shifted shr 3)) and EdgeColor.WHITE
    }

    private fun switchColor(color: Int, seed: LongArray, banned: Int): Int {
        val combined = color and banned
        return if (combined == EdgeColor.RED || combined == EdgeColor.GREEN || combined == EdgeColor.BLUE)
            combined xor EdgeColor.WHITE
        else
            switchColor(color, seed)
    }

    fun colorShape(shape: Shape, angleThreshold: Double, seed: Long): Shape {
        val crossThreshold = sin(angleThreshold)
        val seedRef = longArrayOf(seed)
        var color = initColor(seedRef)
        val corners = mutableListOf<Int>()

        val newContours = mutableListOf<Contour>()

        for (contour in shape.contours) {
            if (contour.edges.isEmpty()) {
                newContours.add(contour)
                continue
            }

            corners.clear()
            var prevDirection = contour.edges.last().direction(1.0)
            for ((index, edge) in contour.edges.withIndex()) {
                if (isCorner(
                        prevDirection.normalized(),
                        edge.direction(0.0).normalized(),
                        crossThreshold
                    )
                ) {
                    corners.add(index)
                }
                prevDirection = edge.direction(1.0)
            }

            val newEdges: List<EdgeSegment>

            if (corners.isEmpty()) {
                color = switchColor(color, seedRef)
                newEdges = contour.edges.map { it.withColor(color) }
            } else if (corners.size == 1) {
                val colorsArr = IntArray(3)
                color = switchColor(color, seedRef)
                colorsArr[0] = color
                colorsArr[1] = EdgeColor.WHITE
                color = switchColor(color, seedRef)
                colorsArr[2] = color
                val corner = corners.first()

                val m = contour.edges.size
                if (m >= 3) {
                    newEdges = contour.edges.mapIndexed { i, edge ->
                        val idx = (i - corner + m) % m
                        val col = colorsArr[1 + symmetricalTrichotomy(idx, m)]
                        edge.withColor(col)
                    }
                } else {
                    val parts = mutableListOf<EdgeSegment?>()
                    contour.edges.forEachIndexed { _, edge ->
                        val segs = edge.splitInThirds()
                        parts.add(segs[0])
                        parts.add(segs[1])
                        parts.add(segs[2])
                    }
                    val totalParts = parts.size
                    if (totalParts == 3) {
                        parts[0] = parts[0]?.withColor(colorsArr[0])
                        parts[1] = parts[1]?.withColor(colorsArr[1])
                        parts[2] = parts[2]?.withColor(colorsArr[2])
                    } else if (totalParts == 6) {
                        val shift = if (corner == 0) 0 else 3
                        parts[shift + 0] = parts[shift + 0]?.withColor(colorsArr[0])
                        parts[shift + 1] = parts[shift + 1]?.withColor(colorsArr[0])
                        parts[shift + 2] = parts[shift + 2]?.withColor(colorsArr[1])
                        parts[(shift + 3) % 6] = parts[(shift + 3) % 6]?.withColor(colorsArr[1])
                        parts[(shift + 4) % 6] = parts[(shift + 4) % 6]?.withColor(colorsArr[2])
                        parts[(shift + 5) % 6] = parts[(shift + 5) % 6]?.withColor(colorsArr[2])
                    }
                    newEdges = parts.filterNotNull()
                }
            } else {
                val cornerCount = corners.size
                var spline = 0
                val start = corners.first()
                val m = contour.edges.size
                color = switchColor(color, seedRef)
                val initialColor = color
                newEdges = contour.edges.mapIndexed { i, edge ->
                    val currentIndex = (start + i) % m
                    if (spline + 1 < cornerCount && corners[spline + 1] == currentIndex) {
                        spline++
                        color = switchColor(color, seedRef, if (spline == cornerCount - 1) initialColor else 0)
                    }
                    edge.withColor(color)
                }
            }

            newContours.add(Contour(newEdges))
        }

        return Shape(newContours, shape.inverseYAxis)
    }

    private fun EdgeSegment.withColor(newColor: Int): EdgeSegment {
        return when (this) {
            is LinearSegment -> copy(color = newColor)
            is QuadraticSegment -> copy(color = newColor)
            is CubicSegment -> copy(color = newColor)
        }
    }
}