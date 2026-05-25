package org.academy.api.client.gui.widget

import net.minecraft.util.Mth
import org.academy.api.client.gui.layout.Orientation
import java.util.function.Consumer

/**
 * The abstract base class for slider widgets. It handles all the logic for converting
 * between a value and a position on the track, including orientation and direction.
 * It does not perform any rendering itself.
 */
abstract class AbstractSliderWidget(
    x: Float, y: Float, width: Float, height: Float, orientation: Orientation,
    protected var minValue: Float, protected var maxValue: Float, initialValue: Float
) : DragBarWidget(orientation) {
    enum class Direction {
        /** For vertical sliders, represents a value increase from bottom to top.  */
        BOTTOM_TO_TOP,

        /** For vertical sliders, represents a value increase from top to bottom.  */
        TOP_TO_BOTTOM
    }

    protected var currentValue: Float
    protected var onValueChanged: Consumer<Float>? = null
    protected var direction: Direction = Direction.BOTTOM_TO_TOP
    override val thumbSize: Float = 8f
    override val thumbPosition: Float
        get() {
            val range = maxValue - minValue
            if (range <= 0) {
                return 0f
            }
            var valueFraction = (currentValue - minValue) / range

            if (orientation == Orientation.VERTICAL && direction == Direction.BOTTOM_TO_TOP) {
                valueFraction = 1.0f - valueFraction
            }

            val trackSize = trackSize - thumbSize
            return valueFraction * trackSize
        }

    init {
        currentValue = Mth.clamp(initialValue, this.minValue, this.maxValue)
    }

    var value: Float
        get() = currentValue
        set(value) {
            val newValue = Mth.clamp(value, minValue, maxValue)
            if (currentValue != newValue) {
                currentValue = newValue
                if (onValueChanged != null) {
                    onValueChanged!!.accept(currentValue)
                }
            }
        }

    override fun updateTargetFromMouse(mouse: Float) {
        val trackSize = trackSize - thumbSize
        if (trackSize <= 0) {
            return
        }

        val position = mouse - dragOffset
        var positionFraction = Mth.clamp(position / trackSize, 0f, 1f)

        if (orientation == Orientation.VERTICAL && direction == Direction.BOTTOM_TO_TOP) {
            positionFraction = 1.0f - positionFraction
        }

        val newValue = minValue + positionFraction * (maxValue - minValue)
        this.value = newValue
    }

    fun setDirection(direction: Direction): AbstractSliderWidget {
        this.direction = direction
        return this
    }

    fun setOnValueChanged(onValueChanged: Consumer<Float>): AbstractSliderWidget {
        this.onValueChanged = onValueChanged
        return this
    }

}