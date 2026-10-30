package org.academy.internal.client.ability.mentalout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgramUiGraphicsWrapTest {
    private val width: (String) -> Float = { value ->
        value.codePoints().map { if (it > 127) 2 else 1 }.sum().toFloat()
    }

    @Test
    fun `reflows suggested lines that would be truncated by actual measurement`() {
        val text = "默认目标位移为沿视线方向三格，耗时一秒。"
        val lines = ProgramUiGraphics.wrapMeasured(text, 12f, width) { listOf(it) }

        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.size > 1)
        assertTrue(lines.all { width(it) <= 12f })
        assertTrue(lines.none { it.contains('…') })
    }

    @Test
    fun `keeps fitting candidate lines as the fast path`() {
        val candidate = listOf("Release ", "control")
        val lines = ProgramUiGraphics.wrapMeasured("Release control", 8f, width) { candidate }

        assertEquals(candidate, lines)
    }

    @Test
    fun `preserves words whitespace explicit paragraphs and trailing empty lines`() {
        val text = "Move the whole structure  \n\nRelease control\n"
        val lines = ProgramUiGraphics.wrapMeasured(text, 16f, width)

        assertEquals(listOf("Move the whole ", "structure  ", "", "Release control", ""), lines)
        assertEquals(text.replace("\n", ""), lines.joinToString(""))
        assertTrue(lines.all { width(it) <= 16f })
    }

    @Test
    fun `rejects lossy candidate wrapping and never splits supplementary code points`() {
        val text = "A🚀B结构"
        val lines = ProgramUiGraphics.wrapMeasured(text, 3f, width) { listOf("A") }

        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.all { width(it) <= 3f })
        assertTrue(lines.none { it.isNotEmpty() && Character.isHighSurrogate(it.last()) })
        assertTrue(lines.none { it.isNotEmpty() && Character.isLowSurrogate(it.first()) })
    }

    @Test
    fun `impossibly narrow widths retain one complete code point and terminate`() {
        for (maxWidth in listOf(0f, -1f, 0.25f, Float.NaN)) {
            assertEquals(listOf("🚀", "结", "构"),
                ProgramUiGraphics.wrapMeasured("🚀结构", maxWidth, width))
        }
    }
}
