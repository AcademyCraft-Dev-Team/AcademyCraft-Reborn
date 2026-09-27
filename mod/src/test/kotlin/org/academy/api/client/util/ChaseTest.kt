package org.academy.api.client.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ChaseTest {
    private val tau = Chase.TIME_CONSTANT_MS

    @Test
    fun `factor is zero for non-positive dt`() {
        assertEquals(0f, Chase.factor(0f))
        assertEquals(0f, Chase.factor(-5f))
    }

    @Test
    fun `factor reaches one time constant at tau`() {
        assertEquals(1f - kotlin.math.exp(-1f), Chase.factor(tau), 1e-5f)
    }

    @Test
    fun `approach is monotonic and never overshoots`() {
        var value = 0f
        var previous = value
        repeat(600) {
            value = Chase.approach(value, 1f, 1000f / 60f)
            assertTrue(value >= previous, "must be monotonic: $value < $previous")
            previous = value
        }
        assertTrue(value <= 1f, "must not overshoot: $value")
        assertTrue(abs(value - 1f) < 1e-3f, "must converge: $value")
    }

    @Test
    fun `approach converges from either direction`() {
        var down = 1f
        repeat(600) { down = Chase.approach(down, 0.25f, 1000f / 60f) }
        assertTrue(abs(down - 0.25f) < 1e-3f)
    }

    @Test
    fun `approach is frame-rate independent`() {
        // 固定 300ms 时长，按整数步长分步，验证不同帧率结果一致
        fun simulate(fps: Int, totalMs: Float): Float {
            val dtMs = 1000f / fps
            val steps = (totalMs / dtMs).toInt()
            var value = 0f
            repeat(steps) { value = Chase.approach(value, 1f, dtMs) }
            return value
        }

        val at60 = simulate(60, 300f)
        val at30 = simulate(30, 300f)
        val at120 = simulate(120, 300f)
        assertEquals(at60, at30, 2e-3f)
        assertEquals(at60, at120, 2e-3f)
    }

    @Test
    fun `approach with zero dt keeps current value`() {
        assertEquals(0.3f, Chase.approach(0.3f, 0.9f, 0f))
    }
}
