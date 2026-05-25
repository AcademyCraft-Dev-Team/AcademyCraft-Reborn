package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.util.ClientUtil

/**
 * A universal button widget that acts as a container.
 * Its appearance is controlled by a background Drawable, and its content can be any Widget.
 * This class encapsulates button behavior (clicking, press state) and separates it from presentation.
 */
open class ButtonWidget() : FrameLayoutWidget() {
    var onClickListener: OnClickListener? = null
    protected var isPointerDown: Boolean = false
    override val isPressed: Boolean
        get() = isPointerDown && isHovered

    /**
     * Creates an empty button. Content can be added later with [.addChild].
     */
    init {
        isClickable = true
    }

    /**
     * Creates a button with the given widget as its content.
     * @param content The widget to be displayed inside the button.
     */
    constructor(content: Widget) : this() {
        addChild("content", content)
    }

    override fun canFocus(): Boolean {
        return isAbsoluteEnabled()
    }

    override fun onInterceptEvent(event: InputEvent): Boolean {
        return event.type == EventType.MOUSE_PRESSED
                || event.type == EventType.MOUSE_RELEASED
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == 0 && isMouseOver(event.x, event.y)) {
            isPointerDown = true
            handlePress(event)
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (event.button == 0) isPointerDown = false
    }

    protected fun handlePress(event: MouseEvent) {
        ClientUtil.playDownSound()
        if (onClickListener != null) onClickListener!!.onClick(this)
        event.consume()
    }
}