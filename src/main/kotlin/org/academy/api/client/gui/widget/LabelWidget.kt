package org.academy.api.client.gui.widget

import org.academy.api.client.gui.command.GlyphDrawCommand
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.msdf.layout.MsdfTextProcessor.layout
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.util.GlyphCommandGenerator
import kotlin.math.min
import net.minecraft.util.Mth

open class LabelWidget(text: String) : AbstractWidget() {
    var baseFontSize: Float = DEFAULT_BASE_FONT_SIZE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }
    protected var layoutScale: Float = 1.0f
    protected var dropShadow: Boolean = false
    private var measuredText: String? = null
    private var measuredFontSize = 0f
    private var measuredTextWidth = 0f
    private var measuredTextHeight = 0f
    open var text: String = text
        set(text) {
            if (field != text) {
                field = text
                requestLayout()
                invalidate()
            }
        }
    protected var lastText: String? = null
    private var red = 1f
    private var green = 1f
    private var blue = 1f
    private var lastFinalAlpha = 1f
    protected var drawCommands: MutableList<GlyphDrawCommand> = mutableListOf()
    private var colorChanged = false

    protected fun calculateLayoutScale(
        baseTextWidth: Float,
        baseTextHeight: Float,
        constraintWidth: Float,
        constraintHeight: Float
    ): Float {
        var scaleX = 1.0f
        var scaleY = 1.0f

        if (constraintWidth > 0 && constraintWidth < Float.MAX_VALUE) {
            scaleX = constraintWidth / baseTextWidth
        }

        if (constraintHeight > 0 && constraintHeight < Float.MAX_VALUE) {
            scaleY = constraintHeight / baseTextHeight
        }

        val finalScale = min(scaleX, scaleY)

        return Mth.clamp(finalScale, 0.0f, 1.0f)
    }

    private fun ensureMeasured(text: String) {
        if (text !== measuredText || baseFontSize != measuredFontSize) {
            measuredText = text
            measuredFontSize = baseFontSize
            measuredTextWidth = getTextWidth(text, baseFontSize)
            measuredTextHeight = getTextHeight(text, baseFontSize)
        }
    }

    protected fun getTextWidth(text: String): Float {
        ensureMeasured(text)
        return measuredTextWidth
    }

    protected fun getTextHeight(text: String): Float {
        ensureMeasured(text)
        return measuredTextHeight
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val lp = layoutParams
        if (text.isEmpty()) {
            layoutScale = 1f
            setMeasuredDimension(
                resolveSize(lp.paddingLeft + lp.paddingRight, widthMeasureSpec),
                resolveSize(lp.paddingTop + lp.paddingBottom, heightMeasureSpec)
            )
            return
        }

        val baseTextWidth = getTextWidth(text)
        val baseTextHeight = getTextHeight(text)

        val constraintWidth =
            if (widthMeasureSpec.mode == MeasureSpec.Mode.UNSPECIFIED) Float.MAX_VALUE else (widthMeasureSpec.size - lp.paddingLeft - lp.paddingRight)
        val constraintHeight =
            if (heightMeasureSpec.mode == MeasureSpec.Mode.UNSPECIFIED) Float.MAX_VALUE else (heightMeasureSpec.size - lp.paddingTop - lp.paddingBottom)

        layoutScale = calculateLayoutScale(baseTextWidth, baseTextHeight, constraintWidth, constraintHeight)

        val measuredWidth = baseTextWidth * layoutScale + lp.paddingLeft + lp.paddingRight
        val measuredHeight = baseTextHeight * layoutScale + lp.paddingTop + lp.paddingBottom

        setMeasuredDimension(
            resolveSize(measuredWidth, widthMeasureSpec),
            resolveSize(measuredHeight, heightMeasureSpec)
        )
    }

    override fun render(context: RenderContext) {
        super.render(context)
        if (!isVisible() || text.isEmpty()) return

        val lp = layoutParams
        val baseTextWidth = getTextWidth(text)
        val baseTextHeight = getTextHeight(text)

        val finalScale = scale * layoutScale

        val visualTextWidth = baseTextWidth * finalScale
        val visualTextHeight = baseTextHeight * finalScale

        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom

        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX =
            availableWidth - visualTextWidth

        var alignmentOffsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY =
            availableHeight - visualTextHeight

        context.pose().pushPose()
        context.drawOrder().push()
        run {
            context.drawOrder().advance()
            val textTopY = lp.paddingTop + alignmentOffsetY
            context.pose().translate(lp.paddingLeft + alignmentOffsetX, textTopY)
            context.pose().scale(finalScale, finalScale)

            val finalAlpha = alpha * context.accumulatedAlpha
            if (colorChanged || lastFinalAlpha != finalAlpha || (text != lastText)) {
                drawCommands = generateDrawCommands(
                    text, baseFontSize, 0f, red, green, blue, finalAlpha
                )
                lastText = text
                colorChanged = false
            }
            lastFinalAlpha = finalAlpha
            for (command in drawCommands) context.submit(command)
        }
        context.drawOrder().pop()
        context.pose().popPose()
    }

    protected open fun generateDrawCommands(
        text: String,
        fontSize: Float, thickness: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): MutableList<GlyphDrawCommand> {
        return GlyphCommandGenerator.generate(text, fontSize, thickness, red, green, blue, alpha)
    }

    fun getRed(): Float {
        return red
    }

    fun setRed(red: Float) {
        if (red != this.red) {
            this.red = red
            colorChanged = true
            invalidate()
        }
    }

    fun getBlue(): Float {
        return blue
    }

    fun setBlue(blue: Float) {
        if (blue != this.blue) {
            this.blue = blue
            colorChanged = true
            invalidate()
        }
    }

    fun getGreen(): Float {
        return green
    }

    fun setGreen(green: Float) {
        if (green != this.green) {
            this.green = green
            colorChanged = true
            invalidate()
        }
    }

    fun setDropShadow(dropShadow: Boolean): LabelWidget {
        this.dropShadow = dropShadow
        return this
    }

    companion object {
        const val DEFAULT_BASE_FONT_SIZE: Float = 8f
        fun getTextWidth(text: String, baseFontSize: Float): Float {
            val instances = layout(text, baseFontSize)
            var maxX = 0f
            for (inst in instances) {
                val right = inst.x + inst.quadWidth
                if (right > maxX) maxX = right
            }
            return maxX
        }

        fun getTextHeight(text: String, baseFontSize: Float): Float {
            val instances = layout(text, baseFontSize)
            var maxY = 0f
            for (inst in instances) {
                val bottom = inst.y + inst.quadHeight
                if (bottom > maxY) maxY = bottom
            }
            return maxY
        }
    }
}
