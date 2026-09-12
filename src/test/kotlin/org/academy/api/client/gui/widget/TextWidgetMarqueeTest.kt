package org.academy.api.client.gui.widget

import org.academy.api.client.gui.text.MarqueeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 跑马灯几何/时序核心（AOSP `TextView.Marquee`）与 [TextWidget] 属性委托。
 * 这些用例不触发字体度量，纯数学。
 */
class TextWidgetMarqueeTest {
    private fun state(textWidth: Float = 300f, viewportWidth: Float = 100f): MarqueeState =
        MarqueeState().apply {
            speed = 30f
            repeatDelayMs = 1200L
            repeatLimit = -1
            fadeLength = 12f
            configure(textWidth, viewportWidth)
            reset()
        }

    @Test
    fun `configure matches AOSP marquee geometry`() {
        val s = state(textWidth = 300f, viewportWidth = 100f)
        val gap = 100f / 3f
        assertEquals(300f - 100f + gap, s.ghostStart, 1e-4f)
        assertEquals(s.ghostStart + 100f, s.maxScroll, 1e-4f)
        assertEquals(300f + gap, s.ghostOffset, 1e-4f)
        assertEquals(300f + 100f / 6f, s.fadeStop, 1e-4f)
        assertEquals(s.ghostStart + 2f * 300f, s.maxFadeScroll, 1e-4f)
        assertTrue(s.overflow)
    }

    @Test
    fun `overflow requires text wider than viewport`() {
        assertFalse(state(textWidth = 100f, viewportWidth = 100f).overflow)
        assertFalse(MarqueeState().apply { configure(100f, 0f) }.overflow, "viewport<=0 不跑马灯")
        assertTrue(state(textWidth = 101f, viewportWidth = 100f).overflow)
    }

    @Test
    fun `advance is frame-rate independent`() {
        val s = state(textWidth = 10_000f, viewportWidth = 100f)
        assertFalse(s.advance(0L), "首帧仅校准时间")
        assertTrue(s.advance(1000L))
        assertEquals(30f, s.scroll, 1e-4f)
        assertTrue(s.advance(2000L))
        assertEquals(60f, s.scroll, 1e-4f)
        assertFalse(s.advance(2000L), "dt=0 不产生变化")
        assertEquals(60f, s.scroll, 1e-4f)
    }

    @Test
    fun `reaching end pauses then wraps`() {
        val s = state(textWidth = 50f, viewportWidth = 10f)
        s.speed = 1000f
        val max = s.maxScroll
        s.advance(0L)
        s.advance(100L) // +100 -> clamp to max, schedule pause
        assertEquals(max, s.scroll, 1e-4f)
        assertFalse(s.advance(500L), "停顿期间不推进")
        assertEquals(max, s.scroll, 1e-4f)
        assertTrue(s.advance(1300L), "停顿结束当帧归零并重绘")
        assertEquals(0f, s.scroll, 1e-4f)
    }

    @Test
    fun `repeat limit stops the marquee`() {
        val s = state(textWidth = 50f, viewportWidth = 10f)
        s.repeatLimit = 1
        s.speed = 1000f
        s.reset()
        assertEquals(1, s.repeatRemaining)
        s.advance(0L)
        s.advance(100L)
        assertTrue(s.finished)
        assertFalse(s.advance(2000L), "结束后不再推进")
    }

    @Test
    fun `fade strengths follow AOSP scroll ramp`() {
        val s = state(textWidth = 300f, viewportWidth = 100f)
        assertEquals(0f, s.leftStrength(), 1e-4f, "scroll=0 左边缘不渐隐")
        assertEquals(1f, s.rightStrength(), 1e-4f, "远端右边缘满渐隐")

        s.fadeLength = 10f
        s.advance(0L)
        s.advance(1000L) // scroll = 30
        assertEquals(1f, s.leftStrength(), 1e-4f)
        assertTrue(s.rightStrength() in 0f..1f)
    }

    @Test
    fun `fadeFactor ramps at both edges and is 1 when disabled`() {
        val viewport = 100f
        assertEquals(0f, MarqueeState.fadeFactor(0f, 0f, viewport, 10f, 1f, 1f), 1e-4f)
        assertEquals(0.5f, MarqueeState.fadeFactor(5f, 0f, viewport, 10f, 1f, 1f), 1e-4f)
        assertEquals(1f, MarqueeState.fadeFactor(50f, 0f, viewport, 10f, 1f, 1f), 1e-4f)
        assertEquals(0f, MarqueeState.fadeFactor(100f, 0f, viewport, 10f, 1f, 1f), 1e-4f)
        assertEquals(1f, MarqueeState.fadeFactor(0f, 0f, viewport, 0f, 1f, 1f), 1e-4f)
        assertEquals(1f, MarqueeState.fadeFactor(0f, 0f, 0f, 10f, 1f, 1f), 1e-4f)
    }

    @Test
    fun `viewport shift follows scroll for the ghost copy`() {
        // 幽灵副本的淡出窗口整体左移 ghostOffset：窗口内某点仍应保持满 alpha。
        val viewport = 100f
        val ghostOffset = 53.333f
        val scroll = 153.333f
        val u = 153.333f - ghostOffset + 50f // 窗口中央
        assertEquals(
            1f,
            MarqueeState.fadeFactor(u, scroll - ghostOffset, viewport, 10f, 1f, 1f),
            1e-3f
        )
    }

    @Test
    fun `text widget delegates marquee properties`() {
        val widget = TextWidget("hello")
        assertFalse(widget.isMarqueeActive)
        assertEquals(-1, widget.marqueeRepeatLimit)
        assertEquals(30f, widget.marqueeSpeed, 0f)
        assertEquals(1200L, widget.marqueeRepeatDelayMs)
        assertEquals(12f, widget.marqueeFadeSize, 0f)

        widget.marqueeRepeatLimit = 2
        assertEquals(2, widget.marquee.repeatLimit)
        assertEquals(2, widget.marquee.repeatRemaining, "变更重复次数后重置剩余次数")

        widget.marqueeSpeed = 45f
        assertEquals(45f, widget.marquee.speed, 0f)
    }
}
