package org.academy.api.client.gui.command

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.academy.api.client.render.Render
import org.academy.api.client.render.UniformPayload
import org.joml.Vector2f
import org.joml.Vector4f
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * SDF 圆角矩形绘制命令喵. 单一 pipeline 覆盖 圆角/描边/阴影/渐变,
 * 顶点仍为 QUAD (POSITION_TEX), 片元内做圆角盒 SDF.
 */
class RoundedRectDrawCommand(
    width: Float,
    height: Float,
    cornerRadius: Vector4f,
    borderWidth: Float,
    fillColor: Vector4f,
    borderColor: Vector4f,
    shadowColor: Vector4f,
    shadowBlur: Float,
    shadowOffset: Vector2f,
    gradientMode: Int,
    gradientFrom: Vector4f,
    gradientTo: Vector4f
) : PosTexRectDrawCommand(
    Render.RenderPipelines.ROUNDED_RECT,
    width, height, 0f, 0f, 1f, 1f,
    emptyList(),
    listOf(
        UniformPayload(
            "RoundedRectUniforms",
            RoundedRectData::class.java,
            RoundedRectData(
                Vector2f(width, height),
                cornerRadius,
                borderWidth,
                shadowBlur,
                shadowOffset,
                fillColor,
                borderColor,
                shadowColor,
                gradientMode,
                gradientFrom,
                gradientTo
            ),
            RoundedRectData.UBO_SIZE
        )
    )
) {
    private val shadowBlurValue: Float = shadowBlur
    private val shadowOffsetValue: Vector2f = Vector2f(shadowOffset)
    private val borderWidthValue: Float = borderWidth

    /**
     * 圆角盒 SDF 会绘制到标称矩形之外: 阴影按 [shadowBlur] 外扩并叠加 [shadowOffset],
     * 描边再外扩一半宽度. 取保守 AABB, 否则合并重排会漏裁.
     */
    override fun localBounds(): LocalBounds {
        val pad = shadowBlurValue + borderWidthValue * 0.5f + PosColorRectDrawCommand.AA
        val left = min(0f, shadowOffsetValue.x) - pad
        val top = min(0f, shadowOffsetValue.y) - pad
        val right = max(width, width + shadowOffsetValue.x) + pad
        val bottom = max(height, height + shadowOffsetValue.y) + pad
        return LocalBounds(left, top, right, bottom)
    }
}

data class RoundedRectData(
    val size: Vector2f,
    val cornerRadius: Vector4f,
    val borderWidth: Float,
    val shadowBlur: Float,
    val shadowOffset: Vector2f,
    val fillColor: Vector4f,
    val borderColor: Vector4f,
    val shadowColor: Vector4f,
    val gradientMode: Int,
    val gradientFrom: Vector4f,
    val gradientTo: Vector4f
) : DynamicUniform {
    override fun write(buffer: ByteBuffer) {
        Std140Builder.intoBuffer(buffer)
            .putVec2(size)
            .putVec2(0f, 0f)
            .putVec4(cornerRadius)
            .putFloat(borderWidth)
            .putFloat(shadowBlur)
            .putVec2(shadowOffset)
            .putVec4(fillColor)
            .putVec4(borderColor)
            .putVec4(shadowColor)
            .putInt(gradientMode)
            .putVec4(gradientFrom)
            .putVec4(gradientTo)
    }

    companion object {
        val UBO_SIZE: Int = Std140SizeCalculator()
            .putVec2().putVec2().putVec4().putFloat().putFloat().putVec2()
            .putVec4().putVec4().putVec4().putInt().putVec4().putVec4()
            .get()
    }
}

object RoundedRectGradient {
    const val NONE: Int = 0
    const val VERTICAL: Int = 1
    const val HORIZONTAL: Int = 2
    const val RADIAL: Int = 3
}
