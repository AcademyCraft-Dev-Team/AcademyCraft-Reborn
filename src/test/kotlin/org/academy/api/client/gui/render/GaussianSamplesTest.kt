package org.academy.api.client.gui.render

import org.academy.api.client.render.Render
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GaussianSamplesTest {

    @Test
    fun `radius 20 stays within sample slot bounds`() {
        val samples = Render.GaussianSamples.getGaussianSamples(20f)
        assertTrue(samples.sampleCount <= Render.GaussianSamples.MAX_GAUSSIAN_SAMPLES)
        assertTrue(samples.sampleCount >= 1)
    }

    @Test
    fun `large radius is clamped to the sample slot capacity`() {
        val samples = Render.GaussianSamples.getGaussianSamples(24f)
        assertEquals(Render.GaussianSamples.MAX_GAUSSIAN_SAMPLES, samples.sampleCount)
    }

    @Test
    fun `even radius weights are normalized`() {
        val samples = Render.GaussianSamples.getGaussianSamples(20f)
        // 着色器对每个样本做 +/- 双向采样, 因此对权重 double 求和 = 1.
        val total = samples.samples.filterIndexed { i, _ -> i < samples.sampleCount }
            .sumOf { (if (it == samples.samples[0]) it.z else it.z * 2.0).toDouble() }
        assertEquals(1.0, total, 1e-3)
    }
}
