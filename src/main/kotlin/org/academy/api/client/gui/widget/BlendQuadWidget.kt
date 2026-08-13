package org.academy.api.client.gui.widget

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.command.PosTexRectDrawCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.render.Render
import org.academy.api.client.render.UniformPayload
import org.academy.api.client.resources.R
import org.joml.Vector2f
import org.joml.Vector4f
import java.nio.ByteBuffer
import kotlin.math.max

class BlendQuadWidget : AbstractWidget() {
    var marginTop: Float = 4f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var marginBottom: Float = 4f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var marginLeft: Float = 4f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var marginRight: Float = 4f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var drawLine: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var red: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var green: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var blue: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun render(context: RenderContext) {
        if (!isVisible()) return

        val lp = layoutParams
        val paddedWidth = width - lp.paddingLeft - lp.paddingRight
        val paddedHeight = height - lp.paddingTop - lp.paddingBottom

        // paddedHeight == 0 是预期行为喵
        if (paddedWidth <= 0 || paddedHeight < 0) return

        val finalAlpha = alpha * context.accumulatedAlpha

        context.pose().pushPose()
        context.pose().translate(lp.paddingLeft, lp.paddingTop)
        context.drawOrder().push()
        run {
            // 极小值也需要渲染喵
            if (finalAlpha != 0f) {
                val sdfCommand = PosTexRectDrawCommand(
                    Render.RenderPipelines.SDF_SHARP_MARGIN,
                    paddedWidth,
                    paddedHeight,
                    0.0f,
                    0.0f,
                    1.0f,
                    1.0f,
                    listOf(),
                    listOf(
                        UniformPayload(
                            "SdfUniforms",
                            SDFData::class.java, SDFData(
                                Vector2f(paddedWidth, paddedHeight),
                                Vector4f(
                                    marginLeft, marginTop, marginRight, marginBottom
                                ),
                                Vector4f(red, green, blue, finalAlpha)
                            ),
                            SDFData.UBO_SIZE
                        )
                    )
                )
                context.submit(sdfCommand)
            }
            if (drawLine) {
                context.drawOrder().advance()
                renderLines(context, context.accumulatedAlpha, paddedWidth, paddedHeight)
            }
        }
        context.drawOrder().pop()
        context.pose().popPose()
    }

    private fun renderLines(context: RenderContext, finalAlpha: Float, paddedWidth: Float, paddedHeight: Float) {
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        val lineTextureView = UiEnvironment.get().loadTexture(R.textures.gui.element.line)
        val lineH = 4.0f
        run {
            val topLineCommand = ImageDrawCommand(
                lineTextureView,
                sampler,
                paddedWidth,
                lineH,
                0f,
                0f,
                1f,
                1f,
                1.0f,
                1.0f,
                1.0f,
                finalAlpha
            )
            context.submit(topLineCommand)
        }
        run {
            context.pose().pushPose()
            context.pose().translate(0f, max(paddedHeight - lineH, 0f))
            val bottomLineCommand = ImageDrawCommand(
                lineTextureView,
                sampler,
                paddedWidth,
                lineH,
                0f,
                0f,
                1f,
                1f,
                1.0f,
                1.0f,
                1.0f,
                finalAlpha
            )
            context.submit(bottomLineCommand)
            context.pose().popPose()
        }
    }

    data class SDFData(
        val size: Vector2f, val margins: Vector4f,
        val fillColor: Vector4f
    ) : DynamicUniform {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer).putVec2(size).putVec4(margins).putVec4(fillColor)
        }

        companion object {
            val UBO_SIZE: Int = Std140SizeCalculator().putVec2().putVec4().putVec4().get()
        }
    }
}
