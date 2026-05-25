package org.academy.api.client.gui.event

import org.academy.api.client.gui.widget.Widget

abstract class InputEvent protected constructor(val type: EventType) {
    var isConsumed: Boolean = false
        private set
    var target: Widget? = null

    fun consume() {
        isConsumed = true
    }
}