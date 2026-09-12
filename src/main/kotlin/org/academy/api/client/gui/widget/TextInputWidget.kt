package org.academy.api.client.gui.widget

import net.minecraft.client.input.PreeditEvent
import net.minecraft.util.ARGB
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.text.TextBlob
import org.academy.api.client.gui.text.TextEditCommand
import org.academy.api.client.gui.text.TextEditingState
import org.academy.api.client.gui.text.TextInputKeymap
import org.academy.api.client.gui.text.TextLayoutManager
import org.academy.api.client.gui.text.TextLine
import org.academy.api.client.gui.text.TextPainter
import org.academy.api.client.gui.text.TextShaper
import org.academy.api.client.gui.text.TextShapingOptions
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min

open class TextInputWidget(protected val maxLength: Int) : AbstractWidget(), TextHolder {
    private val editing = TextEditingState(maxLength)
    private val painter = TextPainter()
    private val hintPainter = TextPainter()

    /** 文本字号，sp。 */
    override var textSize: Float = TextWidget.DEFAULT_TEXT_SIZE
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

    /** 提示文本颜色，ARGB（对标 Android `textColorHint`）。 */
    var hintTextColor: Int = 0xFF808080.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 文本在 bounds 内的对齐。 */
    var gravity: Int = Gravity.TOP_LEFT
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** 当前显示文本（含 IME 预编辑）。 */
    override var text: String
        get() = editing.composedText
        set(value) {
            editing.setText(value)
            afterTextChanged()
        }

    /** 空内容时的提示文本（对标 Android `hint`，替代旧 `placeholder`）。 */
    var hint: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var allowLineBreak: Boolean
        get() = editing.allowLineBreak
        set(value) {
            editing.allowLineBreak = value
        }

    protected var whenEnter: Consumer<String>? = null
    protected var onTextChanged: Consumer<String>? = null
    protected var onFocusLostCallback: Runnable? = null
    protected var clearWhenEnter: Boolean = true

    private var showCaret = true
    private var lastBlinkTime = 0L
    private var mouseDragging = false
    private var dragStartPos = 0
    private var cachedLayout: TextBlob? = null
    private var cachedLayoutText: String? = null
    private var cachedLayoutFontSize = -1f

    init {
        isClickable = true

        editing.onCommittedTextChanged = { committed -> onTextChanged?.accept(committed) }

        val sld = StateListDrawable()
        sld.setDefault(ColorDrawable(0x5F1F1F1F))
        sld.addState(Widget.FOCUSED, ColorDrawable(0x5F5A5A5A))
        background = sld

        setFrameUpdate {
            if (isFocused) {
                val now = System.currentTimeMillis()
                if (now - lastBlinkTime >= 500) {
                    showCaret = !showCaret
                    invalidate()
                    lastBlinkTime = now
                }
            }
            true
        }
    }

    override fun renderInternal(context: Canvas) {
        super.renderInternal(context)
        renderText(context)
        if (text.isEmpty() && hint.isNotEmpty() && !isFocused) {
            renderHint(context)
        }
        if (editing.hasSelection) {
            renderSelection(context)
        }
        if (isFocused && showCaret) {
            renderCaret(context)
        }
    }

    private fun renderText(context: Canvas) {
        if (text.isEmpty()) return
        val blob = textLayout()
        val finalScale = 1f
        val (originX, originY) = textOrigin(blob.width, blob.height)
        painter.draw(
            context, text, textSize,
            ARGB.red(textColor) / 255.0f, ARGB.green(textColor) / 255.0f, ARGB.blue(textColor) / 255.0f,
            TextShapingOptions.DEFAULT,
            originX, originY, finalScale,
            alpha * context.accumulatedAlpha
        )
    }

    private fun renderHint(context: Canvas) {
        val finalScale = 1f
        val (originX, originY) = textOrigin(
            TextLayoutManager.measureWidth(hint, textSize),
            TextLayoutManager.measureHeight(hint, textSize)
        )
        hintPainter.draw(
            context, hint, textSize,
            ARGB.red(hintTextColor) / 255.0f, ARGB.green(hintTextColor) / 255.0f, ARGB.blue(hintTextColor) / 255.0f,
            TextShapingOptions.DEFAULT,
            originX, originY, finalScale, alpha * context.accumulatedAlpha
        )
    }

