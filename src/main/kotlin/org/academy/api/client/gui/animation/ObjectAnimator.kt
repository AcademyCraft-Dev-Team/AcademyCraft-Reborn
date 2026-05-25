package org.academy.api.client.gui.animation

import java.util.function.Consumer

class ObjectAnimator private constructor() : ValueAnimator() {
    private var getter: (() -> Float)? = null
    private var autoUpdateStartValue = false

    override fun start() {
        val getter = getter
        if (autoUpdateStartValue && getter != null) {
            setFloatValues(getter.invoke(), endValue)
        }
        super.start()
    }

    override fun onStartInternal() {
        val getter = getter
        if (autoUpdateStartValue && getter != null) {
            setFloatValues(getter.invoke(), endValue)
        }
        super.onStartInternal()
    }

    override fun setDuration(duration: Long): ObjectAnimator {
        super.setDuration(duration)
        return this
    }

    override fun setStartDelay(startDelay: Long): ObjectAnimator {
        super.setStartDelay(startDelay)
        return this
    }

    override fun setInterpolator(interpolator: TimeInterpolator): ObjectAnimator {
        super.setInterpolator(interpolator)
        return this
    }

    companion object {
        fun ofFloat(target: (Float) -> Unit, startValue: Float, endValue: Float): ObjectAnimator {
            val anim = ObjectAnimator()
            anim.setFloatValues(startValue, endValue)
            anim.addUpdateListener { animation -> target.invoke(animation.animatedValue) }
            target.invoke(startValue)
            return anim
        }

        fun ofFloat(getter: () -> Float, setter: Consumer<Float>, endValue: Float): ObjectAnimator {
            val anim = ObjectAnimator()
            anim.getter = getter
            anim.setFloatValues(getter.invoke(), endValue)
            anim.addUpdateListener { animation -> setter.accept(animation.animatedValue) }
            anim.autoUpdateStartValue = true
            return anim
        }
    }
}