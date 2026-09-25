package org.academy.api.client.gui.widget

import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.animation.TimeInterpolator
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.render.Canvas

open class PagerLayoutWidget : FrameLayoutWidget() {
    private var pageOffset = 0f

    var currentPage: Int = 0
        private set

    val pageCount: Int get() = children.size

    var pageSwitchDuration: Long = 350L
    var pageSwitchInterpolator: TimeInterpolator = EasingFunctions.EASE_IN_OUT_SINE

    private var activeAnimator: Animator? = null

    fun switchToPage(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= children.size || index == currentPage) return
        activeAnimator?.cancel()
        currentPage = index
        val from = pageOffset
        val to = index.toFloat()
        if (!animate) {
            pageOffset = to
            invalidate()
            return
        }
        val anim = ObjectAnimator.ofFloat({ p ->
            pageOffset = p
            applyPageOffset()
            invalidate()
        }, from, to)
            .setDuration(pageSwitchDuration)
            .setInterpolator(pageSwitchInterpolator)
        activeAnimator = anim
        startAnimation(anim)
        invalidate()
    }

    fun jumpToPage(index: Int) {
        if (index < 0 || index >= children.size) return
        currentPage = index
        pageOffset = index.toFloat()
        applyPageOffset()
        invalidate()
    }

    fun setCurrentPageUnchecked(index: Int) {
        currentPage = index.coerceAtLeast(0)
        pageOffset = index.coerceAtLeast(0).toFloat()
        applyPageOffset()
        invalidate()
    }

    private fun applyPageOffset() {
        for ((index, child) in children.values.withIndex()) {
            val tx = (index - pageOffset) * width
            if (child.translationX != tx) child.translationX = tx
        }
    }

    override fun onLayout() {
        super.onLayout()
        applyPageOffset()
    }

    override fun render(context: Canvas) {
        if (!isVisible()) return
        pageOffset = pageOffset.coerceIn(0f, (children.size - 1).coerceAtLeast(0).toFloat())
        applyPageOffset()
        super.render(context)
    }

    override fun dispatchEvent(event: InputEvent) {
        if (event is MouseEvent && !isWithinVisibleBounds(event.x, event.y)) return
        if (event is ScrollEvent && !isWithinVisibleBounds(event.x, event.y)) return
        super.dispatchEvent(event)
    }

    private fun isWithinVisibleBounds(x: Double, y: Double): Boolean {
        val left = getAbsoluteX() + getAbsoluteTranslationX()
        val top = getAbsoluteY() + getAbsoluteTranslationY()
        return x >= left && y >= top && x < left + width && y < top + height
    }
}