    private fun renderCaret(context: Canvas) {
        val blob = textLayout()
        val finalScale = 1f

        val empty = blob.lines.isEmpty()
        val metrics = if (empty) TextLayoutManager.lineMetrics(textSize) else null
        val caretUnit = editing.caretUnit + editing.preeditText.length
        // 换行后光标 == 上一行 charEnd 时应归属下一行（或最后一行兜底）。
        val line = blob.lines.firstOrNull { caretUnit < it.charEnd } ?: blob.lines.lastOrNull()

        val ascent = line?.ascent ?: metrics?.ascent ?: textSize
        val descent = line?.descent ?: metrics?.descent ?: 0f
        val blockHeight = if (empty) ascent + descent + (metrics?.leading ?: 0f) else blob.height
        val (originX, originY) = textOrigin(blob.width, blockHeight)

        val x = originX + (if (line != null) caretX(blob, line, caretUnit) else 0f) * finalScale
        val y = originY + (if (line != null) (line.baselineY - line.ascent) else 0f) * finalScale

        context.pose().pushPose()
        context.pose().translate(x, y)
        context.submit(
            FillRectDrawCommand(
                0.5f,
                (ascent + descent) * finalScale,
                1f,
                1f,
                1f,
                alpha * context.accumulatedAlpha
            )
        )
        context.pose().popPose()
    }

    private fun renderSelection(context: Canvas) {
        val start = min(editing.selectionStart, editing.selectionEnd)
        val end = max(editing.selectionStart, editing.selectionEnd)
        if (start >= end) return

        val blob = textLayout()
        if (blob.runs.isEmpty()) return

        val startUnit = codeUnitIndex(start)
        val endUnit = codeUnitIndex(end)
        val finalScale = 1f
        val (originX, originY) = textOrigin(blob.width, blob.height)

        for (line in blob.lines) {
            val overlapStart = max(startUnit, line.charStart)
            val overlapEnd = min(endUnit, line.charEnd)
            if (overlapStart >= overlapEnd) continue
            val x0 = caretX(blob, line, overlapStart)
            val x1 = caretX(blob, line, overlapEnd)
            context.pose().pushPose()
            context.pose().translate(
                originX + x0 * finalScale,
                originY + (line.baselineY - line.ascent) * finalScale
            )
            context.submit(
                FillRectDrawCommand(
                    (x1 - x0).coerceAtLeast(0f),
                    (line.ascent + line.descent) * finalScale,
                    0.3f, 0.5f, 0.8f,
                    alpha * context.accumulatedAlpha * 0.5f
                )
            )
            context.pose().popPose()
        }
    }

    override fun onCharTyped(event: CharTypedEvent) {
        editing.clearPreedit()
        if (!isFocused) return
        if (!editing.insertCodePoint(event.codePoint)) return
        afterTextChanged()
        event.consume()
    }

