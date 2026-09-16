package org.academy.api.client.gui.text.shape

import org.academy.api.client.gui.text.model.TextShapingOptions

object TextMeasurer {
    fun measureWidth(text: CharSequence, fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float =
        ShapingCache.blob(text, fontSize, options).width

    fun measureHeight(text: CharSequence, fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float =
        ShapingCache.blob(text, fontSize, options).height

    fun wrap(text: String, fontSize: Float, maxWidth: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): String =
        TextShaper.wrap(text, fontSize, maxWidth, options)

    fun lineMetrics(fontSize: Float): java.awt.font.LineMetrics? =
        TextShaper.defaultLineMetrics(fontSize)

    fun lineHeight(fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float {
        val metrics = TextShaper.defaultFontLineMetrics(fontSize)
        val base = if (options.includeFontPadding) metrics.top + metrics.bottom else metrics.ascent + metrics.descent
        return base * options.lineSpacingMultiplier + options.lineSpacingExtra
    }

    fun wrapLines(text: String, fontSize: Float, maxWidth: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): List<String> =
        wrap(text, fontSize, maxWidth, options).split('\n')

    fun ellipsize(text: String, fontSize: Float, maxWidth: Float, ellipsis: String = "…", options: TextShapingOptions = TextShapingOptions.DEFAULT): String {
        if (text.isEmpty() || maxWidth <= 0f) return ""
        if (measureWidth(text, fontSize, options) <= maxWidth) return text
        val ellipsisWidth = measureWidth(ellipsis, fontSize, options)
        if (ellipsisWidth > maxWidth) return ""
        var low = 0
        var high = text.codePointCount(0, text.length)
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            val end = text.offsetByCodePoints(0, mid)
            if (measureWidth(text.substring(0, end) + ellipsis, fontSize, options) <= maxWidth) low = mid
            else high = mid - 1
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis
    }
}
