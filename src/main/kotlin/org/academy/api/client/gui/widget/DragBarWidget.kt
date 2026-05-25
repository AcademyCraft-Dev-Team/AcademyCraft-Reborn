package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Orientation

abstract class DragBarWidget(protected val orientation: Orientation) : AbstractWidget() {
    var isShowBackground: Boolean = true
        protected set
    protected var dragOffset: Float = 0f
    var thumbColor: Int = -0x555556
        protected set
    var trackColor: Int = -0xdfdfe0
        protected set
    private var isDragging = false

    init {
        isClickable = true
    }

    protected abstract val thumbSize: Float

    protected abstract val thumbPosition: Float

    protected abstract fun updateTargetFromMouse(mouse: Float)

    protected val trackSize: Float
        get() = if (orientation == Orientation.HORIZONTAL) width else height

    protected fun getMouseRelative(mouseX: Float, mouseY: Float): Float {
        return if (orientation == Orientation.HORIZONTAL) (mouseX - getAbsoluteX()) else (mouseY - getAbsoluteY())
    }

    override fun onMousePressed(event: MouseEvent) {
        if (isMouseOver(event.x, event.y) && event.button == 0) {
            isDragging = true
            dragOffset = this.thumbSize / 2f
            updateTargetFromMouse(getMouseRelative(event.x.toFloat(), event.y.toFloat()))
            event.consume()
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        isDragging = false
        super.onMouseReleased(event)
    }

    override fun onMouseDragged(event: MouseEvent) {
        if (isDragging && event.button == 0) {
            updateTargetFromMouse(getMouseRelative(event.x.toFloat(), event.y.toFloat()))
            event.consume()
        }
    }

    fun setThumbColor(color: Int): DragBarWidget {
        thumbColor = color
        return this
    }

    fun setTrackColor(color: Int): DragBarWidget {
        trackColor = color
        return this
    }

    fun setShowBackground(show: Boolean): DragBarWidget {
        this.isShowBackground = show
        return this
    }

    override fun canFocus(): Boolean {
        return true
    }
}