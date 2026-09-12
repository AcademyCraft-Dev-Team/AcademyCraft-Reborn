package org.academy.api.client.gui.text

/**
 * 文本度量与断行的唯一入口。
 *
 * 全部委托给 AWT 单一布局源 [TextShaper]（`LineBreakMeasurer` + `GlyphVector`），
 * 不再手写贪心/前缀宽度。省略号仍用二分度量（度量本身来自 AWT）。
 */
object TextLayoutManager {
    /** 文本块宽度（AWT user units）。 */
    fun measureWidth(text: CharSequence, fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float =
        TextShaper.shape(text.toString(), fontSize, options).width

    /** 文本块高度（AWT user units，含行距）。 */
    fun measureHeight(text: CharSequence, fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float =
        TextShaper.shape(text.toString(), fontSize, options).height

    /** AWT 换行，返回插入 `\n` 后的字符串。 */
    fun wrap(text: String, fontSize: Float, maxWidth: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): String =
        TextShaper.wrap(text, fontSize, maxWidth, options)

    /** 默认字体的行度量（AWT user units）；空文本用于占位行高。 */
    fun lineMetrics(fontSize: Float): java.awt.font.LineMetrics? =
        TextShaper.defaultLineMetrics(fontSize)

    /** AWT 换行后的逐行结果。 */
    fun wrapLines(text: String, fontSize: Float, maxWidth: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): List<String> =
        wrap(text, fontSize, maxWidth, options).split('\n')

    /** 省略号截断（末端省略）。 */
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
