package org.academy.api.client.gui.widget

import org.academy.api.client.gui.frame.UiFrame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class FrameUpdateTest {
    @AfterEach
    fun tearDown() {
        UiFrame.clear()
    }

    @Test
    fun `setFrameUpdate callback runs once attached`() {
        val root = FrameLayoutWidget()
        val counter = AtomicInteger(0)
        root.setFrameUpdate { counter.incrementAndGet(); true }

        root.dispatchAttached()
        UiFrame.onFrame()
        assertEquals(1, counter.get())

        UiFrame.onFrame()
        assertEquals(2, counter.get())
    }

    @Test
    fun `frame update registered before attach is still registered on attach`() {
        val root = FrameLayoutWidget()
        val counter = AtomicInteger(0)
        root.setFrameUpdate { counter.incrementAndGet(); true }

        UiFrame.onFrame()
        assertEquals(0, counter.get(), "unattached widget must not run frame callback")

        root.dispatchAttached()
        UiFrame.onFrame()
        assertEquals(1, counter.get())
    }

    @Test
    fun `frame update stops when callback returns false`() {
        val root = FrameLayoutWidget()
        val counter = AtomicInteger(0)
        root.setFrameUpdate {
            counter.incrementAndGet()
            counter.get() < 2
        }

        root.dispatchAttached()
        UiFrame.onFrame()
        UiFrame.onFrame()
        UiFrame.onFrame()
        assertEquals(2, counter.get())
    }

    @Test
    fun `frame update cancels on detach`() {
        val root = FrameLayoutWidget()
        val counter = AtomicInteger(0)
        root.setFrameUpdate { counter.incrementAndGet(); true }

        root.dispatchAttached()
        UiFrame.onFrame()
        assertEquals(1, counter.get())

        root.dispatchDetached()
        UiFrame.onFrame()
        assertEquals(1, counter.get(), "detached widget must not run frame callback")
    }

    @Test
    fun `runFrameUpdate executes the callback without attach`() {
        val root = FrameLayoutWidget()
        val counter = AtomicInteger(0)
        root.setFrameUpdate { counter.incrementAndGet(); true }
        root.runFrameUpdate()
        assertEquals(1, counter.get())
    }
}