    override fun onKeyPressed(event: KeyEvent) {
        if (!isFocused) return

        val command = TextInputKeymap.resolve(event.keyCode, event.hasControlDownWithQuirk()) ?: return
        val extend = event.hasShiftDown()
        var textChanged = false

        when (command) {
            TextEditCommand.BACKSPACE -> textChanged = editing.backspace()
            TextEditCommand.DELETE -> textChanged = editing.delete()
            TextEditCommand.MOVE_LEFT -> editing.moveLeft(extend)
            TextEditCommand.MOVE_RIGHT -> editing.moveRight(extend)
            TextEditCommand.MOVE_HOME -> editing.moveHome(extend)
            TextEditCommand.MOVE_END -> editing.moveEnd(extend)

            TextEditCommand.NEWLINE -> {
                if (allowLineBreak) {
                    textChanged = editing.insertNewline()
                } else {
                    whenEnter?.accept(text)
                    if (clearWhenEnter) text = ""
                }
            }

            TextEditCommand.SELECT_ALL -> editing.selectAll()

            TextEditCommand.COPY -> {
                if (!editing.hasSelection) return
                copyToClipboard()
            }

            TextEditCommand.CUT -> {
                if (!editing.hasSelection) return
                copyToClipboard()
                editing.deleteSelectedText()
                textChanged = true
            }

            TextEditCommand.PASTE -> textChanged = pasteFromClipboard()
        }

        event.consume()
        if (textChanged) afterTextChanged() else invalidate()
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == 0 && isMouseOver(event.x, event.y)) {
            mouseDragging = true
            dragStartPos = getCaretPosAtMouse(event.x, event.y)
            editing.beginSelection(dragStartPos)

            showCaret = true
            lastBlinkTime = System.currentTimeMillis()
            event.consume()
            invalidate()
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (event.button == 0) {
            mouseDragging = false
            invalidate()
        }
    }

    override fun onMouseDragged(event: MouseEvent) {
        if (mouseDragging && event.button == 0) {
            val newCaretPos = getCaretPosAtMouse(event.x, event.y)
            editing.dragSelection(dragStartPos, newCaretPos)
            event.consume()
            invalidate()
        }
    }

    private fun getCaretPosAtMouse(mouseX: Double, mouseY: Double): Int {
        val blob = textLayout()
        if (blob.lines.isEmpty()) return 0
        val finalScale = 1f
        val (originX, originY) = textOrigin(blob.width, blob.height)

        val textY = ((mouseY - getAbsoluteY()).toFloat() - originY) / finalScale
        var target = blob.lines.first()
        var bestDist = Float.MAX_VALUE
        for (line in blob.lines) {
            val top = line.baselineY - line.ascent
            val bottom = line.baselineY + line.descent
            val dist = when {
                textY < top -> top - textY
                textY > bottom -> textY - bottom
                else -> 0f
            }
            if (dist < bestDist) {
                bestDist = dist
                target = line
            }
        }

        val textX = ((mouseX - getAbsoluteX()).toFloat() - originX) / finalScale
        val unitOffset = hitTestLine(blob, target, textX)
        // 命中用的是 composed blob（含 IME preedit），须映射回 committed 空间再交给编辑状态。
        val composedCp = blob.text.codePointCount(0, unitOffset)
        return mapComposedToCommitted(composedCp)
    }

    /** 把 composed（committed + preedit）空间的码点偏移映射为 committed 空间。 */
    private fun mapComposedToCommitted(composedCp: Int): Int {
        val preedit = editing.preeditText
        if (preedit.isEmpty()) return composedCp
        val caret = editing.caretPos
        val preeditLen = preedit.codePointCount(0, preedit.length)
        return when {
            composedCp <= caret -> composedCp
            composedCp >= caret + preeditLen -> composedCp - preeditLen
            else -> caret
        }
    }

    private fun textLayout(): TextBlob {
        val current = text
        var cached = cachedLayout
        if (cached == null || current != cachedLayoutText || textSize != cachedLayoutFontSize) {
            cached = TextShaper.shape(current, textSize)
            cachedLayoutText = current
            cachedLayoutFontSize = textSize
            cachedLayout = cached
        }
        return cached
    }

    /** Shared padding+gravity alignment; block dimensions are unscaled font px. */
    private fun textOrigin(blockWidth: Float, blockHeight: Float): Pair<Float, Float> {
        val lp = layoutParams
        val finalScale = 1f
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom
        return TextPainter.blockOrigin(
            availableWidth, availableHeight, blockWidth * finalScale, blockHeight * finalScale,
            gravity, lp.paddingLeft, lp.paddingTop
        )
    }

