package org.academy.api.client.gui.widget

import com.mojang.math.Axis
import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.StateListAnimator
import org.academy.api.client.gui.drawable.Drawable
import org.academy.api.client.gui.event.*
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import kotlin.math.min

abstract class AbstractWidget : Widget {
    override var layoutParams: WidgetContainer.LayoutParams = WidgetContainer.LayoutParams.NONE
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    final override var measuredWidth: Float = 0f
        private set
    final override var measuredHeight: Float = 0f
        private set

    final override var x: Float = 0f
        private set
    final override var y: Float = 0f
        private set

    override var coverAllPrev: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    protected var protectedWidth: Float = 0f

    override var width: Float
        get() = protectedWidth
        set(width) {
            if (layoutParams.width != width || layoutParams.widthMode != SizeMode.FIXED) {
                layoutParams.width = width
                layoutParams.widthMode = SizeMode.FIXED
                requestLayout()
            }
        }

    protected var protectedHeight: Float = 0f

    override var height: Float
        get() = protectedHeight
        set(value) {
            if (layoutParams.height != value || layoutParams.heightMode != SizeMode.FIXED) {
                layoutParams.height = value
                layoutParams.heightMode = SizeMode.FIXED
                requestLayout()
            }
        }

    override var translationX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    override var translationY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    override var scaleX: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }
    override var scaleY: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }
    override var rotation: Float = 0.0f
        set(value) {
            field = value
            invalidate()
        }
    override var originX: Float = 0.5f
    override var originY: Float = 0.5f
    override var origin: Float
        get() = originX
        set(value) {
            originX = value
            originY = value
        }

    override var visibility: Widget.Visibility = Widget.Visibility.VISIBLE
        set(value) {
            if (field != value) {
                field = value
                parent?.requestLayout()
                invalidate()
            }
        }

    override var isEnabled: Boolean = true
        set(enabled) {
            field = enabled
            updateStateAnimator()
            invalidate()
        }

    override var isFocused: Boolean = false
        set(focused) {
            if (field != focused && canFocus()) {
                field = focused
                updateStateAnimator()
                invalidate()
                if (focused) onFocusGained() else onFocusLost()
            }
        }
    override var isSelected: Boolean = false
        set(selected) {
            field = selected
            updateStateAnimator()
            invalidate()
        }
    override var isHovered: Boolean = false
        set(hovered) {
            field = hovered
            updateStateAnimator()
            invalidate()
        }
    override var isClickable: Boolean = false
    override var alpha: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }
    override var name: String = ""

    override var stateListAnimator: StateListAnimator? = null
        set(animator) {
            field = animator
            if (isAttached && animator != null) {
                animator.jumpToCurrentState()
                updateStateAnimator()
            }
        }

    override var scale: Float
        get() = scaleX
        set(value) {
            scaleX = value
            scaleY = value
            invalidate()
        }

    override var background: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }
    override var foreground: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }
    override var tooltipText: CharSequence? = null
        set(value) {
            field = value
            invalidate()
        }

    final override var scrollX: Float = 0f
        private set
    final override var scrollY: Float = 0f
        private set

    protected var protectedIsPressed = false
    override val isPressed: Boolean get() = protectedIsPressed
    override var parent: WidgetContainer? = null

    private var isAttached = false
    private val attachedAnimators: MutableList<Animator> = ArrayList()
    private val detachListeners: MutableList<() -> Unit> = ArrayList()
    private val postLayoutActions: MutableList<() -> Unit> = ArrayList()

    override var onLayoutComplete: ((Widget.WidgetLayoutInfo) -> Unit)? = null

    override fun postLayout(action: () -> Unit) {
        postLayoutActions.add(action)
    }

    override var isRenderDirty: Boolean = true

    override var bypassRenderCache: Boolean = false

    override fun invalidate() {
        isRenderDirty = true
        parent?.onChildInvalidated(this)
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return

        val pivotX = width * originX
        val pivotY = height * originY

        val hasTransform = scaleX != 1.0f || scaleY != 1.0f || rotation != 0.0f

        context.pose().pushPose()
        if (hasTransform) {
            context.pose().translate(pivotX, pivotY)
            if (rotation != 0.0f) {
                context.pose().mulPose(Axis.ZP.rotationDegrees(rotation))
            }
            if (scaleX != 1.0f || scaleY != 1.0f) {
                context.pose().scale(scaleX, scaleY)
            }
            context.pose().translate(-pivotX, -pivotY)
        }

        renderInternal(context)

        context.pose().popPose()
    }

    protected open fun renderInternal(context: RenderContext) {
        background?.draw(context, this)
        foreground?.draw(context, this)
    }

    override fun dispatchEvent(event: InputEvent) {
        if (!isAbsoluteEnabled() || visibility != Widget.Visibility.VISIBLE) {
            return
        }

        when (event.type) {
            EventType.MOUSE_PRESSED -> onMousePressed(event as MouseEvent)
            EventType.MOUSE_RELEASED -> onMouseReleased(event as MouseEvent)
            EventType.MOUSE_MOVED -> onMouseMoved(event as MouseEvent)
            EventType.MOUSE_SCROLLED -> onMouseScrolled(event as ScrollEvent)
            EventType.MOUSE_DRAGGED -> onMouseDragged(event as MouseEvent)
            EventType.KEY_PRESSED -> onKeyPressed(event as KeyEvent)
            EventType.KEY_RELEASED -> onKeyReleased(event as KeyEvent)
            EventType.CHAR_TYPED -> onCharTyped(event as CharTypedEvent)
        }
    }

    protected open fun onMousePressed(event: MouseEvent) {
        if (isClickable && isMouseOver(event.x, event.y)) {
            protectedIsPressed = true
            updateStateAnimator()
            invalidate()
            event.consume()
        }
    }

    protected open fun onMouseReleased(event: MouseEvent) {
        if (protectedIsPressed) {
            protectedIsPressed = false
            updateStateAnimator()
            invalidate()
        }
        if (isMouseOver(event.x, event.y)) {
            event.consume()
        }
    }

    protected open fun onMouseMoved(event: MouseEvent) {
    }

    protected open fun onMouseScrolled(event: ScrollEvent) {
    }

    protected open fun onMouseDragged(event: MouseEvent) {
    }

    protected open fun onKeyPressed(event: KeyEvent) {
    }

    protected open fun onKeyReleased(event: KeyEvent) {
    }

    protected open fun onCharTyped(event: CharTypedEvent) {
    }

    override fun measure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        if (visibility == Widget.Visibility.GONE) {
            setMeasuredDimension(0f, 0f)
            return
        }
        onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    protected open fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val desiredWidth = layoutParams.paddingLeft + layoutParams.paddingRight
        val desiredHeight = layoutParams.paddingTop + layoutParams.paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    protected fun setMeasuredDimension(measuredWidth: Float, measuredHeight: Float) {
        this.measuredWidth = measuredWidth
        this.measuredHeight = measuredHeight
    }

    override fun layout(left: Float, top: Float, right: Float, bottom: Float) {
        x = left
        y = top
        protectedWidth = right - left
        protectedHeight = bottom - top
        onLayoutComplete?.invoke(Widget.WidgetLayoutInfo(x, y, protectedWidth, protectedHeight))
        if (postLayoutActions.isNotEmpty()) {
            val actions = ArrayList(postLayoutActions)
            postLayoutActions.clear()
            for (action in actions) action()
        }
    }

    override fun requestLayout() {
        invalidate()
        parent?.requestLayout()
    }

    override fun isVisible(): Boolean {
        return visibility == Widget.Visibility.VISIBLE
    }

    override fun getWidgetState(): Int {
        var state = Widget.NONE
        if (!isEnabled) state = state or Widget.DISABLED
        if (isHovered) state = state or Widget.HOVERED
        if (isPressed) state = state or Widget.PRESSED
        if (isFocused) state = state or Widget.FOCUSED
        if (isSelected) state = state or Widget.SELECTED
        return state
    }

    protected fun updateStateAnimator() {
        stateListAnimator?.setState(getWidgetState())
    }

    override fun scrollTo(x: Float, y: Float) {
        if (scrollX != x || scrollY != y) {
            scrollX = x
            scrollY = y
            invalidate()
        }
    }

    override fun scrollBy(dx: Float, dy: Float) {
        scrollX += dx
        scrollY += dy
    }

    override fun getAbsoluteX(): Float {
        var currentX = x
        var p = parent
        while (p != null) {
            currentX += p.x
            currentX -= p.scrollX
            p = p.parent
        }
        return currentX
    }

    override fun getAbsoluteY(): Float {
        var currentY = y
        var p = parent
        while (p != null) {
            currentY += p.y
            currentY -= p.scrollY
            p = p.parent
        }
        return currentY
    }

    override fun getAbsoluteTranslationX(): Float {
        var currentTX = translationX
        var p = parent
        while (p != null) {
            currentTX += p.translationX
            p = p.parent
        }
        return currentTX
    }

    override fun getAbsoluteTranslationY(): Float {
        var currentTY = translationY
        var p = parent
        while (p != null) {
            currentTY += p.translationY
            p = p.parent
        }
        return currentTY
    }

    override fun getAbsoluteAlpha(): Float {
        var currentAlpha = alpha
        var p = parent
        while (p != null) {
            currentAlpha *= p.alpha
            p = p.parent
        }
        return currentAlpha
    }

    override fun isAbsoluteEnabled(): Boolean {
        var current = this as Widget?
        while (current != null) {
            if (!current.isEnabled) return false
            current = current.parent
        }
        return true
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        val visualX = getAbsoluteX() + getAbsoluteTranslationX()
        val visualY = getAbsoluteY() + getAbsoluteTranslationY()
        return mouseX >= visualX && mouseY >= visualY && mouseX < visualX + width && mouseY < visualY + height
    }

    override fun canFocus(): Boolean {
        return false
    }

    override fun onFocusGained() {
    }

    override fun onFocusLost() {
    }

    override fun isAttached(): Boolean {
        return isAttached
    }

    override fun dispatchAttached() {
        if (isAttached) return
        isAttached = true
        onAttached()
        if (stateListAnimator != null) {
            stateListAnimator!!.jumpToCurrentState()
            updateStateAnimator()
        }
    }

    override fun dispatchDetached() {
        if (!isAttached) return
        onDetached()
        isAttached = false
        for (listener in detachListeners.toList()) listener()
        detachListeners.clear()
    }

    override fun addOnDetach(action: () -> Unit) {
        detachListeners.add(action)
    }

    override fun removeOnDetach(action: () -> Unit) {
        detachListeners.remove(action)
    }

    override fun onAttached() {
    }

    override fun onDetached() {
        cancelAnimations()
    }

    override fun startAnimation(animator: Animator) {
        attachedAnimators.add(animator)
        animator.addListener(object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                attachedAnimators.remove(animation)
            }

            override fun onAnimationCancel(animation: Animator) {
                attachedAnimators.remove(animation)
            }
        })
        animator.start()
    }

    override fun cancelAnimations() {
        for (anim in ArrayList(attachedAnimators)) anim.cancel()
        attachedAnimators.clear()
    }

    companion object {
        fun resolveSize(desiredSize: Float, spec: MeasureSpec): Float {
            val specMode = spec.mode
            val specSize = spec.size
            return when (specMode) {
                MeasureSpec.Mode.EXACTLY -> specSize
                MeasureSpec.Mode.AT_MOST -> min(desiredSize, specSize)
                else -> desiredSize
            }
        }
    }
}
