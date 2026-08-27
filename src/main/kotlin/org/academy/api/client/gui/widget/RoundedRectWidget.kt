package org.academy.api.client.gui.widget

import net.minecraft.util.ARGB
import org.academy.api.client.gui.command.RoundedRectDrawCommand
import org.academy.api.client.gui.command.RoundedRectGradient
import org.academy.api.client.gui.render.RenderContext
import org.joml.Vector2f
import org.joml.Vector4f

/**
 * SDF 圆角矩形控件喵. 单 pipeline 覆盖 圆角/描边/阴影/渐变.
 *
 * 颜色使用 ARGB Int (与 [org.academy.api.client.gui.drawable.ColorDrawable] 一致).
 */
open class RoundedRectWidget(
    fillColor: Int = 0xFFFFFFFF.toInt(),
    cornerRadius: Float = 4f
) : AbstractWidget() {
    var fillColor: Int = fillColor
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var cornerRadius: Vector4f = Vector4f(cornerRadius)
        set(value) {
            field = value
            invalidate()
        }
    var borderWidth: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var borderColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var shadowColor: Int = 0x80000000.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var shadowBlur: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var shadowOffset: Vector2f = Vector2f(0f)
        set(value) {
            field = value
            invalidate()
        }
    var gradientMode: Int = RoundedRectGradient.NONE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var gradientFrom: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var gradientTo: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun render(context: RenderContext) {
        if (!isVisible() || width <= 0f || height <= 0f) return
        val finalAlpha = alpha * context.accumulatedAlpha
        if (finalAlpha == 0f) return

        context.submit(
            RoundedRectDrawCommand(
                width, height,
                cornerRadius,
                borderWidth,
                argbToVec4(fillColor, finalAlpha),
                argbToVec4(borderColor, finalAlpha),
                argbToVec4(shadowColor, finalAlpha),
                shadowBlur,
                shadowOffset,
                gradientMode,
                argbToVec4(gradientFrom, finalAlpha),
                argbToVec4(gradientTo, finalAlpha)
            )
        )
    }

    fun setBorder(width: Float, color: Int): RoundedRectWidget {
        borderWidth = width
        borderColor = color
        return this
    }

    fun setShadow(color: Int, blur: Float, offsetX: Float = 0f, offsetY: Float = 0f): RoundedRectWidget {
        shadowColor = color
        shadowBlur = blur
        shadowOffset = Vector2f(offsetX, offsetY)
        return this
    }

    fun setCornerRadius(radius: Float): RoundedRectWidget {
        cornerRadius = Vector4f(radius)
        return this
    }

    fun setVerticalGradient(from: Int, to: Int): RoundedRectWidget {
        gradientMode = RoundedRectGradient.VERTICAL
        gradientFrom = from
        gradientTo = to
        return this
    }

    fun setHorizontalGradient(from: Int, to: Int): RoundedRectWidget {
        gradientMode = RoundedRectGradient.HORIZONTAL
        gradientFrom = from
        gradientTo = to
        return this
    }

    fun setRadialGradient(from: Int, to: Int): RoundedRectWidget {
        gradientMode = RoundedRectGradient.RADIAL
        gradientFrom = from
        gradientTo = to
        return this
    }

    companion object {
        fun argbToVec4(color: Int, alphaScale: Float): Vector4f {
            return Vector4f(
                ARGB.red(color) / 255.0f,
                ARGB.green(color) / 255.0f,
                ARGB.blue(color) / 255.0f,
                ARGB.alpha(color) / 255.0f * alphaScale
            )
        }
    }
}
