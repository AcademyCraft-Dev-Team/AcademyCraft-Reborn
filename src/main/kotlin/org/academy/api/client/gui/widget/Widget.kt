package org.academy.api.client.gui.widget

import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.StateListAnimator
import org.academy.api.client.gui.drawable.Drawable
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.common.vanilla.Tickable

interface Widget : Tickable {
    val x: Float
    val y: Float
    var z: Float
    var width: Float
    var height: Float
    var translationX: Float
    var translationY: Float
    var visibility: Visibility
    var name: String
    var isEnabled: Boolean
    var isFocused: Boolean
    var isSelected: Boolean
    var isHovered: Boolean
    var isClickable: Boolean
    var stateListAnimator: StateListAnimator?
    var alpha: Float
    var scaleX: Float
    var scaleY: Float
    var scale: Float
    var rotation: Float
    var originX: Float
    var originY: Float
    var origin: Float
    var background: Drawable?
    var foreground: Drawable?
    val measuredWidth: Float
    val measuredHeight: Float
    var layoutParams: WidgetContainer.LayoutParams
    val scrollX: Float
    val scrollY: Float
    val isPressed: Boolean
    var parent: WidgetContainer?

    fun render(context: RenderContext)
    fun dispatchEvent(event: InputEvent)
    fun measure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec)
    fun layout(left: Float, top: Float, right: Float, bottom: Float)
    fun requestLayout()
    fun isVisible(): Boolean
    fun getWidgetState(): Int
    fun isAbsoluteEnabled(): Boolean
    fun isMouseOver(mouseX: Double, mouseY: Double): Boolean
    fun canFocus(): Boolean
    fun onFocusGained()
    fun onFocusLost()
    fun scrollTo(x: Float, y: Float)
    fun scrollBy(dx: Float, dy: Float)
    fun getAbsoluteX(): Float
    fun getAbsoluteY(): Float
    fun getAbsoluteTranslationX(): Float
    fun getAbsoluteTranslationY(): Float
    fun getAbsoluteAlpha(): Float
    override fun tick() {}
    fun onAttached()
    fun onDetached()
    fun dispatchAttached()
    fun dispatchDetached()
    fun isAttached(): Boolean
    fun startAnimation(animator: Animator)
    fun cancelAnimations()

    enum class Visibility {
        VISIBLE, INVISIBLE, GONE
    }

    companion object State {
        const val NONE = 0
        const val DISABLED = 1
        const val HOVERED = 1 shl 1
        const val PRESSED = 1 shl 2
        const val FOCUSED = 1 shl 3
        const val CHECKED = 1 shl 4
        const val SELECTED = 1 shl 5
    }
}