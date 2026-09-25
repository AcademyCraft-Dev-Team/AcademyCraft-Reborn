package org.academy.api.client.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GaussianSamplesTest {

    /**
     * 连续核: 任意 float 半径都保持多采样且权重归一 (中心 1 次 + 对称 2 次 = 1).
     */
    @Test
    fun `kernel stays multi-tap and normalized for any float radius`() {
        for (base in listOf(0.5f, 1f, 2f, 4f, 8f, 20f)) {
            val g = Render.GaussianSamples.getGaussianSamples(base)
            assertTrue(g.sampleCount() > 1, "radius=$base 核必须保留多采样")
            assertTrue(g.sampleCount() <= Render.GaussianSamples.MAX_GAUSSIAN_SAMPLES)
            var total = 0f
            for (i in 0 until g.sampleCount()) {
                total += (if (i == 0) 1f else 2f) * g.samples()[i].z()
            }
            assertEquals(1.0f, total, 1e-4f, "radius=$base 采样权重必须归一化")
        }
    }

    /**
     * 小半径平滑: 拓扑连续, radius 增大时最远 tap 偏移随之单调增大, radius->0 时收敛 (无跳变).
     */
    @Test
    fun `far tap offset scales monotonically with radius`() {
        val small = Render.GaussianSamples.getGaussianSamples(0.1f)
        val large = Render.GaussianSamples.getGaussianSamples(3f)
        val last = Render.GaussianSamples.MAX_GAUSSIAN_SAMPLES - 1
        assertTrue(small.samples()[last].x() < large.samples()[last].x())
    }
}
