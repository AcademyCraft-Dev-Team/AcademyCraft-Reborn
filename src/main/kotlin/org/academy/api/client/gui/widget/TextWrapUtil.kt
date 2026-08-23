package org.academy.api.client.gui.widget

internal object TextWrapUtil {
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (text.isEmpty() || maxWidth <= 0f || !maxWidth.isFinite()) return text

        val lines = mutableListOf<String>()
        var paragraphStart = 0
        for (index in 0..text.length) {
            if (index != text.length && text[index] != '\n') continue
            wrapParagraph(text.substring(paragraphStart, index), maxWidth, measure, lines)
            paragraphStart = index + 1
        }
        return lines.joinToString("\n")
    }

    private fun wrapParagraph(
        paragraph: String,
        maxWidth: Float,
        measure: (String) -> Float,
        output: MutableList<String>
    ) {
        if (paragraph.isEmpty()) {
            output.add("")
            return
        }

        var remaining = paragraph
        while (remaining.isNotEmpty()) {
            if (measure(remaining) <= maxWidth) {
                output.add(remaining.trimEnd())
                return
            }

            var offset = 0
            var lastFittingOffset = 0
            var lastPreferredBreak = 0
            var overflowAtWhitespace = false
            while (offset < remaining.length) {
                val codePoint = remaining.codePointAt(offset)
                val nextOffset = offset + Character.charCount(codePoint)
                if (measure(remaining.substring(0, nextOffset)) > maxWidth) {
                    overflowAtWhitespace = Character.isWhitespace(codePoint)
                    break
                }

                lastFittingOffset = nextOffset
                if (isPreferredBreak(codePoint)) lastPreferredBreak = nextOffset
                offset = nextOffset
            }

            val breakOffset = when {
                overflowAtWhitespace && lastFittingOffset > 0 -> lastFittingOffset
                lastPreferredBreak > 0 -> lastPreferredBreak
                lastFittingOffset > 0 -> lastFittingOffset
                else -> Character.charCount(remaining.codePointAt(0))
            }
            output.add(remaining.substring(0, breakOffset).trimEnd())
            remaining = remaining.substring(breakOffset).trimStart()
        }
    }

    private fun isPreferredBreak(codePoint: Int): Boolean {
        if (Character.isWhitespace(codePoint)) return true
        if (codePoint in 0x2E80..0x9FFF || codePoint in 0xF900..0xFAFF) return true
        return codePoint.toChar() in "-/,.;:!?，。；：！？、）】》"
    }
}
