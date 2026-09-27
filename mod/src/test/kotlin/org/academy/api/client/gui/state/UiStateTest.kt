package org.academy.api.client.gui.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiStateTest {
    @Test
    fun `observe fires immediately with current value`() {
        val state = UiState("a")
        var observed = ""
        state.observe({ observed = it })
        assertEquals("a", observed)
    }

    @Test
    fun `changing value notifies subscribers`() {
        val state = UiState(0)
        var sum = 0
        state.observe({ sum += it }, fireImmediately = false)
        state.value = 1
        state.value = 2
        assertEquals(3, sum)
    }

    @Test
    fun `equal values do not notify`() {
        val state = UiState(0)
        var notified = 0
        state.observe({ notified++ }, fireImmediately = false)
        state.value = 0
        state.value = 0
        assertEquals(0, notified)
    }

    @Test
    fun `unsubscribe stops notifications`() {
        val state = UiState(0)
        var notified = 0
        val unsubscribe = state.observe({ notified++ }, fireImmediately = false)
        state.value = 1
        unsubscribe()
        state.value = 2
        assertEquals(1, notified)
    }
}
