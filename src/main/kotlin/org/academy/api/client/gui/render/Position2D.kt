package org.academy.api.client.gui.render

import org.academy.api.common.util.MathUtil.Axis2D
import org.academy.api.common.util.MathUtil.Direction2D
import org.joml.Vector2f

class Position2D : Vector2f {
    constructor(x: Float, y: Float) : super(x, y)

    constructor()

    fun step(direction: Direction2D): Position2D {
        return when (direction) {
            Direction2D.DOWN -> Position2D(x, y + 1)
            Direction2D.UP -> Position2D(x, y - 1)
            Direction2D.LEFT -> Position2D(x - 1, y)
            Direction2D.RIGHT -> Position2D(x + 1, y)
        }
    }

    fun getCoordinate(axis: Axis2D): Float {
        return when (axis) {
            Axis2D.HORIZONTAL -> x
            Axis2D.VERTICAL -> y
        }
    }

    companion object {
        fun of(axis: Axis2D, primaryPosition: Float, secondaryPosition: Float): Position2D {
            return when (axis) {
                Axis2D.HORIZONTAL -> Position2D(primaryPosition, secondaryPosition)
                Axis2D.VERTICAL -> Position2D(secondaryPosition, primaryPosition)
            }
        }
    }
}