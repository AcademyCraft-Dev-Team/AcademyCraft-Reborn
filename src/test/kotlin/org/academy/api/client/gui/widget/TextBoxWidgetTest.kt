package org.academy.api.client.gui.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextBoxWidgetTest {
    @Test
    fun textChangeCallbackTracksCommittedUnicodeTextWithoutDuplicates() {
        val values = mutableListOf<String>()
        val input = TextBoxWidget(16).setOnTextChanged(values::add)

        input.text = "vector"
        input.text = "vector"
        input.text = "矢量偏移"

        assertEquals(listOf("vector", "矢量偏移"), values)
    }
}
