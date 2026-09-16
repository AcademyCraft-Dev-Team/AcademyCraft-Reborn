package org.academy.api.client.gui.text.fx

import kotlin.math.min

class MarqueeState {
    var textWidth: Float = 0f
    var viewportWidth: Float = 0f

    var speed: Float = 30f

    var repeatDelayMs: Long = 1200L

    var repeatLimit: Int = -1

    var fadeLength: Float = 12f

    var scroll: Float = 0f
        private set
    var ghostStart: Float = 0f
        private set
    var maxScroll: Float = 0f
        private set
    var ghostOffset: Float = 0f
        private set
    var fadeStop: Float = 0f
        private set
    var maxFadeScroll: Float = 0f
        private set
    var repeatRemaining: Int = 0
        private set

    private var lastFrameMs: Long = -1L
    private var pauseUntilMs: Long = 0L

    val overflow: Boolean get() = viewportWidth > 0f && textWidth > viewportWidth

    val finished: Boolean get() = repeatLimit >= 0 && repeatRemaining <= 0

    fun configure(textWidth: Float, viewportWidth: Float) {
        this.textWidth = textWidth
        this.viewportWidth = viewportWidth
        if (viewportWidth <= 0f) {
            ghostStart = 0f
            maxScroll = 0f
            ghostOffset = 0f
            fadeStop = 0f
            maxFadeScroll = 0f
            scroll = 0f
            return
        }
        val gap = viewportWidth / 3f
        ghostStart = textWidth - viewportWidth + gap
        maxScroll = ghostStart + viewportWidth
        ghostOffset = textWidth + gap
        fadeStop = textWidth + viewportWidth / 6f
        maxFadeScroll = ghostStart + 2f * textWidth
        if (scroll > maxScroll) scroll = maxScroll
        if (scroll < 0f) scroll = 0f
    }

    fun reset() {
        scroll = 0f
        lastFrameMs = -1L
        pauseUntilMs = 0L
        repeatRemaining = repeatLimit
    }

    fun advance(nowMs: Long): Boolean {
        if (finished) return false

        if (pauseUntilMs != 0L) {
            if (nowMs < pauseUntilMs) return false
            pauseUntilMs = 0L
            scroll = 0f
            lastFrameMs = nowMs
            return true
        }
        if (lastFrameMs < 0L) lastFrameMs = nowMs
        val dt = nowMs - lastFrameMs
        lastFrameMs = nowMs
        if (dt <= 0L) return false

        scroll += dt / 1000f * speed
        if (scroll >= maxScroll) {
            scroll = maxScroll
            if (repeatLimit >= 0) {
                repeatRemaining--
                if (repeatRemaining <= 0) return true
            }
            pauseUntilMs = nowMs + repeatDelayMs
        }
        return true
    }

    fun leftStrength(): Float =
        if (fadeLength > 0f && scroll <= fadeStop) min(1f, scroll / fadeLength) else 0f

    fun rightStrength(): Float =
        if (fadeLength > 0f) min(1f, (maxFadeScroll - scroll) / fadeLength) else 0f
}
