package org.academy.api.client.gui.animation

abstract class Animator {
    var startTime: Long = -1
    var startDelay: Long = 0
    var duration: Long = 300
    var isRunning: Boolean = false
    var isPaused: Boolean = false
    var pauseBeginTime: Long = -1
    private val listeners: MutableList<AnimatorListener> = ArrayList()

    open fun start() {
        if (this.isRunning) return
        this.isRunning = true
        this.isPaused = false
        pauseBeginTime = -1
        AnimationManager.startAnimation(this)
    }

    fun cancel() {
        if (!this.isRunning) return
        this.isRunning = false
        this.isPaused = false
        AnimationManager.remove(this)
        val tempList = ArrayList<AnimatorListener?>(listeners)
        for (listener in tempList) listener!!.onAnimationCancel(this)
    }

    fun end() {
        if (!this.isRunning) return
        this.isRunning = false
        this.isPaused = false
        AnimationManager.remove(this)
        val tempList = ArrayList<AnimatorListener?>(listeners)
        for (listener in tempList) listener!!.onAnimationEnd(this)
    }

    fun pause() {
        if (this.isRunning && !this.isPaused) {
            this.isPaused = true
            pauseBeginTime = -1
        }
    }

    fun resume() {
        if (this.isRunning && this.isPaused) this.isPaused = false
    }

    open fun onStartInternal() {
        this.isRunning = true
        this.isPaused = false
        val tempList = ArrayList<AnimatorListener?>(listeners)
        for (listener in tempList) listener!!.onAnimationStart(this)
    }

    fun addListener(listener: AnimatorListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AnimatorListener) {
        listeners.remove(listener)
    }

    open fun setDuration(duration: Long): Animator {
        require(duration >= 0) { "Animators cannot have negative duration: $duration" }
        this.duration = duration
        return this
    }

    open fun setStartDelay(startDelay: Long): Animator {
        require(startDelay >= 0) { "Animators cannot have negative start delay: $startDelay" }
        this.startDelay = startDelay
        return this
    }

    abstract fun tick(currentTime: Long): Boolean
}