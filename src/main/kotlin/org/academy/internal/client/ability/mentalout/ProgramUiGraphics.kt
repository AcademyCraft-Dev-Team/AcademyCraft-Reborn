package org.academy.internal.client.ability.mentalout

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.ScissorRect
import org.academy.api.client.gui.text.model.TextShapingOptions
import org.academy.api.client.gui.text.record.TextPainter
import org.academy.api.client.gui.text.shape.TextMeasurer
import org.academy.api.client.gui.widget.TextWidget
import java.text.BreakIterator
import java.util.Locale
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
            return wrapMeasured(
                value, maxWidth,
                { TextMeasurer.measureWidth(it, fontSize) },
                { TextMeasurer.wrapLines(it, fontSize, maxWidth) }
            )
        }

        /**
         * Keep the shaper's usual line breaks when they fit the same measurement used by [fit].
         * AWT wrapping and glyph shaping can disagree, so reflow only affected paragraphs.
         * A single code point wider than the available space is retained to guarantee progress.
         */
        internal fun wrapMeasured(
            value: String?,
            maxWidth: Float,
            measureWidth: (String) -> Float,
            suggestedLines: (String) -> List<String> = { listOf(it) }
        ): List<String> {
            if (value.isNullOrEmpty()) return listOf("")
            val result = ArrayList<String>()
            for (paragraph in value.split('\n')) {
                if (paragraph.isEmpty()) {
                    result.add("")
                    continue
                }
                val suggested = suggestedLines(paragraph)
                if (suggested.isNotEmpty() && suggested.joinToString("") == paragraph &&
                    suggested.all { it.isNotEmpty() && measureWidth(it) <= maxWidth } &&
                    suggested.dropLast(1).none { Character.isHighSurrogate(it.last()) }
                ) {
                    result.addAll(suggested)
                    continue
                }

                val breaks = BreakIterator.getLineInstance(Locale.ROOT)
                breaks.setText(paragraph)
                var start = 0
                while (start < paragraph.length) {
                    val remaining = paragraph.substring(start)
                    if (measureWidth(remaining) <= maxWidth) {
                        result.add(remaining)
                        break
                    }
                    var low = 0
                    var high = remaining.codePointCount(0, remaining.length)
                    while (low < high) {
                        val middle = (low + high + 1) ushr 1
                        val end = remaining.offsetByCodePoints(0, middle)
                        if (measureWidth(remaining.substring(0, end)) <= maxWidth) {
                            low = middle
                        } else {
                            high = middle - 1
                        }
                    }
                    var end = start + remaining.offsetByCodePoints(0, max(1, low))
                    val wordEnd = if (breaks.isBoundary(end)) end else breaks.preceding(end)
                    if (wordEnd > start && measureWidth(paragraph.substring(start, wordEnd)) <= maxWidth) {
                        end = wordEnd
                    }
                    result.add(paragraph.substring(start, end))
                    start = end
                }
            }
            return result
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
