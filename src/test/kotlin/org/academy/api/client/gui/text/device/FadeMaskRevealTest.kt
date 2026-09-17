package org.academy.api.client.gui.text.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FadeMaskRevealTest {
    private val textWidth = 200f
    private val fadeLength = 12f

    private fun viewportWidth() = textWidth + fadeLength * 2f

    private fun factorAt(u: Float, viewportLeft: Float): Float =
        FadeMask.fadeFactor(u, viewportLeft, viewportWidth(), fadeLength, 0f, 1f)

    @Test
    fun `wipe is hidden at the start`() {
        val start = -viewportWidth()
        assertEquals(0f, factorAt(0f, start), 1e-4f)
        assertEquals(0f, factorAt(textWidth, start), 1e-4f)
    }

    @Test
    fun `wipe reveals every glyph at the end`() {
        assertEquals(1f, factorAt(0f, 0f), 1e-4f)
        assertEquals(1f, factorAt(textWidth, 0f), 1e-4f)
    }

    @Test
    fun `wipe sweeps left to right`() {
        val start = -viewportWidth()
        val cursor = start / 2f
        assertTrue(factorAt(0f, cursor) > 0f, "left glyph should lead")
        assertTrue(
            factorAt(0f, cursor) >= factorAt(textWidth, cursor),
            "right glyph must not reveal before the left one"
        )
    }
}
