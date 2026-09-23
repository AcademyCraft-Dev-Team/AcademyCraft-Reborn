package org.academy.api.client.gui.text.record

import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.text.model.TextShapingOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextPainterTest {
    @Test
    fun `drawing the status preserves previously recorded labels and styles`() {
        val canvas = Canvas()
        val painter = TextPainter()
        painter.draw(canvas, "精密操作", 8.5f, 1f, 1f, 1f,
            TextShapingOptions.DEFAULT, 4f, 5f, 1f, 1f)
        painter.draw(canvas, "Chat trigger", 7.5f, 1f, 0.5f, 0f,
            TextShapingOptions.DEFAULT, 10f, 25f, 1f, 0.8f)
        painter.draw(canvas, "程序校验通过。", 6.75f, 0.6f, 0.6f, 0.6f,
            TextShapingOptions.DEFAULT, 4f, 200f, 1f, 0.5f)

        val records = canvas.commands.map { it.command as TextBlobRecord }
        assertEquals(listOf("精密操作", "Chat trigger", "程序校验通过。"), records.map { it.text })
        assertEquals(listOf(8.5f, 7.5f, 6.75f), records.map { it.fontSize })
        assertEquals(listOf(1f, 0.5f, 0.6f), records.map { it.green })
        assertEquals(listOf(1f, 0.8f, 0.5f), records.map { it.alpha })
    }

    @Test
    fun `later draws do not change records retained by an earlier canvas`() {
        val painter = TextPainter()
        val previousFrame = Canvas()
        val currentFrame = Canvas()
        painter.draw(previousFrame, "before", 8f, 1f, 1f, 1f,
            TextShapingOptions.DEFAULT, 0f, 0f, 1f, 1f,
            revealCodeUnits = 2, fadeLength = 10f, fadeLeftStrength = 1f)
        painter.draw(currentFrame, "before", 8f, 1f, 1f, 1f,
            TextShapingOptions.DEFAULT, 0f, 0f, 1f, 1f)

        val previous = previousFrame.commands.single().command as TextBlobRecord
        val current = currentFrame.commands.single().command as TextBlobRecord
        assertEquals("before", previous.text)
        assertEquals(2, previous.revealCodeUnits)
        assertEquals(10f, previous.fadeLength)
        assertEquals(1f, previous.fadeLeftStrength)
        assertEquals("before", current.text)
        assertEquals(Int.MAX_VALUE, current.revealCodeUnits)
        assertEquals(0f, current.fadeLength)
    }
}
