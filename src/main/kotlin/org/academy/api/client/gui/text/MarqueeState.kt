package org.academy.api.client.gui.text

import kotlin.math.min

/**
 * AOSP `TextView.Marquee` 的几何与时间核心，无字体/渲染依赖，便于单测。
 *
 * - 几何：`gap = viewport/3`、`ghostStart = textWidth - viewport + gap`、
 *   `maxScroll = ghostStart + viewport`、`ghostOffset = textWidth + gap`（对齐 AOSP `Marquee.start`）。
 * - 渐隐：`fadeStop`、`maxFadeScroll` 及左右强度（对齐 `TextView.getLeft/RightFadingEdgeStrength`）。
 * - 时间：由调用方每帧传入 [advance] 的当前毫秒时间，帧率无关。
 */
class MarqueeState {
    var textWidth: Float = 0f
    var viewportWidth: Float = 0f

    /** 速度，本地单位/秒。 */
    var speed: Float = 30f

    /** 每轮结束后的停顿，毫秒。 */
    var repeatDelayMs: Long = 1200L

    /** 重复次数，`-1` 无限。 */
    var repeatLimit: Int = -1

    /** 渐隐边长，本地单位。 */
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

    /** 文本超出视口。 */
    val overflow: Boolean get() = viewportWidth > 0f && textWidth > viewportWidth

    /** 有限次数用尽。 */
    val finished: Boolean get() = repeatLimit >= 0 && repeatRemaining <= 0

    /** 重新计算几何（每次测量后调用）。 */
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

    /** 从起点重新开始（溢出状态变化或属性变更时调用）。 */
    fun reset() {
        scroll = 0f
        lastFrameMs = -1L
        pauseUntilMs = 0L
        repeatRemaining = repeatLimit
    }

    /**
     * 推进一帧。
     * @return true 表示 [scroll] 发生变化（需要重绘）。
     */
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

    /** 左渐隐强度（AOSP `getLeftFadingEdgeStrength`）。 */
    fun leftStrength(): Float =
        if (fadeLength > 0f && scroll <= fadeStop) min(1f, scroll / fadeLength) else 0f

    /** 右渐隐强度（AOSP `getRightFadingEdgeStrength`）。 */
    fun rightStrength(): Float =
        if (fadeLength > 0f) min(1f, (maxFadeScroll - scroll) / fadeLength) else 0f

    companion object {
        /**
         * AOSP fading-edge alpha factor at block-local [u]：左边缘在 [fadeLen] 内按
         * [leftStrength] 渐隐，右边缘同理。禁用淡出时返回 1。
         */
        fun fadeFactor(
            u: Float,
            viewportLeft: Float,
            viewportWidth: Float,
            fadeLen: Float,
            leftStrength: Float,
            rightStrength: Float
        ): Float {
            if (fadeLen <= 0f || viewportWidth <= 0f) return 1f
            val local = u - viewportLeft
            val inFromLeft = (local / fadeLen).coerceIn(0f, 1f)
            val inFromRight = ((viewportWidth - local) / fadeLen).coerceIn(0f, 1f)
            val left = 1f - leftStrength * (1f - inFromLeft)
            val right = 1f - rightStrength * (1f - inFromRight)
            return (left * right).coerceIn(0f, 1f)
        }
    }
}
