package org.academy.api.client.gui.widget

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.render.RenderContext

/**
 * 九宫格控件喵. 把纹理按四边分割为 9 块: 四角原尺寸, 四边与中心拉伸,
 * 用于按钮/面板皮肤. 边框单位为纹理像素.
 */
open class NineSliceWidget(
    var texture: Identifier? = null,
    left: Float = 0f,
    right: Float = 0f,
    top: Float = 0f,
    bottom: Float = 0f
) : AbstractWidget() {
    var borderLeft: Float = left
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var borderRight: Float = right
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var borderTop: Float = top
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var borderBottom: Float = bottom
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var red: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var green: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var blue: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun setColor(r: Float, g: Float, b: Float): NineSliceWidget {
        red = r
        green = g
        blue = b
        return this
    }

    override fun render(context: RenderContext) {
        if (!isVisible() || width <= 0f || height <= 0f) return
        val texture = texture ?: return
        val textureView = UiEnvironment.get().loadTexture(texture)
        val finalAlpha = alpha * context.accumulatedAlpha
        if (finalAlpha == 0f) return

        val textureWidth = textureView.getWidth(0).coerceAtLeast(1)
        val textureHeight = textureView.getHeight(0).coerceAtLeast(1)
        val leftU = (borderLeft / textureWidth).coerceIn(0f, 1f)
        val rightU = (1f - borderRight / textureWidth).coerceIn(0f, 1f)
        val topV = (borderTop / textureHeight).coerceIn(0f, 1f)
        val bottomV = (1f - borderBottom / textureHeight).coerceIn(0f, 1f)

        val leftW = minOf(borderLeft, width)
        val rightW = minOf(borderRight, width)
        val topH = minOf(borderTop, height)
        val bottomH = minOf(borderBottom, height)
        val midW = (width - leftW - rightW).coerceAtLeast(0f)
        val midH = (height - topH - bottomH).coerceAtLeast(0f)

        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)

        context.pose().pushPose()
        context.drawOrder().push()
        run {
            context.drawOrder().advance()
            submitPatch(
                context, textureView, sampler, finalAlpha,
                0f, 0f, leftW, topH, 0f, 0f, leftU, topV
            )
            submitPatch(
                context, textureView, sampler, finalAlpha,
                leftW, 0f, midW, topH, leftU, 0f, rightU, topV
            )
            submitPatch(
                context, textureView, sampler, finalAlpha,
                leftW + midW, 0f, rightW, topH, rightU, 0f, 1f, topV
            )

            submitPatch(
                context, textureView, sampler, finalAlpha,
                0f, topH, leftW, midH, 0f, topV, leftU, bottomV
            )
            submitPatch(
                context, textureView, sampler, finalAlpha,
                leftW, topH, midW, midH, leftU, topV, rightU, bottomV
            )
            submitPatch(
                context, textureView, sampler, finalAlpha,
                leftW + midW, topH, rightW, midH, rightU, topV, 1f, bottomV
            )

            submitPatch(
                context, textureView, sampler, finalAlpha,
                0f, topH + midH, leftW, bottomH, 0f, bottomV, leftU, 1f
            )
            submitPatch(
                context, textureView, sampler, finalAlpha,
                leftW, topH + midH, midW, bottomH, leftU, bottomV, rightU, 1f
            )
            submitPatch(
                context, textureView, sampler, finalAlpha,
                leftW + midW, topH + midH, rightW, bottomH, rightU, bottomV, 1f, 1f
            )
        }
        context.drawOrder().pop()
        context.pose().popPose()
    }

    private fun submitPatch(
        context: RenderContext,
        textureView: com.mojang.blaze3d.textures.GpuTextureView,
        sampler: com.mojang.blaze3d.textures.GpuSampler,
        alpha: Float,
        x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float
    ) {
        if (w <= 0f || h <= 0f) return
        context.pose().pushPose()
        context.pose().translate(x, y)
        context.submit(
            ImageDrawCommand(
                textureView, sampler, w, h,
                u0, v0, u1, v1,
                red, green, blue, alpha
            )
        )
        context.pose().popPose()
    }
}
