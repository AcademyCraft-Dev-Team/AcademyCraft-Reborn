package org.academy.internal.client.app.props

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object PropsIcon {
    val LOCATION: Identifier = AcademyCraft.academy("dynamic/props_icon")

    fun init() {
        val image = NativeImage(32, 32, false)
        image.fillRect(0, 0, 32, 32, 0xFF171D24.toInt())
        val points = (0 until 5).map { index ->
            val angle = -PI / 2.0 + index * PI * 2.0 / 5.0
            Pair(
                (16.0 + cos(angle) * 12.0).roundToInt(),
                (16.0 + sin(angle) * 12.0).roundToInt()
            )
        }
        points.indices.forEach { index ->
            drawLine(image, points[index], points[(index + 1) % points.size], 0xFFD4F7FF.toInt())
            drawLine(image, Pair(16, 16), points[index], 0xFF547987.toInt())
        }
        val profile = listOf(Pair(16, 6), Pair(24, 14), Pair(21, 24), Pair(10, 23), Pair(8, 14))
        profile.indices.forEach { index ->
            drawLine(image, profile[index], profile[(index + 1) % profile.size], 0xFF4DE1FF.toInt())
        }
        Minecraft.getInstance().textureManager.register(
            LOCATION,
            DynamicTexture(Supplier { "academy_props_icon" }, image)
        )
    }

    private fun drawLine(image: NativeImage, from: Pair<Int, Int>, to: Pair<Int, Int>, color: Int) {
        var x0 = from.first
        var y0 = from.second
        val x1 = to.first
        val y1 = to.second
        val dx = kotlin.math.abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -kotlin.math.abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var error = dx + dy
        while (true) {
            if (x0 in 0 until image.width && y0 in 0 until image.height) image.setPixel(x0, y0, color)
            if (x0 == x1 && y0 == y1) break
            val doubled = error * 2
            if (doubled >= dy) {
                error += dy
                x0 += sx
            }
            if (doubled <= dx) {
                error += dx
                y0 += sy
            }
        }
    }
}
