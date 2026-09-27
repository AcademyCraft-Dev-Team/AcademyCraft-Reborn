package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ButtonWidgetHitTest {
    @Test
    fun staleHoverStateDoesNotAcceptPressOutsideCurrentBounds() {
        val button = ButtonWidget()
        button.layout(10f, 10f, 26f, 26f)
        button.isHovered = true

        val event = MouseEvent.createPressEvent(100.0, 100.0, 0)
        button.dispatchEvent(event)

        assertFalse(event.isConsumed)
        assertFalse(button.isPressed)
    }
}
