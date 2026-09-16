package org.academy.api.client.gui.text.shape

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.text.model.GlyphRun
import org.academy.api.client.gui.text.model.TextBlob
import org.academy.api.client.gui.text.model.TextLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaretGeometryTest {
    private fun blob(): TextBlob {
        val font = Identifier.parse("academy:test")
        val run = GlyphRun(
            font,
            glyphIndices = intArrayOf(1, 2, 3),
            positionsX = floatArrayOf(0f, 5f, 10f),
            advances = floatArrayOf(5f, 5f, 5f),
            charIndices = intArrayOf(0, 1, 2)
        )
        val line = TextLine(charStart = 0, charEnd = 3, baselineY = 8f, ascent = 6f, descent = 2f, leading = 0f)
        return TextBlob("abc", listOf(run), listOf(line), width = 15f, height = 8f)
    }

    @Test
    fun `caret x maps unit to glyph origin`() {
        val blob = blob()
        val line = blob.lines.single()
        assertEquals(0f, CaretGeometry.caretX(blob, line, 0), 1e-4f)
        assertEquals(5f, CaretGeometry.caretX(blob, line, 1), 1e-4f)
        assertEquals(10f, CaretGeometry.caretX(blob, line, 2), 1e-4f)
    }

    @Test
    fun `caret x at end falls back to previous glyph advance`() {
        val blob = blob()
        assertEquals(15f, CaretGeometry.caretX(blob, blob.lines.single(), 3), 1e-4f)
    }

    @Test
    fun `hit test snaps to nearest gap midpoint`() {
        val blob = blob()
        val line = blob.lines.single()
        assertEquals(0, CaretGeometry.hitTest(blob, line, 1f))
        assertEquals(1, CaretGeometry.hitTest(blob, line, 4f))
        assertEquals(2, CaretGeometry.hitTest(blob, line, 9f))
    }

    @Test
    fun `hit test past the end clamps to line end`() {
        val blob = blob()
        assertEquals(3, CaretGeometry.hitTest(blob, blob.lines.single(), 16f))
    }

    @Test
    fun `line for returns last line past the end`() {
        val blob = blob()
        assertEquals(blob.lines.last(), CaretGeometry.lineFor(blob, 99))
        assertEquals(blob.lines.first(), CaretGeometry.lineFor(blob, 0))
    }
}
