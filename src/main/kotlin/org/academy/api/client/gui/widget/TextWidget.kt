package org.academy.api.client.gui.widget

import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.academy.api.client.gui.frame.UiFrame
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.text.Ellipsize
import org.academy.api.client.gui.text.MarqueeState
import org.academy.api.client.gui.text.Spanned
import org.academy.api.client.gui.text.TextLayoutManager
import org.academy.api.client.gui.text.TextPainter
import org.academy.api.client.gui.text.TextShapingOptions
import org.academy.api.client.gui.text.TextStyle
import kotlin.math.min

/**
 * 文本控件，API 对齐 Android `TextView`。
 *
 * - 字号 `sp`、颜色 `ARGB`、字体/字重、对齐、行距、省略号、全大写、字距等均为独立属性。
 * - `singleLine=true`（默认）：不换行，溢出时自动适配（layoutScale）或按 [ellipsize] 截断；
 *   `singleLine=false`：按宽度换行，受 [maxLines]/[minLines] 约束。
 * - 控件自身缩放经 pose（CTM）施加，文本只烘焙 `layoutScale`（内容适配）。
 */
open class TextWidget(text: String) : AbstractWidget(), TextHolder {
    /** 文本字号，sp。 */
    override var textSize: Float = DEFAULT_TEXT_SIZE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** 文本颜色，ARGB。 */
    override var textColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 首选字体；null 表示逐字回退。 */
    var typeface: Identifier? = null
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** 字重/字形。 */
    var textStyle: TextStyle = TextStyle.NORMAL
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** 文本在 bounds 内的对齐（独立于父布局的 `layoutParams.gravity`）。 */
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

    /** 单行模式（不换行）。默认 true，与旧 `wrapText=false` 行为一致。 */
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

    /** 跑马灯滚动速度，本地单位/秒（AOSP `MARQUEE_DP_PER_SECOND = 30`）。 */
    var marqueeSpeed: Float
        get() = marqueeState.speed
        set(value) {
            marqueeState.speed = value
        }

    /** 每轮走完后的停顿，毫秒（AOSP `MARQUEE_DELAY = 1200`）。 */
    var marqueeRepeatDelayMs: Long
        get() = marqueeState.repeatDelayMs
        set(value) {
            marqueeState.repeatDelayMs = value
        }

    /** 重复次数，`-1` 表示无限（AOSP 默认 3，此处默认无限）。 */
    var marqueeRepeatLimit: Int
        get() = marqueeState.repeatLimit
        set(value) {
            if (marqueeState.repeatLimit != value) {
                marqueeState.repeatLimit = value
                marqueeState.reset()
                invalidate()
            }
        }

    /** 渐隐边长，本地单位（AOSP `FADING_EDGE_LENGTH = 12` dp）。 */
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

    /** 字母间距，em 倍数。 */
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

    /** 水平缩放（字距压缩/拉伸），1.0 正常。 */
    var textScaleX: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /**
     * Typewriter reveal limit in UTF-16 code units ([Int.MAX_VALUE] = fully visible).
     * The blob command expands only the visible glyphs; no command list truncation.
     */
    var revealCodeUnits: Int = Int.MAX_VALUE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    protected var layoutScale: Float = 1.0f

    /** 未换行的基准文本（可带行内样式）。 */
    private var baseText: Spanned = Spanned.of(text)

    /** 实际测量/绘制用文本（[singleLine] 为 false 时为换行结果）。 */
    private var displayText: Spanned = baseText
    private var measuredText: CharSequence? = null
    private var measuredFontSize = 0f
    private var measuredOptions: TextShapingOptions? = null
    private var measuredTextWidth = 0f
    private var measuredTextHeight = 0f

    // ---- Marquee（跑马灯）状态 ----

    private val ghostPainter: TextPainter = TextPainter()
    private val marqueeState: MarqueeState = MarqueeState()

    /** 当前是否处于跑马灯（单行 + MARQUEE + 溢出）。 */
    private var marqueeOverflow = false
    private var marqueeFrameCancel: (() -> Unit)? = null

    /** 供测试读取跑马灯几何/时序核心。 */
    internal val marquee: MarqueeState get() = marqueeState

    /** 供测试读取当前是否处于跑马灯。 */
    internal val isMarqueeActive: Boolean get() = marqueeOverflow

    override var text: String
        get() = baseText.text
        set(value) {
            if (baseText.text != value) {
                spannedText = Spanned.of(value)
            }
        }

    /** 可带行内样式的文本；无 span 时等价于 [text]。 */
    open var spannedText: Spanned
        get() = baseText
        set(value) {
            if (baseText != value) {
                baseText = value
                displayText = value
                marqueeState.reset()
                requestLayout()
                invalidate()
            }
        }

    private val redF: Float get() = ARGB.red(textColor) / 255.0f
    private val greenF: Float get() = ARGB.green(textColor) / 255.0f
    private val blueF: Float get() = ARGB.blue(textColor) / 255.0f

    /** Device-independent record; expanded with the canvas CTM at batch time. */
    protected val painter: TextPainter = TextPainter()

    /** 派生 shaping 参数（供子类覆盖）。 */
    protected open fun shapingOptions(): TextShapingOptions = TextShapingOptions(
        letterSpacing = letterSpacing,
        textScaleX = textScaleX,
        lineSpacingMultiplier = lineSpacingMultiplier,
        lineSpacingExtra = lineSpacingExtra,
        includeFontPadding = includeFontPadding,
        preferredFont = typeface,
        textStyle = textStyle
    )

