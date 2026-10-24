package org.academy.api.client.gui.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextInputWidgetTest {
    @Test
    fun disabledAndHiddenQuotaInputsCannotTakeFocus() {
        val parent = FrameLayoutWidget()
        val input = TextInputWidget(3)
        parent.addChild("quota", input)
        assertTrue(input.canFocus())
        parent.isEnabled = false
        assertFalse(input.canFocus())
        parent.isEnabled = true
        input.visibility = Widget.Visibility.GONE
        assertFalse(input.canFocus())
        input.visibility = Widget.Visibility.VISIBLE
        assertTrue(input.canFocus())
    }

    @Test
    fun textChangeCallbackTracksCommittedUnicodeTextWithoutDuplicates() {
        val values = mutableListOf<String>()
        val input = TextInputWidget(16).setOnTextChanged(values::add)

        input.text = "vector"
        input.text = "vector"
        input.text = "矢量偏移"

        assertEquals(listOf("vector", "矢量偏移"), values)
    }
}
