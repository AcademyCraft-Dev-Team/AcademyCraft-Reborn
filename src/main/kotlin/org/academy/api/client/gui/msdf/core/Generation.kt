package org.academy.api.client.gui.msdf.core

import java.util.stream.IntStream

class FloatBitmap(val width: Int, val height: Int, val nChannels: Int) {
    val pixels = FloatArray(nChannels * width * height)

    fun toRef(): FloatBitmapRef {
        return FloatBitmapRef(pixels, width, height, nChannels, nChannels * width, YAxisOrientation.Y_UPWARD)
    }
}

class FloatBitmapRef(
    val pixels: FloatArray,
    val width: Int,
    val height: Int,
    val nChannels: Int,
    var rowStride: Int,
    var yOrientation: YAxisOrientation
) {
    private var offset: Int = 0

    fun getIndex(x: Int, y: Int): Int {
        return offset + rowStride * y + nChannels * x
    }

    fun reorient(newYAxisOrientation: YAxisOrientation) {
        if (yOrientation != newYAxisOrientation) {
            offset += rowStride * (height - 1)
            rowStride = -rowStride
            yOrientation = newYAxisOrientation
        }
    }
}

class Projection(
    val scale: Vec2 = Vec2(1.0, 1.0),
    val translate: Vec2 = Vec2(0.0, 0.0)
) {
    fun unproject(coord: Point): Point {
        return Point(
            coord.x / scale.x - translate.x,
            coord.y / scale.y - translate.y
        )
    }
}

data class GeneratorConfig(
    val width: Int,
    val height: Int,
    val pxRange: Double,
    val overlapSupport: Boolean = false,
    val angleThreshold: Double = 3.0,
    val seed: Long = 0L,
    val projection: Projection = Projection(),
    val distanceMapping: DistanceMapping? = null
)

object MsdfGenerator {
    fun generate(shape: Shape, config: GeneratorConfig): FloatBitmap {
        val processed = shape.normalize().orient()
        val colored = EdgeColoring.colorShape(processed, config.angleThreshold, config.seed)

        val distanceMapping = config.distanceMapping ?: run {
            val bounds = colored.getBounds()
            val maxDim = maxOf(bounds.r - bounds.l, bounds.t - bounds.b)
            DistanceMapping(Range(bounds.l, bounds.l + maxDim))
        }

        val bitmap = FloatBitmap(config.width, config.height, 3)
        val output = bitmap.toRef()

        val transformation = config.projection

        output.reorient(
            if (colored.getYAxisOrientation() == 1) YAxisOrientation.Y_DOWNWARD else YAxisOrientation.Y_UPWARD
        )

        IntStream.range(0, output.height).parallel().forEach { y ->
            val combiner: ContourCombiner<MultiDistance> = if (config.overlapSupport) {
                OverlappingCombiner(colored)
            } else {
                SimpleCombiner()
            }

            val pixel = FloatArray(output.nChannels)
            var p: Point

            val xDirection = if (y % 2 == 0) 1 else -1
            var x = if (xDirection == 1) 0 else output.width - 1

            repeat(output.width) {
                p = Point(x + 0.5, y + 0.5)
                val unprojected = transformation.unproject(p)

                combiner.reset(unprojected)
                for (i in colored.contours.indices) {
                    val contour = colored.contours[i]
                    if (contour.edges.isEmpty()) continue
                    val selector = combiner.edgeSelector(i)
                    val edgeCount = contour.edges.size
                    var prevEdge = if (edgeCount >= 2) contour.edges[edgeCount - 2] else contour.edges.first()
                    var curEdge = contour.edges[edgeCount - 1]
                    for (edge in contour.edges) {
                        selector.addEdge(prevEdge, curEdge, edge)
                        prevEdge = curEdge
                        curEdge = edge
                    }
                }

                val distance = combiner.combine()
                distanceToMsdfPixel(pixel, distance, distanceMapping)
                System.arraycopy(pixel, 0, output.pixels, output.getIndex(x, y), output.nChannels)

                x += xDirection
            }
        }

        return bitmap
    }

    private fun distanceToMsdfPixel(pixels: FloatArray, distance: MultiDistance, mapping: DistanceMapping) {
        pixels[0] = mapping.apply(distance.r).toFloat()
        pixels[1] = mapping.apply(distance.g).toFloat()
        pixels[2] = mapping.apply(distance.b).toFloat()
    }
}