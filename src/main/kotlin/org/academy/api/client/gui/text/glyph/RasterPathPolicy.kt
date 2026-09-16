package org.academy.api.client.gui.text.glyph

object RasterPathPolicy {
    const val BITMAP_PX_THRESHOLD = 32f

    const val TRANSFORMED_PX_THRESHOLD = 24f

    const val BITMAP_GAMMA_PX_MAX = 16f

    const val HYSTERESIS = 2f

    fun isMsdf(deviceTextSize: Float, transformed: Boolean): Boolean {
        val threshold = if (transformed) TRANSFORMED_PX_THRESHOLD else BITMAP_PX_THRESHOLD
        return deviceTextSize > threshold
    }

    fun isBitmap(deviceTextSize: Float, transformed: Boolean, previousBitmap: Boolean): Boolean {
        val threshold = if (transformed) TRANSFORMED_PX_THRESHOLD else BITMAP_PX_THRESHOLD
        val bound = if (previousBitmap) threshold + HYSTERESIS else threshold - HYSTERESIS
        return deviceTextSize <= bound
    }

    fun usesGamma(rasterPx: Float): Boolean = rasterPx <= BITMAP_GAMMA_PX_MAX
}
