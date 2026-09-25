package org.academy.api.client.gui.widget

import com.mojang.blaze3d.platform.InputConstants
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.util.ClientUtil

open class ButtonWidget() : FrameLayoutWidget() {
    var onClickListener: OnClickListener? = null
    protected var isPointerDown: Boolean = false
    override val isPressed: Boolean
        get() = isPointerDown && isHovered

    init {
        isClickable = true
    }

    constructor(content: Widget) : this() {
        addChild("content", content)
    }

    override fun canFocus(): Boolean {
        return isAbsoluteEnabled()
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(event.x, event.y)) {
            isPointerDown = true
            updateStateAnimator()
            invalidate()
            handlePress(event)
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (event.button == InputConstants.MOUSE_BUTTON_LEFT && isPointerDown) {
            isPointerDown = false
            updateStateAnimator()
            invalidate()
        }
    }

    protected fun handlePress(event: MouseEvent) {
        ClientUtil.playDownSound()
        if (onClickListener != null) onClickListener!!.onClick(this)
        event.consume()
    }
}
