package org.academy.api.client.gui.widget

import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.academy.api.client.gui.frame.UiFrame
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.text.fx.MarqueeState
import org.academy.api.client.gui.text.model.Ellipsize
import org.academy.api.client.gui.text.model.FontStyle
import org.academy.api.client.gui.text.model.TextShapingOptions
import org.academy.api.client.gui.text.record.TextPainter
import org.academy.api.client.gui.text.shape.TextMeasurer
import kotlin.math.min

open class TextWidget(text: String) : AbstractWidget(), TextHolder {
    override var textSize: Float = TextShapingOptions.DEFAULT_SIZE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    override var textColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var typeface: Identifier? = null
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var fontStyle: FontStyle = FontStyle.NORMAL
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var gravity: Int = Gravity.TOP_LEFT
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var lineSpacingMultiplier: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }
    var lineSpacingExtra: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var maxLines: Int = Int.MAX_VALUE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }
    var minLines: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var singleLine: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                displayText = baseText
                requestLayout()
                invalidate()
            }
        }

    var ellipsize: Ellipsize = Ellipsize.NONE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var marqueeSpeed: Float
        get() = marqueeState.speed
        set(value) {
            marqueeState.speed = value
        }

    var marqueeRepeatDelayMs: Long
        get() = marqueeState.repeatDelayMs
        set(value) {
            marqueeState.repeatDelayMs = value
        }

    var marqueeRepeatLimit: Int
        get() = marqueeState.repeatLimit
        set(value) {
            if (marqueeState.repeatLimit != value) {
                marqueeState.repeatLimit = value
                marqueeState.reset()
                invalidate()
            }
        }

    var marqueeFadeSize: Float
        get() = marqueeState.fadeLength
        set(value) {
            if (marqueeState.fadeLength != value) {
                marqueeState.fadeLength = value
                invalidate()
            }
        }

    var includeFontPadding: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var letterSpacing: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var allCaps: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var textScaleX: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var revealCodeUnits: Int = Int.MAX_VALUE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var fadeViewportLeft: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var fadeViewportWidth: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var fadeLength: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var fadeLeftStrength: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var fadeRightStrength: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    protected var layoutScale: Float = 1.0f

    private var baseText: String = text

    private var displayText: String = baseText
    private var measuredText: CharSequence? = null
    private var measuredFontSize = 0f
    private var measuredOptions: TextShapingOptions? = null
    private var measuredTextWidth = 0f
    private var measuredTextHeight = 0f


    private val ghostPainter: TextPainter = TextPainter()
    private val marqueeState: MarqueeState = MarqueeState()

    private var marqueeOverflow = false
    private var marqueeFrameCancel: (() -> Unit)? = null

    internal val marquee: MarqueeState get() = marqueeState

    internal val isMarqueeActive: Boolean get() = marqueeOverflow

    override var text: String
        get() = baseText
        set(value) {
            if (baseText != value) {
                baseText = value
                displayText = value
                if (ellipsize == Ellipsize.MARQUEE) marqueeState.reset()
                requestLayout()
                invalidate()
            }
        }

    private val redF: Float get() = ARGB.red(textColor) / 255.0f
    private val greenF: Float get() = ARGB.green(textColor) / 255.0f
    private val blueF: Float get() = ARGB.blue(textColor) / 255.0f

    protected val painter: TextPainter = TextPainter()

    protected open fun shapingOptions(): TextShapingOptions = TextShapingOptions(
        letterSpacing = letterSpacing,
        textScaleX = textScaleX,
        lineSpacingMultiplier = lineSpacingMultiplier,
        lineSpacingExtra = lineSpacingExtra,
        includeFontPadding = includeFontPadding,
        preferredFont = typeface,
        fontStyle = fontStyle
    )

    private fun effectiveText(text: String): String =
        if (allCaps) text.uppercase() else text

    protected open fun calculateLayoutScale(
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

    private fun ensureMeasured(text: CharSequence) {
        val options = shapingOptions()
        if (text.toString() != measuredText?.toString() ||
            textSize != measuredFontSize || options != measuredOptions
        ) {
            measuredText = text
            measuredFontSize = textSize
            measuredOptions = options
            measuredTextWidth = TextMeasurer.measureWidth(text, textSize, options)
            measuredTextHeight = TextMeasurer.measureHeight(text, textSize, options)
        }
    }

    protected fun getTextWidth(text: CharSequence): Float {
        ensureMeasured(text)
        return measuredTextWidth
    }

    protected fun getTextHeight(text: CharSequence): Float {
        ensureMeasured(text)
        return measuredTextHeight
    }

    private fun prepareDisplayText(constraintWidth: Float): String {
        val effective = effectiveText(baseText)
        if (singleLine) {
            if (ellipsize != Ellipsize.NONE && ellipsize != Ellipsize.MARQUEE &&
                constraintWidth > 0f && constraintWidth < Float.MAX_VALUE &&
                TextMeasurer.measureWidth(effective, textSize, shapingOptions()) > constraintWidth
            ) {
                layoutScale = 1f
                return ellipsizeText(effective, constraintWidth)
            }
            return effective
        }
        if (constraintWidth <= 0f || constraintWidth >= Float.MAX_VALUE) {
            layoutScale = 1f
            return effective
        }
        val wrapped = TextMeasurer.wrap(effective, textSize, constraintWidth, shapingOptions())
        val lines = wrapped.split('\n')
        val capped = if (maxLines > 0 && lines.size > maxLines) {
            val keep = lines.take(maxLines).toMutableList()
            if (ellipsize != Ellipsize.NONE && ellipsize != Ellipsize.MARQUEE) {
                val last = keep.last()
                keep[keep.size - 1] = ellipsizeText(last, constraintWidth)
            }
            keep.joinToString("\n")
        } else wrapped
        layoutScale = 1f
        return capped
    }

    private fun ellipsizeText(text: String, maxWidth: Float): String {
        val options = shapingOptions()
        if (TextMeasurer.measureWidth(text, textSize, options) <= maxWidth) return text
        return when (ellipsize) {
            Ellipsize.START -> {
                var low = 0
                var high = text.codePointCount(0, text.length)
                while (low < high) {
                    val mid = (low + high) ushr 1
                    val suffix = text.substring(text.offsetByCodePoints(0, mid))
                    if (TextMeasurer.measureWidth("…$suffix", textSize, options) <= maxWidth) high = mid
                    else low = mid + 1
                }
                "…" + text.substring(text.offsetByCodePoints(0, low))
            }

            Ellipsize.MIDDLE -> {
                var low = 0
                var high = text.codePointCount(0, text.length)
                while (low < high) {
                    val mid = (low + high + 1) ushr 1
                    val head = text.substring(0, text.offsetByCodePoints(0, mid))
                    val tailLen = text.codePointCount(0, text.length) - mid
                    val tail = text.substring(text.offsetByCodePoints(mid, tailLen))
                    if (TextMeasurer.measureWidth("$head…$tail", textSize, options) <= maxWidth) low = mid
                    else high = mid - 1
                }
                val head = text.substring(0, text.offsetByCodePoints(0, low))
                val tailLen = text.codePointCount(0, text.length) - low
                val tail = text.substring(text.offsetByCodePoints(low, tailLen))
                "$head…$tail"
            }

            else -> TextMeasurer.ellipsize(text, textSize, maxWidth, "…", options)
        }
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val lp = layoutParams
        if (baseText.isEmpty()) {
            displayText = ""
            layoutScale = 1f
            setMeasuredDimension(
                resolveSize(lp.paddingLeft + lp.paddingRight, widthMeasureSpec),
                resolveSize(lp.paddingTop + lp.paddingBottom, heightMeasureSpec)
            )
            return
        }

        val constraintWidth =
            if (widthMeasureSpec.mode == MeasureSpec.Mode.UNSPECIFIED) Float.MAX_VALUE else (widthMeasureSpec.size - lp.paddingLeft - lp.paddingRight)
        val constraintHeight =
            if (heightMeasureSpec.mode == MeasureSpec.Mode.UNSPECIFIED) Float.MAX_VALUE else (heightMeasureSpec.size - lp.paddingTop - lp.paddingBottom)

        displayText = prepareDisplayText(constraintWidth)
        val baseTextWidth = getTextWidth(displayText)
        var baseTextHeight = getTextHeight(displayText)

        val marqueeEnabled = singleLine && ellipsize == Ellipsize.MARQUEE &&
                constraintWidth > 0f && constraintWidth < Float.MAX_VALUE
        marqueeState.configure(baseTextWidth, if (marqueeEnabled) constraintWidth else 0f)
        val marquee = marqueeEnabled && marqueeState.overflow
        if (marquee != marqueeOverflow) {
            marqueeOverflow = marquee
            marqueeState.reset()
            syncMarqueeFrames()
        }

        if (singleLine && ellipsize == Ellipsize.NONE) {
            layoutScale = calculateLayoutScale(baseTextWidth, baseTextHeight, constraintWidth, constraintHeight)
        } else {
            layoutScale = 1f
        }

        val minHeight = minLines * minLinesHeight()
        if (minHeight > baseTextHeight) baseTextHeight = minHeight

        val measuredWidth = baseTextWidth * layoutScale + lp.paddingLeft + lp.paddingRight
        val measuredHeight = baseTextHeight * layoutScale + lp.paddingTop + lp.paddingBottom

        setMeasuredDimension(
            resolveSize(measuredWidth, widthMeasureSpec),
            resolveSize(measuredHeight, heightMeasureSpec)
        )
    }

    private fun minLinesHeight(): Float = TextMeasurer.lineHeight(textSize, shapingOptions())

    override fun renderInternal(context: Canvas) {
        super.renderInternal(context)
        val renderText = displayText
        if (renderText.isEmpty()) return

        val lp = layoutParams
        val baseTextWidth = getTextWidth(renderText)
        val baseTextHeight = getTextHeight(renderText)

        val contentScale = layoutScale
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom

        if (marqueeOverflow && availableWidth > 0f) {
            renderMarquee(
                context,
                renderText,
                baseTextWidth,
                baseTextHeight,
                contentScale,
                availableWidth,
                availableHeight
            )
            return
        }

        val visualTextWidth = baseTextWidth * contentScale
        val visualTextHeight = baseTextHeight * contentScale

        val (originX, originY) = TextPainter.blockOrigin(
            availableWidth, availableHeight, visualTextWidth, visualTextHeight,
            gravity, lp.paddingLeft, lp.paddingTop
        )

        painter.draw(
            context, renderText, textSize, redF, greenF, blueF,
            shapingOptions(), originX, originY, contentScale,
            alpha * context.accumulatedAlpha, revealCodeUnits,
            fadeViewportLeft, fadeViewportWidth, fadeLength,
            fadeLeftStrength, fadeRightStrength
        )
    }

    private fun renderMarquee(
        context: Canvas,
        renderText: String,
        baseTextWidth: Float,
        baseTextHeight: Float,
        contentScale: Float,
        availableWidth: Float,
        availableHeight: Float
    ) {
        val lp = layoutParams
        val visualTextHeight = baseTextHeight * contentScale
        val originY = TextPainter.blockOrigin(
            availableWidth, availableHeight, baseTextWidth * contentScale, visualTextHeight,
            gravity, lp.paddingLeft, lp.paddingTop
        ).second

        val scroll = marqueeState.scroll
        val textAlpha = alpha * context.accumulatedAlpha
        val fadeLen = marqueeState.fadeLength
        val leftStrength = marqueeState.leftStrength()
        val rightStrength = marqueeState.rightStrength()

        context.clipRect(lp.paddingLeft, lp.paddingTop, width - lp.paddingRight, height - lp.paddingBottom)
        painter.draw(
            context, renderText, textSize, redF, greenF, blueF,
            shapingOptions(), lp.paddingLeft - scroll, originY, contentScale,
            textAlpha, Int.MAX_VALUE,
            scroll, availableWidth, fadeLen, leftStrength, rightStrength
        )
        if (scroll > marqueeState.ghostStart) {
            ghostPainter.draw(
                context, renderText, textSize, redF, greenF, blueF,
                shapingOptions(), lp.paddingLeft - scroll + marqueeState.ghostOffset, originY, contentScale,
                textAlpha, Int.MAX_VALUE,
                scroll - marqueeState.ghostOffset, availableWidth, fadeLen, leftStrength, rightStrength
            )
        }
        context.disableScissor()
    }


    override fun onAttached() {
        if (marqueeOverflow) startMarqueeFrames()
    }

    override fun onDetached() {
        super.onDetached()
        stopMarqueeFrames()
    }

    private fun startMarqueeFrames() {
        if (marqueeFrameCancel != null || !isAttached() || !marqueeOverflow) return
        marqueeFrameCancel = UiFrame.post { tickMarquee() }
    }

    private fun stopMarqueeFrames() {
        marqueeFrameCancel?.invoke()
        marqueeFrameCancel = null
    }

    private fun syncMarqueeFrames() {
        if (marqueeOverflow && isAttached() && isVisible()) startMarqueeFrames() else stopMarqueeFrames()
    }

    private fun tickMarquee() {
        if (!marqueeOverflow || !isAttached()) {
            stopMarqueeFrames()
            return
        }
        if (!isVisible()) return

        if (marqueeState.advance(System.nanoTime() / 1_000_000L)) invalidate()
        if (marqueeState.finished) stopMarqueeFrames()
    }

    companion object {
        @JvmOverloads
        fun getTextWidth(
            text: String,
            textSize: Float,
            options: TextShapingOptions = TextShapingOptions.DEFAULT
        ): Float {
            return TextMeasurer.measureWidth(text, textSize, options)
        }

        @JvmOverloads
        fun getTextHeight(
            text: String,
            textSize: Float,
            options: TextShapingOptions = TextShapingOptions.DEFAULT
        ): Float {
            return TextMeasurer.measureHeight(text, textSize, options)
        }
    }
}
