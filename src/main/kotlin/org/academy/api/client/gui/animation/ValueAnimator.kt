package org.academy.api.client.gui.animation

import java.util.function.Consumer

open class ValueAnimator protected constructor() : Animator() {
    private var startValue = 0f
    protected var endValue: Float = 0f
        private set
    var animatedValue: Float = 0f
        private set

    var repeatCount: Int = 0
    var repeatMode: Int = RESTART

    private var currentIteration = 0
    private var isReversing = false

    private var interpolator = EasingFunctions.LINEAR
    private val updateListeners: MutableList<Consumer<ValueAnimator>> = ArrayList<Consumer<ValueAnimator>>()

    fun setFloatValues(startValue: Float, endValue: Float) {
        this.startValue = startValue
        this.endValue = endValue
        animatedValue = startValue
    }

    override fun setDuration(duration: Long): ValueAnimator {
        super.setDuration(duration)
        return this
    }

    override fun setStartDelay(startDelay: Long): ValueAnimator {
        super.setStartDelay(startDelay)
        return this
    }

    open fun setInterpolator(interpolator: TimeInterpolator): ValueAnimator {
        this.interpolator = interpolator
        return this
    }

    fun addUpdateListener(listener: Consumer<ValueAnimator>) {
        updateListeners.add(listener)
    }

    fun removeUpdateListener(listener: Consumer<ValueAnimator>) {
        updateListeners.remove(listener)
    }

    override fun onStartInternal() {
        isReversing = false
        currentIteration = 0
        super.onStartInternal()
    }

    override fun tick(currentTime: Long): Boolean {
        if (isPaused) {
            if (pauseBeginTime == -1L) {
                pauseBeginTime = currentTime
            }
            return false
        } else {
            if (pauseBeginTime != -1L) {
                val pausedDuration = currentTime - pauseBeginTime
                startTime += pausedDuration
                pauseBeginTime = -1
            }
        }

        if (startTime == -1L) return true
        if (currentTime < startTime) return false

        var elapsedTime = currentTime - startTime
        var fraction = if (duration > 0) elapsedTime.toFloat() / duration else 1.0f

        var finished = false
        if (fraction >= 1.0f) {
            if (repeatCount == INFINITE || currentIteration < repeatCount) {
                currentIteration++

                if (repeatMode == REVERSE) {
                    isReversing = !isReversing
                }

                startTime += duration
                elapsedTime = currentTime - startTime
                fraction = if (duration > 0) elapsedTime.toFloat() / duration else 0f
            } else {
                finished = true
            }
        }

        fraction = Math.clamp(fraction, 0.0f, 1.0f)

        val effectiveFraction = if (isReversing) (1.0f - fraction) else fraction
        val interpolatedFraction = interpolator.getInterpolation(effectiveFraction)
        animatedValue = startValue + interpolatedFraction * (endValue - startValue)

        for (listener in updateListeners) listener.accept(this)

        if (finished) end()

        return finished
    }

    companion object {
        const val INFINITE: Int = -1
        const val RESTART: Int = 1
        const val REVERSE: Int = 2

        fun ofFloat(startValue: Float, endValue: Float): ValueAnimator {
            val anim = ValueAnimator()
            anim.setFloatValues(startValue, endValue)
            return anim
        }
    }
}