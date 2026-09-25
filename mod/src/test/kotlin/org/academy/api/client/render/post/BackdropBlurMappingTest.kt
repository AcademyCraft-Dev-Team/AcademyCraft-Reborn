package org.academy.api.client.render.post

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证 BackdropBlur 的连续 / 恒等映射不变量 (纯 CPU, 无 GPU 依赖).
 */
class BackdropBlurMappingTest {

    private val s = 1f

    @Test
    fun `radius zero maps to level zero`() {
        assertEquals(0f, BackdropBlur.radiusToLevel(0f, s))
    }

    @Test
    fun `radius zero is identity span`() {
        val span = BackdropBlur.levelSpan(0f, s, layerCount = 4)
        assertTrue(span.isIdentity)
        assertEquals(0, span.lo)
        assertEquals(0, span.hi)
    }

    @Test
    fun `radiusToLevel is monotonic`() {
        var prev = BackdropBlur.radiusToLevel(0f, s)
        for (r in 1..100) {
            val v = BackdropBlur.radiusToLevel(r.toFloat(), s)
            assertTrue(v >= prev, "radiusToLevel must not decrease at r=$r")
            prev = v
        }
    }

    @Test
    fun `layer boundaries map exactly to integer levels`() {
        for (i in 0..6) {
            val boundary = BackdropBlur.layerRadius(i, s)
            assertEquals(i.toFloat(), BackdropBlur.radiusToLevel(boundary, s), 1e-5f)
        }
    }

    @Test
    fun `levelSpan crosses between adjacent levels at integer boundaries`() {
        val span = BackdropBlur.levelSpan(BackdropBlur.layerRadius(2, s), s, layerCount = 6)
        assertEquals(2, span.lo)
        assertTrue(span.hi == 2)
        assertEquals(0f, span.frac, 1e-5f)
    }

    @Test
    fun `fractional radius interpolates between levels`() {
        val span = BackdropBlur.levelSpan(1.5f * BackdropBlur.layerRadius(1, s), s, layerCount = 6)
        // r/s = 1.5 => k = log2(2.5) ~ 1.3219, lo=1 hi=2 frac>0
        assertTrue(span.lo in 1..2)
        assertTrue(span.frac > 0f && span.frac < 1f)
    }

    @Test
    fun `layer count adapts and caps at MAX_LAYERS`() {
        assertEquals(1, BackdropBlur.layersFor(0f, s))
        assertTrue(BackdropBlur.layersFor(20f, s) in 1..BackdropBlur.MAX_LAYERS)
        assertEquals(BackdropBlur.MAX_LAYERS, BackdropBlur.layersFor(100f * s, s))
    }

    @Test
    fun `pyramid sizes halve to a floor of one`() {
        val sizes = BackdropBlur.pyramidSize(100, 60, 8)
        assertEquals(8, sizes.size)
        assertEquals(100 to 60, sizes[0])
        assertEquals(50 to 30, sizes[1])
        assertEquals(1 to 1, sizes.last())
    }

    @Test
    fun `zero sigma degenerates to identity`() {
        assertEquals(0f, BackdropBlur.radiusToLevel(10f, 0f))
    }
}
