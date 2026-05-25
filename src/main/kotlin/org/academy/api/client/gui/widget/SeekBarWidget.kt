package org.academy.api.client.gui.widget

import net.minecraft.util.Mth
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Orientation
import org.lwjgl.glfw.GLFW

open class SeekBarWidget : ProgressBarWidget() {
    var keyProgressIncrement: Int = 1
        protected set
    protected var isDragging: Boolean = false

    var onSeekBarChangeListener: OnSeekBarChangeListener? = null
        private set

    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean)

        fun onStartTrackingTouch(seekBar: SeekBarWidget)

        fun onStopTrackingTouch(seekBar: SeekBarWidget)
    }

    init {
        isClickable = true
    }

    override fun onMousePressed(event: MouseEvent) {
        if (isMouseOver(event.x, event.y)) {
            event.consume()
            isDragging = true
            if (onSeekBarChangeListener != null) {
                onSeekBarChangeListener!!.onStartTrackingTouch(this)
            }
            updateProgressFromCoord(event.x, event.y)
        }
    }

    override fun onMouseDragged(event: MouseEvent) {
        if (isDragging) {
            event.consume()
            updateProgressFromCoord(event.x, event.y)
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (isDragging) {
            event.consume()
            isDragging = false
            if (onSeekBarChangeListener != null) {
                onSeekBarChangeListener!!.onStopTrackingTouch(this)
            }
        }
    }

    override fun onKeyReleased(event: KeyEvent) {
        val oldProgress = progress
        var handled = false
        val keyCode = event.keyCode

        if (orientation == Orientation.HORIZONTAL) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                setProgress(progress - keyProgressIncrement)
                handled = true
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                setProgress(progress + keyProgressIncrement)
                handled = true
            }
        } else {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                setProgress(progress - keyProgressIncrement)
                handled = true
            } else if (keyCode == GLFW.GLFW_KEY_UP) {
                setProgress(progress + keyProgressIncrement)
                handled = true
            }
        }

        if (handled && oldProgress != progress && onSeekBarChangeListener != null) {
            onSeekBarChangeListener!!.onProgressChanged(this, progress, true)
        }
    }

    private fun updateProgressFromCoord(mouseX: Double, mouseY: Double) {
        val ratio: Double
        if (orientation == Orientation.HORIZONTAL) {
            val relativeX = mouseX - getAbsoluteX()
            val trackWidth = width
            val progressX = Mth.clamp(relativeX, 0.0, trackWidth.toDouble())
            ratio = if (trackWidth > 0) progressX / trackWidth else 0.0
        } else {
            val relativeY = mouseY - getAbsoluteY()
            val trackHeight = height
            val progressY = Mth.clamp(relativeY, 0.0, trackHeight.toDouble())
            ratio = if (trackHeight > 0) 1.0 - (progressY / trackHeight) else 0.0
        }

        val newProgress = min + ratio * (max - min)

        val oldProgress = progress
        super.setProgress(newProgress.toFloat())

        if (oldProgress != progress && onSeekBarChangeListener != null) {
            onSeekBarChangeListener!!.onProgressChanged(this, progress, true)
        }
    }

    override fun setProgress(progress: Float): ProgressBarWidget {
        val oldProgress = this.progress
        super.setProgress(progress)
        if (oldProgress != this.progress && onSeekBarChangeListener != null) {
            onSeekBarChangeListener!!.onProgressChanged(this, this.progress, false)
        }
        return this
    }

    fun setKeyProgressIncrement(keyProgressIncrement: Int): SeekBarWidget {
        this.keyProgressIncrement = keyProgressIncrement
        return this
    }

    fun setOnSeekBarChangeListener(onSeekBarChangeListener: OnSeekBarChangeListener?): SeekBarWidget {
        this.onSeekBarChangeListener = onSeekBarChangeListener
        return this
    }
}