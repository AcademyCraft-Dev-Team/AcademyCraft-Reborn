package org.academy.api.client.util

import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.animation.TimeInterpolator
import org.academy.api.client.gui.widget.Widget

object AnimationUtil {
    const val DURATION: Long = 350L
    private const val Y_OFFSET = 20f

    fun show(widget: Widget) {
        reveal(
            widget = widget,
            targetAlpha = 1f,
            alphaDuration = DURATION,
            translationDuration = DURATION,
            yInterpolator = EasingFunctions.EASE_OUT_BACK,
            applyShowFlags = true
        )
    }

    /**
     * Fade + slide-up reveal. When [applyShowFlags] is false, only animates alpha/translation
     * (used by machine open rails that already manage visibility).
     */
    fun reveal(
        widget: Widget,
        targetAlpha: Float = 1f,
        alphaDuration: Long = DURATION,
        translationDuration: Long = DURATION,
        yInterpolator: TimeInterpolator = EasingFunctions.EASE_OUT_BACK,
        applyShowFlags: Boolean = true
    ) {
        widget.cancelAnimations()
        val endAlpha = targetAlpha.coerceIn(0f, 1f)
        widget.alpha = 0f
        widget.translationY = Y_OFFSET
        if (applyShowFlags) {
            widget.visibility = Widget.Visibility.VISIBLE
            widget.isEnabled = true
        }
        widget.startAnimation(
            ObjectAnimator.ofFloat({ widget.alpha = it }, 0f, endAlpha).setDuration(alphaDuration)
        )
        widget.startAnimation(
            ObjectAnimator.ofFloat({ widget.translationY = it }, Y_OFFSET, 0f)
                .setDuration(translationDuration)
                .setInterpolator(yInterpolator)
        )
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
