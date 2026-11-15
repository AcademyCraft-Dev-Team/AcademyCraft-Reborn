package org.academy.api.client.gui.event

class MouseEvent private constructor(
    type: EventType,
    val x: Double,
    val y: Double,
    val button: Int = -1,
    val dragX: Double = 0.0,
    val dragY: Double = 0.0,
    val delta: Double = 0.0,
) : InputEvent(type) {
    companion object {
        fun createPressEvent(x: Double, y: Double, button: Int): MouseEvent {
            return MouseEvent(EventType.MOUSE_PRESSED, x, y, button = button)
        }

        fun createReleaseEvent(x: Double, y: Double, button: Int): MouseEvent {
            return MouseEvent(EventType.MOUSE_RELEASED, x, y, button = button)
        }

        fun createMoveEvent(x: Double, y: Double): MouseEvent {
            return MouseEvent(EventType.MOUSE_MOVED, x, y)
        }

        fun createDragEvent(x: Double, y: Double, button: Int, dragX: Double, dragY: Double): MouseEvent {
            return MouseEvent(EventType.MOUSE_DRAGGED, x, y, button = button, dragX = dragX, dragY = dragY)
        }

        fun createScrollEvent(x: Double, y: Double, delta: Double): MouseEvent {
            return MouseEvent(EventType.MOUSE_SCROLLED, x, y, delta = delta)
        }
    }
}