    /** [allCaps] 作用于显示文本；spans 位置随大小写变化而失效（spans 目前不渲染）。 */
    private fun effectiveText(spanned: Spanned): Spanned =
        if (allCaps) Spanned.of(spanned.text.uppercase()) else spanned

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
            measuredTextWidth = TextLayoutManager.measureWidth(text, textSize, options)
            measuredTextHeight = TextLayoutManager.measureHeight(text, textSize, options)
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

    private fun prepareDisplayText(constraintWidth: Float): Spanned {
        val effective = effectiveText(baseText)
        if (singleLine) {
            if (ellipsize != Ellipsize.NONE && ellipsize != Ellipsize.MARQUEE &&
                constraintWidth > 0f && constraintWidth < Float.MAX_VALUE &&
                TextLayoutManager.measureWidth(effective.text, textSize, shapingOptions()) > constraintWidth
            ) {
                layoutScale = 1f
                return Spanned.of(ellipsizeText(effective.text, constraintWidth))
            }
            return effective
        }
        if (constraintWidth <= 0f || constraintWidth >= Float.MAX_VALUE) {
            layoutScale = 1f
            return effective
        }
        val wrapped = TextLayoutManager.wrap(effective.text, textSize, constraintWidth, shapingOptions())
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
        return Spanned.of(capped)
    }

    private fun ellipsizeText(text: String, maxWidth: Float): String {
        val options = shapingOptions()
        if (TextLayoutManager.measureWidth(text, textSize, options) <= maxWidth) return text
        return when (ellipsize) {
            Ellipsize.START -> {
                var low = 0
                var high = text.codePointCount(0, text.length)
                while (low < high) {
                    val mid = (low + high) ushr 1
                    val suffix = text.substring(text.offsetByCodePoints(0, mid))
                    if (TextLayoutManager.measureWidth("…" + suffix, textSize, options) <= maxWidth) high = mid
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
                    if (TextLayoutManager.measureWidth(head + "…" + tail, textSize, options) <= maxWidth) low = mid
                    else high = mid - 1
                }
                val head = text.substring(0, text.offsetByCodePoints(0, low))
                val tailLen = text.codePointCount(0, text.length) - low
                val tail = text.substring(text.offsetByCodePoints(low, tailLen))
                head + "…" + tail
            }

            else -> TextLayoutManager.ellipsize(text, textSize, maxWidth, "…", options)
        }
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val lp = layoutParams
        if (baseText.text.isEmpty()) {
            displayText = Spanned.EMPTY
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

    /** 单行高度（sp 计），供 [minLines] 撑高。 */
    private fun minLinesHeight(): Float {
        val metrics = TextLayoutManager.lineMetrics(textSize)
        val asc = metrics?.ascent ?: textSize
        val desc = metrics?.descent ?: 0f
        val lead = metrics?.leading ?: 0f
        val base = asc + desc + (if (includeFontPadding) lead else 0f)
        return base * lineSpacingMultiplier + lineSpacingExtra
    }

    override fun renderInternal(context: Canvas) {
        super.renderInternal(context)
        val renderText = displayText
        if (renderText.text.isEmpty()) return

        val lp = layoutParams
        val baseTextWidth = getTextWidth(renderText)
        val baseTextHeight = getTextHeight(renderText)

        // 内容缩放 = 自动适配 layoutScale；控件自身缩放经 pose（CTM）一次施加。
        val contentScale = layoutScale
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom

        if (marqueeOverflow && availableWidth > 0f) {
            renderMarquee(context, renderText, baseTextWidth, baseTextHeight, contentScale, availableWidth, availableHeight)
            return
        }

        val visualTextWidth = baseTextWidth * contentScale
        val visualTextHeight = baseTextHeight * contentScale

        val (originX, originY) = TextPainter.blockOrigin(
            availableWidth, availableHeight, visualTextWidth, visualTextHeight,
            gravity, lp.paddingLeft, lp.paddingTop
        )

        // Record a device-independent blob; BatchProcessor expands it with the canvas CTM
        // (origin-aware subpixel phases, bitmap/MSDF selection, readiness pulled per frame).
        painter.draw(
            context, renderText, textSize, redF, greenF, blueF,
            shapingOptions(), originX, originY, contentScale,
            alpha * context.accumulatedAlpha, revealCodeUnits
        )
    }

    /**
     * AOSP marquee rendering: clip to the content box, draw the primary text shifted left by
     * the scroll offset, and when past the ghost start draw a second "ghost" copy offset by the
     * ghost offset so the tail re-enters seamlessly from the right. Horizontal fading edges are
     * recomputed per frame from the scroll position.
     */
    private fun renderMarquee(
        context: Canvas,
        renderText: Spanned,
        baseTextWidth: Float,
        baseTextHeight: Float,
        contentScale: Float,
        availableWidth: Float,
        availableHeight: Float
    ) {
        val lp = layoutParams
        val visualTextHeight = baseTextHeight * contentScale
        // 纵向按 gravity 对齐，水平强制贴左。
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

    // ---- Marquee 生命周期与每帧驱动（对标 AOSP Choreographer 回调）----

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
        const val DEFAULT_TEXT_SIZE: Float = 8f

        @JvmOverloads
        fun getTextWidth(text: String, textSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float {
            return TextLayoutManager.measureWidth(text, textSize, options)
        }

        @JvmOverloads
        fun getTextHeight(text: String, textSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): Float {
            return TextLayoutManager.measureHeight(text, textSize, options)
        }
    }
}