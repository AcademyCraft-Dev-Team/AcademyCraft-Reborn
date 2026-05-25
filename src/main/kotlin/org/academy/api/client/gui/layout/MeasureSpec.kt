package org.academy.api.client.gui.layout

class MeasureSpec(val mode: Mode, val size: Float) {
    enum class Mode {
        EXACTLY,
        AT_MOST,
        UNSPECIFIED
    }
}