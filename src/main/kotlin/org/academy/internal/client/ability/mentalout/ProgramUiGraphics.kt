package org.academy.internal.client.ability.mentalout

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.ScissorRect
import org.academy.api.client.gui.text.model.TextShapingOptions
import org.academy.api.client.gui.text.record.TextPainter
import org.academy.api.client.gui.text.shape.TextMeasurer
import org.academy.api.client.gui.widget.TextWidget
import kotlin.math.max
import kotlin.math.roundToInt

class ProgramUiGraphics(private val context: Canvas) {
    private val textPainter = TextPainter()

    fun pose(): Canvas.PoseStack2D = context.pose()

    fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0 || (color ushr 24) == 0) return
        context.pose().pushPose()
        context.pose().translate(left.toFloat(), top.toFloat())
        context.submit(
            FillRectDrawCommand(
                width.toFloat(),
                height.toFloat(),
                red(color),
                green(color),
                blue(color),
                alpha(color) * context.accumulatedAlpha
            )
        )
        context.pose().popPose()
    }

    fun enableScissor(left: Int, top: Int, right: Int, bottom: Int) {
        context.enableScissor(
            ScissorRect(
                left.toFloat(), top.toFloat(),
                max(0, right - left).toFloat(), max(0, bottom - top).toFloat()
            )
        )
    }

    fun disableScissor() {
        context.disableScissor()
    }

    fun text(value: String, x: Float, y: Float, color: Int, fontSize: Float, maxWidth: Float) {
        val clipped = fit(value, maxWidth, fontSize)
        if (clipped.isEmpty()) return
        context.pose().pushPose()
        context.pose().translate(x, y)
        textPainter.draw(
            context, clipped, fontSize,
            red(color), green(color), blue(color), TextShapingOptions.DEFAULT,
            0f, 0f, 1f, alpha(color)
        )
        context.pose().popPose()
    }

    fun centeredText(value: String, centerX: Float, y: Float, color: Int, fontSize: Float, maxWidth: Float) {
        val clipped = fit(value, maxWidth, fontSize)
        val width = TextWidget.getTextWidth(clipped, fontSize)
        text(clipped, centerX - width / 2f, y, color, fontSize, maxWidth)
    }

    companion object {
        const val BODY_FONT_SIZE: Float = 7.5f
        const val CAPTION_FONT_SIZE: Float = 6.75f
        const val HEADING_FONT_SIZE: Float = 8.5f
        private const val ELLIPSIS: String = "…"

        @JvmStatic
        fun fit(value: String?, maxWidth: Float, fontSize: Float): String {
            if (value == null) return ""
            return TextMeasurer.ellipsize(value, fontSize, maxWidth, ELLIPSIS)
        }

        @JvmStatic
        fun wrap(value: String?, maxWidth: Float, fontSize: Float): List<String> {
            if (value.isNullOrEmpty()) return listOf("")
            return TextMeasurer.wrapLines(value, fontSize, maxWidth)
        }

        @JvmStatic
        fun wrappedHeight(value: String?, maxWidth: Float, fontSize: Float, lineHeight: Float): Int =
            (wrap(value, maxWidth, fontSize).size * lineHeight).roundToInt()

        private fun alpha(color: Int): Float = (color ushr 24 and 0xFF) / 255f

        private fun red(color: Int): Float = (color ushr 16 and 0xFF) / 255f

        private fun green(color: Int): Float = (color ushr 8 and 0xFF) / 255f

        private fun blue(color: Int): Float = (color and 0xFF) / 255f
    }
}
