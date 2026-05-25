package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.util.ClientUtil

abstract class AbstractButtonWidget(protected var onClickListener: OnClickListener?) : AbstractWidget() {
    protected var state: MouseButtonState = MouseButtonState.PRESSED
    protected var isPointerDown: Boolean = false
    override val isPressed: Boolean
        get() = isPointerDown && isHovered

    init {
        isClickable = true
    }

    override fun onMousePressed(event: MouseEvent) {
        if (state == MouseButtonState.PRESSED && event.button == 0 && isMouseOver(event.x, event.y)) {
            isPointerDown = true
            handlePress(event)
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (event.button == 0) {
            isPointerDown = false
        }

        if (state == MouseButtonState.RELEASED && event.button == 0 && isMouseOver(event.x, event.y)) {
            handlePress(event)
        }
    }

    protected fun handlePress(event: MouseEvent) {
        ClientUtil.playDownSound()
        if (onClickListener != null) {
            onClickListener!!.onClick(this)
        }
        event.consume()
    }

    override fun canFocus(): Boolean {
        return isAbsoluteEnabled()
    }

    enum class MouseButtonState {
        PRESSED,
        RELEASED
    }
}