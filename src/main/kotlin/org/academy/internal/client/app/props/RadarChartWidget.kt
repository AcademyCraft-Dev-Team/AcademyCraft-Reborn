package org.academy.internal.client.app.props

import com.mojang.math.Axis
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.common.attribute.AbilityFactor
import org.academy.internal.common.attribute.PropsMath
import kotlin.math.*

class RadarChartWidget : AbstractWidget() {
    private data class Point(val x: Float, val y: Float)

    init {
        bypassRenderCache = true
    }

    override fun renderInternal(context: RenderContext) {
        super.renderInternal(context)
        if (width <= 2f || height <= 2f) return

        val center = Point(width / 2f, height / 2f)
        val radius = minOf(width, height) * 0.42f
        val outer = points(center, radius)

        for (ring in 1..4) {
            val ringPoints = points(center, radius * ring / 4f)
            drawPolygon(context, ringPoints, 0.55f, 0.65f, 0.72f, if (ring == 4) 0.65f else 0.22f, 0.55f)
        }
        outer.forEach { drawLine(context, center, it, 0.45f, 0.55f, 0.62f, 0.28f, 0.5f) }

        val values = AbilityFactor.values().mapIndexed { index, factor ->
            val ratio = (PropsClientState.get(factor) / PropsMath.MAX_TOTAL).coerceIn(0.0, 1.0).toFloat()
            val angle = -PI / 2.0 + index * PI * 2.0 / AbilityFactor.values().size
            Point(
                center.x + cos(angle).toFloat() * radius * ratio,
                center.y + sin(angle).toFloat() * radius * ratio
            )
        }
        drawPolygon(context, values, 0.32f, 0.9f, 1f, 0.95f, 1.15f)
    }

    private fun points(center: Point, radius: Float): List<Point> =
        AbilityFactor.values().indices.map { index ->
            val angle = -PI / 2.0 + index * PI * 2.0 / AbilityFactor.values().size
            Point(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius
            )
        }

    private fun drawPolygon(
        context: RenderContext,
        points: List<Point>,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        thickness: Float
    ) {
        if (points.size < 2) return
        points.indices.forEach { index ->
            drawLine(context, points[index], points[(index + 1) % points.size], red, green, blue, alpha, thickness)
        }
    }

    private fun drawLine(
        context: RenderContext,
        from: Point,
        to: Point,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        thickness: Float
    ) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = hypot(dx, dy)
        if (length <= 0.001f) return
        context.pose().pushPose()
        context.pose().translate(from.x, from.y - thickness / 2f)
        context.pose().mulPose(Axis.ZP.rotationDegrees(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()))
        context.submit(
            FillRectDrawCommand(
                length,
                thickness,
                red,
                green,
                blue,
                alpha * context.accumulatedAlpha
            )
        )
        context.pose().popPose()
    }
}
