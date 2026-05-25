package org.academy.api.client.gui.layout

object Gravity {
    const val NO_GRAVITY: Int = 0

    const val AXIS_SPECIFIED: Int = 0x0001
    const val AXIS_PULL_BEFORE: Int = 0x0002
    const val AXIS_PULL_AFTER: Int = 0x0004
    const val AXIS_X_SHIFT: Int = 0
    const val AXIS_Y_SHIFT: Int = 4

    const val TOP: Int = (AXIS_PULL_BEFORE or AXIS_SPECIFIED) shl AXIS_Y_SHIFT
    const val BOTTOM: Int = (AXIS_PULL_AFTER or AXIS_SPECIFIED) shl AXIS_Y_SHIFT
    const val LEFT: Int = (AXIS_PULL_BEFORE or AXIS_SPECIFIED) shl AXIS_X_SHIFT
    const val RIGHT: Int = (AXIS_PULL_AFTER or AXIS_SPECIFIED) shl AXIS_X_SHIFT

    const val CENTER_VERTICAL: Int = AXIS_SPECIFIED shl AXIS_Y_SHIFT
    const val CENTER_HORIZONTAL: Int = AXIS_SPECIFIED shl AXIS_X_SHIFT
    const val CENTER: Int = CENTER_VERTICAL or CENTER_HORIZONTAL

    const val TOP_LEFT: Int = TOP or LEFT

    const val HORIZONTAL_GRAVITY_MASK: Int = (AXIS_SPECIFIED or AXIS_PULL_BEFORE or AXIS_PULL_AFTER) shl AXIS_X_SHIFT
    const val VERTICAL_GRAVITY_MASK: Int = (AXIS_SPECIFIED or AXIS_PULL_BEFORE or AXIS_PULL_AFTER) shl AXIS_Y_SHIFT

    const val FILL_HORIZONTAL: Int = HORIZONTAL_GRAVITY_MASK
    const val FILL_VERTICAL: Int = VERTICAL_GRAVITY_MASK
    const val FILL: Int = FILL_HORIZONTAL or FILL_VERTICAL

    const val RELATIVE_LAYOUT_DIRECTION: Int = 0x00800000

    const val START: Int = RELATIVE_LAYOUT_DIRECTION or LEFT
    const val END: Int = RELATIVE_LAYOUT_DIRECTION or RIGHT

    const val RELATIVE_HORIZONTAL_GRAVITY_MASK: Int = START or END

    const val TOP_RIGHT: Int = TOP or RIGHT
    const val BOTTOM_LEFT: Int = BOTTOM or LEFT
    const val BOTTOM_RIGHT: Int = BOTTOM or RIGHT

    const val CENTER_TOP: Int = CENTER_HORIZONTAL or TOP
    const val CENTER_BOTTOM: Int = CENTER_HORIZONTAL or BOTTOM
    const val CENTER_LEFT: Int = CENTER_VERTICAL or LEFT
    const val CENTER_RIGHT: Int = CENTER_VERTICAL or RIGHT

    fun  apply(){}
}