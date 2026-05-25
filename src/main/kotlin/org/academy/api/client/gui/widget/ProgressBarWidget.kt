package org.academy.api.client.gui.widget

import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.render.RenderContext

open class ProgressBarWidget : AbstractWidget() {
    var max: Float = 100f
        protected set
    var min: Float = 0f
        protected set
    var progress: Float = 0f
        protected set

    var backgroundColor: Int = 0x40000000
        protected set
    var progressColor: Int = -0x1
        protected set

    var orientation: Orientation = Orientation.HORIZONTAL
        protected set

    override fun renderInternal(context: RenderContext) {
        super.renderInternal(context)

        val width = width
        val height = height

        if (width <= 0 || height <= 0) return

        val bgRed = ARGB.red(backgroundColor) / 255.0f
        val bgGreen = ARGB.green(backgroundColor) / 255.0f
        val bgBlue = ARGB.blue(backgroundColor) / 255.0f
        val bgAlpha = ARGB.alpha(backgroundColor) / 255.0f
        context.submit(
            generateBackDrawCommand(
                width,
                height,
                bgRed,
                bgGreen,
                bgBlue,
                bgAlpha * context.accumulatedAlpha
            )
        )

        val range = max - min
        var ratio = if (range > 0) (progress - min) / range else 0.0f
        ratio = Mth.clamp(ratio, 0.0f, 1.0f)

        val fgRed = ARGB.red(progressColor) / 255.0f
        val fgGreen = ARGB.green(progressColor) / 255.0f
        val fgBlue = ARGB.blue(progressColor) / 255.0f
        val fgAlpha = ARGB.alpha(progressColor) / 255.0f

        if (orientation == Orientation.HORIZONTAL) {
            val progressWidth = width * ratio
            if (progressWidth > 0) {
                context.submit(
                    generateProgressDrawCommand(
                        progressWidth,
                        height,
                        fgRed,
                        fgGreen,
                        fgBlue,
                        fgAlpha * context.accumulatedAlpha
                    )
                )
            }
        } else {
            val progressHeight = height * ratio
            if (progressHeight > 0) {
                context.pose().pushPose()
                context.pose().translate(0f, height - progressHeight, 0.1f)
                context.submit(
                    generateProgressDrawCommand(
                        width,
                        progressHeight,
                        fgRed,
                        fgGreen,
                        fgBlue,
                        fgAlpha * context.accumulatedAlpha
                    )
                )
                context.pose().popPose()
            }
        }
    }

    protected fun generateBackDrawCommand(
        width: Float, height: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): DrawCommand {
        return FillRectDrawCommand(width, height, red, green, blue, alpha)
    }

    protected fun generateProgressDrawCommand(
        width: Float, height: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): DrawCommand {
        return FillRectDrawCommand(width, height, red, green, blue, alpha)
    }

    fun setMax(max: Float): ProgressBarWidget {
        var max = max
        if (max < min) {
            max = min
        }
        if (this.max != max) {
            this.max = max
            setProgress(progress)
        }
        return this
    }

    fun setMin(min: Float): ProgressBarWidget {
        var min = min
        if (min > max) {
            min = max
        }
        if (this.min != min) {
            this.min = min
            setProgress(progress)
        }
        return this
    }

    open fun setProgress(progress: Float): ProgressBarWidget {
        val clampedProgress = Mth.clamp(progress, min, max)
        if (this.progress != clampedProgress) {
            this.progress = clampedProgress
        }
        return this
    }

    fun setBackgroundColor(backgroundColor: Int): ProgressBarWidget {
        this.backgroundColor = backgroundColor
        return this
    }

    fun setProgressColor(progressColor: Int): ProgressBarWidget {
        this.progressColor = progressColor
        return this
    }

    fun setOrientation(orientation: Orientation): ProgressBarWidget {
        this.orientation = orientation
        requestLayout()
        return this
    }
}