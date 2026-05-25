package org.academy.api.client.gui.event

class ScrollEvent(val x: Double, val y: Double, val delta: Double) : InputEvent(EventType.MOUSE_SCROLLED) 