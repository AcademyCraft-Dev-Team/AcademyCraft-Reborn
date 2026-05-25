package org.academy.api.client.util

import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.widget.Widget

object AnimationUtil {
    const val DURATION: Long = 350L
    private const val Y_OFFSET = 20f

    fun show(widget: Widget) {
        widget.cancelAnimations()
        widget.alpha = 0f
        widget.visibility = Widget.Visibility.VISIBLE
        widget.isEnabled = true

        moveTranslationYShow(widget)
        alphaShow(widget)
    }

    fun hide(widget: Widget) {
        widget.cancelAnimations()
        widget.isEnabled = false

        moveTranslationYHide(widget)
        alphaHide(widget) {}
    }

    fun moveTranslationYShow(widget: Widget) {
        widget.translationY = Y_OFFSET
        val anim: ObjectAnimator = ObjectAnimator.ofFloat(
            { widget.translationY = it }, Y_OFFSET, 0f
        ).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_OUT_BACK)
        widget.startAnimation(anim)
    }

    fun moveTranslationYHide(widget: Widget) {
        val startY = widget.translationY
        val anim: ObjectAnimator = ObjectAnimator.ofFloat(
            { widget.translationY = it }, startY, startY + Y_OFFSET
        ).setDuration(DURATION).setInterpolator(EasingFunctions.EASE_IN_CUBIC)
        widget.startAnimation(anim)
    }

    fun alphaShow(widget: Widget) {
        widget.startAnimation(
            ObjectAnimator.ofFloat(
                { widget.alpha = it }, 0f, 1f
            ).setDuration(DURATION)
        )
    }

    fun alphaHide(widget: Widget, onEndCallback: Runnable) {
        val alphaAnim: ObjectAnimator =
            ObjectAnimator.ofFloat(
                { widget.alpha = it }, widget.alpha, 0f
            ).setDuration(DURATION)
        alphaAnim.addListener(createHideListener(widget, onEndCallback))
        widget.startAnimation(alphaAnim)
    }

    private fun createHideListener(widget: Widget, onEndCallback: Runnable): AnimatorListener {
        return object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                widget.visibility = Widget.Visibility.INVISIBLE
                onEndCallback.run()
            }
        }
    }
}