package org.academy.api.client.hud.ability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CpDisplayControllerTest {
    private val geometry = CpBarGeometry(240f, 27f)
    private val dt = 1000f / 60f

    @Test
    fun `fill edge chases target without jumps`() {
        val controller = CpDisplayController()
        controller.updateProgress(0f, dt)
        controller.updateProgress(1f, dt)
        var previous = controller.displayProgress

        var reached = false
        for (frame in 0 until 300) {
            controller.updateProgress(1f, dt)
            val current = controller.displayProgress
            assertTrue(current >= previous - 1e-6f, "increase must be monotonic")
            // 60fps 下全量变化的指数追赶单帧步长约 0.105，0.2 仅排除真正的跳变
            assertTrue(current - previous < 0.2f, "no per-frame jump: ${current - previous}")
            previous = current
            if (abs(current - 1f) < 1e-4f) {
                reached = true
                break
            }
        }
        assertTrue(reached, "fill must converge to full")
    }

    @Test
    fun `drain edge recedes without jumps`() {
        val controller = CpDisplayController()
        controller.updateProgress(1f, dt)
        repeat(300) { controller.updateProgress(1f, dt) }
        assertEquals(1f, controller.displayProgress, 1e-3f)
        var previous = controller.displayProgress

        var reached = false
        for (frame in 0 until 300) {
            controller.updateProgress(0f, dt)
            val current = controller.displayProgress
            assertTrue(current <= previous + 1e-6f, "decrease must be monotonic")
            assertTrue(previous - current < 0.2f, "no per-frame jump: ${previous - current}")
            previous = current
            if (current < 1e-4f) {
                reached = true
                break
            }
        }
        assertTrue(reached, "drain must converge to empty")
    }

    @Test
    fun `continuous regeneration keeps edge tracking without steps`() {
        val controller = CpDisplayController()
        controller.updateProgress(0.5f, dt)
        repeat(120) { controller.updateProgress(0.5f, dt) }

        var previous = controller.displayProgress
        for (frame in 0 until 400) {
            val target = (0.5f + 0.001f * frame).coerceAtMost(1f)
            controller.updateProgress(target, dt)
            val current = controller.displayProgress
            assertTrue(current - previous < 0.02f, "regen step must be continuous: ${current - previous}")
            assertTrue(current <= target + 1e-3f, "edge must not overshoot actual cp")
            previous = current
            if (target >= 1f) break
        }
    }

    @Test
    fun `direction reversal mid-flight is smooth`() {
        val controller = CpDisplayController()
        controller.updateProgress(0f, dt)

        // rise partway, then reverse before converging
        for (frame in 0 until 30) controller.updateProgress(0.9f, dt)
        for (frame in 0 until 300) {
            controller.updateProgress(0.2f, dt)
            val d = controller.displayProgress
            assertTrue(d in -1e-3f..0.91f, "edge must stay within sane bounds during reversal: $d")
        }
        assertTrue(abs(controller.displayProgress - 0.2f) < 1e-3f, "must converge to reversal target")
    }

    @Test
    fun `no mote ever enters the filled bar region`() {
        val controller = CpDisplayController()
        controller.updateProgress(0f, dt)

        fun assertInvariant() {
            val fillEdgeX = geometry.edgeX(controller.displayProgress)
            for (mote in controller.computeMotes(geometry, controller.timeSeconds)) {
                assertTrue(
                    mote.x + mote.size * 0.5f <= fillEdgeX + 1e-3f,
                    "mote #${mote.index} (x=${mote.x}, alpha=${mote.alpha}) overlaps fill at edge=$fillEdgeX"
                )
            }
        }

        // mixed scenarios: fill, drain, continuous regen, reversals
        repeat(200) { controller.updateProgress(0.6f, dt); assertInvariant() }
        repeat(100) { controller.updateProgress(1f, dt); assertInvariant() }
        repeat(150) { controller.updateProgress(0.3f, dt); assertInvariant() }
        repeat(100) { controller.updateProgress(0.8f, dt); assertInvariant() }
        repeat(150) { controller.updateProgress(0f, dt); assertInvariant() }
        for (frame in 0 until 200) {
            controller.updateProgress((frame % 100) / 100f, dt)
            assertInvariant()
        }
    }

    @Test
    fun `visible motes move continuously without teleporting`() {
        val controller = CpDisplayController()
        controller.updateProgress(0f, dt)

        var previousMotes = emptyMap<Int, CpDisplayController.Mote>()
        for (frame in 0 until 300) {
            controller.updateProgress(1f, dt)
            val motes = controller.computeMotes(geometry, controller.timeSeconds)
                .associateBy { it.index }
            for ((index, mote) in motes) {
                val prev = previousMotes[index] ?: continue
                if (mote.alpha < 0.05f) continue
                val dx = abs(mote.x - prev.x)
                assertTrue(
                    dx <= 15f,
                    "visible mote #$index moved ${dx}px in one frame (alpha=${mote.alpha})"
                )
            }
            previousMotes = motes
        }
    }

    @Test
    fun `motes appear while filling and fade when idle`() {
        val controller = CpDisplayController()

        // idle from start: no motes
        repeat(10) { controller.updateProgress(0f, dt) }
        assertTrue(controller.computeMotes(geometry, controller.timeSeconds).isEmpty())

        // active filling: motes present
        var sawActive = false
        for (frame in 0 until 60) {
            controller.updateProgress(0.7f, dt)
            val motes = controller.computeMotes(geometry, controller.timeSeconds)
            if (motes.isNotEmpty() && motes.maxOf { it.alpha } > 0.3f) {
                sawActive = true
                break
            }
        }
        assertTrue(sawActive, "filling should produce a visible mote field")

        // idle after settling: field fades away
        var faded = false
        for (frame in 0 until 600) {
            controller.updateProgress(0.7f, dt)
            val maxAlpha = controller.computeMotes(geometry, controller.timeSeconds)
                .maxOfOrNull { it.alpha } ?: 0f
            if (maxAlpha < 1e-3f) {
                faded = true
                break
            }
        }
        assertTrue(faded, "idle edge must fade the mote field out")
    }
}