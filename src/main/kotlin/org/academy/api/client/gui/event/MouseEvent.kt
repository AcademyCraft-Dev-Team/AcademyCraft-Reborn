package org.academy.api.client.gui.event

class MouseEvent private constructor(
    type: EventType,
    val x: Double,
    val y: Double,
    val button: Int,
    val dragX: Double,
    val dragY: Double
) : InputEvent(type) {
    companion object {
        fun createPressEvent(x: Double, y: Double, button: Int): MouseEvent {
            return MouseEvent(EventType.MOUSE_PRESSED, x, y, button, 0.0, 0.0)
        }

        fun createReleaseEvent(x: Double, y: Double, button: Int): MouseEvent {
            return MouseEvent(EventType.MOUSE_RELEASED, x, y, button, 0.0, 0.0)
        }

        fun createMoveEvent(x: Double, y: Double): MouseEvent {
            return MouseEvent(EventType.MOUSE_MOVED, x, y, -1, 0.0, 0.0)
        }

        fun createDragEvent(x: Double, y: Double, button: Int, dragX: Double, dragY: Double): MouseEvent {
            return MouseEvent(EventType.MOUSE_DRAGGED, x, y, button, dragX, dragY)
        }
    }
}