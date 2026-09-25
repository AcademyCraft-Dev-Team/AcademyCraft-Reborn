package org.academy.api.client.gui.event

class ScrollEvent(
    val x: Double,
    val y: Double,
    val delta: Double,
    val xDelta: Double = 0.0,
    val ctrlDown: Boolean = false,
) : InputEvent(EventType.MOUSE_SCROLLED)
