package org.academy.api.client.gui.msdf.util

import com.mojang.blaze3d.platform.NativeImage
import org.academy.api.client.gui.msdf.core.FloatBitmapRef
import kotlin.math.roundToInt

object MsdfTextureUtil {
    fun convertToNativeImage(bitmap: FloatBitmapRef): NativeImage {
        val width = bitmap.width
        val height = bitmap.height
        val channels = bitmap.nChannels
        val nativeImage = NativeImage(NativeImage.Format.RGBA, width, height, false)

        for (y in 0..<height) {
            for (x in 0..<width) {
                val index = bitmap.getIndex(x, y)
                val r = clamp(bitmap.pixels[index])
                val g = if (channels >= 2) clamp(bitmap.pixels[index + 1]) else r
                val b = if (channels >= 3) clamp(bitmap.pixels[index + 2]) else r
                val a = if (channels >= 4) clamp(bitmap.pixels[index + 3]) else 255

                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                nativeImage.setPixel(x, height - 1 - y, argb)
            }
        }
        return nativeImage
    }

    private fun clamp(value: Float): Int {
        return (value * 255f).roundToInt().coerceIn(0, 255)
    }
}