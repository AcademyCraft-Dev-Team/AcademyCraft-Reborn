package org.academy.api.client.gui.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextWrapUtilTest {
    private val monospaceMeasure: (String) -> Float = { value -> value.length.toFloat() }

    @Test
    fun `wraps Chinese text without shrinking or spaces`() {
        assertEquals(
            "定位虚相\n位湖泊",
            TextWrapUtil.wrap("定位虚相位湖泊", 4f, monospaceMeasure)
        )
    }

    @Test
    fun `prefers word boundaries for Latin text`() {
        assertEquals(
            "Alpha beta\ngamma",
            TextWrapUtil.wrap("Alpha beta gamma", 10f, monospaceMeasure)
        )
    }

    @Test
    fun `breaks a word only when it cannot fit on one line`() {
        assertEquals(
            "abcd\nef",
            TextWrapUtil.wrap("abcdef", 4f, monospaceMeasure)
        )
    }

    @Test
    fun `preserves explicit line breaks`() {
        assertEquals(
            "first\nsecond",
            TextWrapUtil.wrap("first\nsecond", 20f, monospaceMeasure)
        )
    }
}