    /**
     * Caret x (AWT user units) for absolute code-unit [unit] within [line], taken from
     * the AWT glyph positions in [blob] so it is consistent with the rendered advance.
     */
    private fun caretX(blob: TextBlob, line: TextLine, unit: Int): Float {
        var prevEnd = 0f
        var nextPos = -1f
        var have = false
        for (run in blob.runs) {
            for (g in run.charIndices.indices) {
                val ci = run.charIndices[g]
                if (ci < line.charStart || ci >= line.charEnd) continue
                if (ci == unit) return run.positionsX[g]
                if (ci > unit && nextPos < 0f) nextPos = run.positionsX[g]
                prevEnd = run.positionsX[g] + run.advances[g]
                have = true
            }
        }
        return if (nextPos >= 0f) nextPos else if (have) prevEnd else 0f
    }

    /**
     * Hit-tests a local text-space x against the AWT glyph advance boundaries of [line],
     * returning the absolute code-unit offset.
     */
    private fun hitTestLine(blob: TextBlob, line: TextLine, localX: Float): Int {
        var best = line.charStart
        var bestDist = Float.MAX_VALUE
        var prevEnd = 0f
        var have = false
        for (run in blob.runs) {
            for (g in run.charIndices.indices) {
                val ci = run.charIndices[g]
                if (ci < line.charStart || ci >= line.charEnd) continue
                val pos = run.positionsX[g]
                val gapMid = if (have) (prevEnd + pos) / 2f else pos
                val dist = kotlin.math.abs(localX - gapMid)
                if (dist < bestDist) {
                    bestDist = dist
                    best = ci
                }
                prevEnd = pos + run.advances[g]
                have = true
            }
        }
        if (have && kotlin.math.abs(localX - prevEnd) < bestDist) {
            best = line.charEnd
        }
        return best
    }

    private fun codeUnitIndex(codePointIndex: Int): Int {
        val committed = editing.committedText
        if (codePointIndex <= 0) return 0
        return committed.offsetByCodePoints(
            0, min(codePointIndex, committed.codePointCount(0, committed.length))
        )
    }

    private fun copyToClipboard() {
        val selectedText = editing.selectedText
        if (selectedText.isNotEmpty()) {
            UiEnvironment.get().setClipboard(selectedText)
        }
    }

    private fun pasteFromClipboard(): Boolean {
        val clipboardText = UiEnvironment.get().clipboard()
        if (clipboardText.isEmpty()) return false
        return editing.insertString(clipboardText)
    }

    private fun afterTextChanged() {
        requestLayout()
        invalidate()
    }

    override fun canFocus(): Boolean = true

    override fun onFocusGained() {
        TextInputFocus.acquire(this)
        UiEnvironment.get().textInputFocusChanged(true)
        showCaret = true
        lastBlinkTime = System.currentTimeMillis()
    }

    override fun onFocusLost() {
        editing.clearPreedit()
        TextInputFocus.release(this)
        UiEnvironment.get().textInputFocusChanged(false)
        showCaret = false
        onFocusLostCallback?.run()
        afterTextChanged()
    }

    override fun onDetached() {
        super.onDetached()
        if (isFocused) isFocused = false
    }

    fun setWhenEnter(callback: Consumer<String>?): TextInputWidget {
        whenEnter = callback
        return this
    }

    /** Invoked after the committed text changes; IME preedit-only updates are ignored. */
    fun setOnTextChanged(callback: Consumer<String>?): TextInputWidget {
        onTextChanged = callback
        return this
    }

    fun setOnFocusLost(callback: Runnable?): TextInputWidget {
        onFocusLostCallback = callback
        return this
    }

    fun setClearWhenEnter(clear: Boolean): TextInputWidget {
        clearWhenEnter = clear
        return this
    }

    fun setInputValidator(validator: Predicate<String>?): TextInputWidget {
        editing.inputValidator = validator
        return this
    }

    fun setAllowLineBreak(allowLineBreak: Boolean): TextInputWidget {
        this.allowLineBreak = allowLineBreak
        return this
    }

    fun getTextMaxLength(): Int = maxLength

    internal fun updatePreedit(event: PreeditEvent?): Boolean {
        if (!isFocused) return false
        editing.updatePreedit(event?.fullText())
        afterTextChanged()
        return true
    }
}
