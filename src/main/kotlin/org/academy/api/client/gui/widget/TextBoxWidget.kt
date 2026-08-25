package org.academy.api.client.gui.widget

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.PreeditEvent
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.msdf.layout.MsdfTextProcessor
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.util.GlyphCommandGenerator
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min
import net.minecraft.util.Mth

open class TextBoxWidget(protected val maxLength: Int) : LabelWidget("") {
    protected val stringBuilder: StringBuilder = StringBuilder()
    protected var caretPos: Int = 0
    protected var selectionStart: Int = 0
    protected var selectionEnd: Int = 0
    protected var hasSelection: Boolean = false
    var allowLineBreak: Boolean = false
        protected set
    protected var whenEnter: Consumer<String>? = null
    protected var onFocusLostCallback: Runnable? = null
    protected var clearWhenEnter: Boolean = true
    protected var inputValidator: Predicate<String>? = null
    private var composedText: String = ""
    override var text: String
        get() = composedText
        set(text) {
            stringBuilder.setLength(0)
            val codePointCount = text.codePointCount(0, text.length)
            if (codePointCount > maxLength) {
                val endIndex = text.offsetByCodePoints(0, maxLength)
                stringBuilder.append(text, 0, endIndex)
            } else {
                stringBuilder.append(text)
            }
            caretPos = stringBuilder.codePointCount(0, stringBuilder.length)
            clearSelection()
            updateTextComponent()
        }
    private var showCaret = true
    private var lastBlinkTime = 0L
    private var mouseDragging = false
    private var dragStartPos = 0
    private var preeditText = ""

    /** Shown in gray when the box is empty and not focused. Not written back to the model. */
    var placeholder: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        isClickable = true

        val sld = StateListDrawable()
        sld.setDefault(ColorDrawable(0x5F1F1F1F))
        sld.addState(Widget.FOCUSED, ColorDrawable(0x5F5A5A5A))
        background = sld
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return

        context.drawOrder().push()
        run {
            super.render(context)
            if (text.isEmpty() && placeholder.isNotEmpty() && !isFocused) {
                context.drawOrder().advance()
                renderPlaceholder(context)
            }
            if (hasSelection) {
                context.drawOrder().advance()
                renderSelection(context)
            }
            if (isFocused && showCaret) {
                context.drawOrder().advance()
                renderCaret(context)
            }
        }
        context.drawOrder().pop()
    }

    private fun renderPlaceholder(context: RenderContext) {
        val lp = layoutParams
        val finalScale = scale * layoutScale
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom
        val placeholderWidth = getTextWidth(placeholder) * finalScale
        val placeholderHeight = getTextHeight(placeholder) * finalScale

        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX = (availableWidth - placeholderWidth) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX = availableWidth - placeholderWidth
        var alignmentOffsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - placeholderHeight) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY = availableHeight - placeholderHeight

        context.pose().pushPose()
        context.pose().translate(lp.paddingLeft + alignmentOffsetX, lp.paddingTop + alignmentOffsetY)
        context.pose().scale(finalScale, finalScale)
        val commands = GlyphCommandGenerator.generate(
            placeholder, baseFontSize, 0f, 0.5f, 0.5f, 0.5f, alpha * context.accumulatedAlpha
        )
        for (command in commands) context.submit(command)
        context.pose().popPose()
    }

    private fun renderCaret(context: RenderContext) {
        val lp = layoutParams
        val finalScale = layoutScale * scale
        val textBeforeCaret = stringBuilder.substring(0, getCodeUnitIndexForCodePoint(caretPos)) + preeditText
        val lastNewline = textBeforeCaret.lastIndexOf('\n')
        val caretLineText = if (lastNewline >= 0) textBeforeCaret.substring(lastNewline + 1) else textBeforeCaret
        val lineIndex = textBeforeCaret.count { it == '\n' }
        val lineAdvance = lineAdvancePx()
        val availableHeight = height - lp.paddingTop - lp.paddingBottom
        val availableWidth = width - lp.paddingLeft - lp.paddingRight

        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX =
            (availableWidth - getTextWidth(composedText) * finalScale) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX =
            availableWidth - getTextWidth(composedText) * finalScale
        var alignmentOffsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY =
            (availableHeight - getTextHeight(composedText) * finalScale) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY =
            availableHeight - getTextHeight(composedText) * finalScale

        val caretXOffset = lineXAt(caretLineText, caretLineText.length) * finalScale
        val finalX = lp.paddingLeft + alignmentOffsetX + caretXOffset
        val finalY = lp.paddingTop + alignmentOffsetY + lineIndex * lineAdvance * finalScale

        context.pose().pushPose()
        context.pose().translate(finalX, finalY)
        context.submit(
            FillRectDrawCommand(
                0.5f,
                lineAdvance * finalScale,
                1f,
                1f,
                1f,
                alpha * context.accumulatedAlpha
            )
        )
        context.pose().popPose()
    }

    private fun renderSelection(context: RenderContext) {
        val lp = layoutParams
        val finalScale = layoutScale * scale
        val fullText = composedText

        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)

        if (start >= end) return

        val startUnit = getCodeUnitIndexForCodePoint(start)
        val endUnit = getCodeUnitIndexForCodePoint(end)

        val lines = fullText.split('\n')
        val lineAdvance = lineAdvancePx()
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom

        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX =
            (availableWidth - getTextWidth(fullText) * finalScale) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX =
            availableWidth - getTextWidth(fullText) * finalScale
        var alignmentOffsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY =
            (availableHeight - getTextHeight(fullText) * finalScale) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY =
            availableHeight - getTextHeight(fullText) * finalScale

        val baseX = lp.paddingLeft + alignmentOffsetX
        val baseY = lp.paddingTop + alignmentOffsetY

        // Draw a highlight rect per overlapped line (code-unit range within each line).
        var lineStartUnit = 0
        for ((index, line) in lines.withIndex()) {
            val lineEndUnit = lineStartUnit + line.length
            val overlapStart = max(startUnit, lineStartUnit)
            val overlapEnd = min(endUnit, lineEndUnit)
            if (overlapStart < overlapEnd) {
                val x0 = lineXAt(line, overlapStart - lineStartUnit) * finalScale
                val x1 = lineXAt(line, overlapEnd - lineStartUnit) * finalScale
                val y = baseY + index * lineAdvance * finalScale
                context.pose().pushPose()
                context.pose().translate(baseX + x0, y)
                context.submit(
                    FillRectDrawCommand(
                        (x1 - x0).coerceAtLeast(0f),
                        lineAdvance * finalScale,
                        0.3f, 0.5f, 0.8f,
                        alpha * context.accumulatedAlpha * 0.5f
                    )
                )
                context.pose().popPose()
            }
            lineStartUnit = lineEndUnit + 1
        }
    }

    override fun onCharTyped(event: CharTypedEvent) {
        clearPreedit()
        if (!isFocused || stringBuilder.codePointCount(
                0,
                stringBuilder.length
            ) >= maxLength || Character.isISOControl(event.codePoint)
        ) return

        if (!allowLineBreak && (event.codePoint == '\n'.code || event.codePoint == '\r'.code)) {
            return
        }

        caretPos = Mth.clamp(caretPos, 0, stringBuilder.codePointCount(0, stringBuilder.length))

        if (hasSelection) {
            deleteSelectedText()
        }

        val potentialText = StringBuilder(stringBuilder).insert(
            getCodeUnitIndexForCodePoint(caretPos),
            Character.toChars(event.codePoint)
        ).toString()
        if (inputValidator == null || inputValidator!!.test(potentialText)) {
            stringBuilder.insert(getCodeUnitIndexForCodePoint(caretPos), Character.toChars(event.codePoint))
            caretPos++
            clearSelection()
            updateTextComponent()
            event.consume()
        }
    }

    override fun onKeyPressed(event: KeyEvent) {
        if (!isFocused) return

        val handled = when (event.keyCode) {
            InputConstants.KEY_BACKSPACE -> {
                if (hasSelection) {
                    deleteSelectedText()
                } else if (caretPos > 0) {
                    caretPos--
                    val deleteIndex = getCodeUnitIndexForCodePoint(caretPos)
                    val charCount =
                        Character.charCount(stringBuilder.codePointAt(min(deleteIndex, stringBuilder.length - 1)))
                    stringBuilder.delete(deleteIndex, deleteIndex + charCount)
                    updateTextComponent()
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                if (hasSelection) {
                    deleteSelectedText()
                } else if (caretPos < stringBuilder.codePointCount(0, stringBuilder.length)) {
                    val deleteIndex = getCodeUnitIndexForCodePoint(caretPos)
                    val charCount =
                        Character.charCount(stringBuilder.codePointAt(min(deleteIndex, stringBuilder.length - 1)))
                    stringBuilder.delete(deleteIndex, deleteIndex + charCount)
                    updateTextComponent()
                }
                true
            }

            InputConstants.KEY_RIGHT -> {
                val extend = event.hasShiftDown()
                if (!extend) {
                    clearSelection()
                } else if (!hasSelection) {
                    selectionStart = caretPos
                    hasSelection = true
                }

                if (caretPos < stringBuilder.codePointCount(0, stringBuilder.length)) {
                    caretPos++
                    if (extend) {
                        selectionEnd = caretPos
                    }
                }
                true
            }

            InputConstants.KEY_LEFT -> {
                val extend = event.hasShiftDown()
                if (!extend) {
                    clearSelection()
                } else if (!hasSelection) {
                    selectionStart = caretPos
                    hasSelection = true
                }

                if (caretPos > 0) {
                    caretPos--
                    if (extend) {
                        selectionEnd = caretPos
                    }
                }
                true
            }

            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (allowLineBreak) {
                    if (hasSelection) {
                        deleteSelectedText()
                    }
                    val potentialText =
                        java.lang.StringBuilder(stringBuilder).insert(getCodeUnitIndexForCodePoint(caretPos), '\n')
                            .toString()
                    if (inputValidator == null || inputValidator!!.test(potentialText)) {
                        stringBuilder.insert(getCodeUnitIndexForCodePoint(caretPos), '\n')
                        caretPos++
                        clearSelection()
                        updateTextComponent()
                    }
                } else {
                    if (whenEnter != null) whenEnter!!.accept(text)
                    if (clearWhenEnter) text = ""
                }
                true
            }

            InputConstants.KEY_END -> {
                val extend = event.hasShiftDown()
                if (!extend) {
                    clearSelection()
                } else if (!hasSelection) {
                    selectionStart = caretPos
                    hasSelection = true
                }

                caretPos = stringBuilder.codePointCount(0, stringBuilder.length)
                if (extend) {
                    selectionEnd = caretPos
                }
                true
            }

            InputConstants.KEY_HOME -> {
                val extend = event.hasShiftDown()
                if (!extend) {
                    clearSelection()
                } else if (!hasSelection) {
                    selectionStart = caretPos
                    hasSelection = true
                }

                caretPos = 0
                if (extend) {
                    selectionEnd = caretPos
                }
                true
            }

            InputConstants.KEY_A -> {
                if (event.hasControlDownWithQuirk()) {
                    selectAll()
                    true
                } else false
            }

            InputConstants.KEY_C -> {
                if (event.hasControlDownWithQuirk() && hasSelection) {
                    copyToClipboard()
                    true
                } else false
            }

            InputConstants.KEY_V -> {
                if (event.hasControlDownWithQuirk()) {
                    pasteFromClipboard()
                    true
                } else false
            }

            InputConstants.KEY_X -> {
                if (event.hasControlDownWithQuirk() && hasSelection) {
                    cutToClipboard()
                    true
                } else false
            }

            else -> false
        }
        if (handled) {
            event.consume()
            invalidate()
        }
    }

    private fun updateTextComponent() {
        if (preeditText.isEmpty() || !isFocused) {
            composedText = stringBuilder.toString()
            super.text = composedText
            return
        }
        composedText = StringBuilder(stringBuilder)
            .insert(getCodeUnitIndexForCodePoint(caretPos), preeditText)
            .toString()
        super.text = composedText
    }

    private fun updatePreedit(event: PreeditEvent?): Boolean {
        if (!isFocused) return false
        val remainingCapacity = maxLength - stringBuilder.codePointCount(0, stringBuilder.length)
        preeditText = event?.fullText()?.takeCodePoints(remainingCapacity.coerceAtLeast(0)) ?: ""
        updateTextComponent()
        invalidate()
        return true
    }

    private fun clearPreedit() {
        if (preeditText.isEmpty()) return
        preeditText = ""
        updateTextComponent()
        invalidate()
    }

    override fun tick() {
        if (!isFocused) return
        val now = System.currentTimeMillis()
        if (now - lastBlinkTime >= 500) {
            showCaret = !showCaret
            invalidate()
            lastBlinkTime = now
        }
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == 0 && isMouseOver(event.x, event.y)) {
            mouseDragging = true
            dragStartPos = getCaretPosAtMouse(event.x, event.y)
            caretPos = dragStartPos
            selectionStart = dragStartPos
            selectionEnd = dragStartPos
            hasSelection = false

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

            if (!hasSelection && newCaretPos != dragStartPos) {
                hasSelection = true
            }

            if (hasSelection) {
                selectionStart = dragStartPos
                selectionEnd = newCaretPos
            }

            caretPos = newCaretPos
            event.consume()
            invalidate()
        }
    }

    private fun getCaretPosAtMouse(mouseX: Double, mouseY: Double): Int {
        val lp = layoutParams
        val finalScale = scale * layoutScale
        val fullText = composedText
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom

        var alignmentOffsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY =
            (availableHeight - getTextHeight(fullText) * finalScale) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY =
            availableHeight - getTextHeight(fullText) * finalScale

        val lineAdvance = lineAdvancePx()
        val localY = (mouseY - getAbsoluteY() - lp.paddingTop - alignmentOffsetY) / finalScale
        val lines = fullText.split('\n')
        var lineIndex = (localY / lineAdvance).toInt()
        lineIndex = lineIndex.coerceIn(0, lines.size - 1)

        var lineStartUnit = 0
        for (k in 0 until lineIndex) lineStartUnit += lines[k].length + 1

        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX =
            (availableWidth - getTextWidth(fullText) * finalScale) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX =
            availableWidth - getTextWidth(fullText) * finalScale

        val lineText = lines[lineIndex]
        val localX = mouseX.toFloat() - getAbsoluteX() - lp.paddingLeft - alignmentOffsetX
        var caretInLine = caretCodePointsInLine(lineText, localX / finalScale)
        val unitOffset = lineStartUnit + lineText.offsetByCodePoints(0, caretInLine)
        return fullText.codePointCount(0, unitOffset)
    }

    /**
     * Finds the caret (code point index within [lineText]) for a local X position.
     * The click is assigned to the closest kerning-adjusted caret stop from the
     * same layout used for rendering.
     */
    private fun caretCodePointsInLine(lineText: String, localX: Float): Int {
        if (lineText.isEmpty()) return 0
        val result = MsdfTextProcessor.layout(lineText, baseFontSize)
        var best = 0
        var bestDist = kotlin.math.abs(localX)
        for (instance in result.instances) {
            val dist = kotlin.math.abs(localX - instance.penX)
            if (dist < bestDist) {
                bestDist = dist
                best = lineText.codePointCount(0, instance.glyphIndex)
            }
        }
        if (kotlin.math.abs(localX - result.width) < bestDist) {
            best = lineText.codePointCount(0, lineText.length)
        }
        return best
    }

    /**
     * Returns the caret stop used by the same shaped layout that renders [line]. For an
     * interior stop this is the next glyph's kerning-adjusted pen; for the final stop it is
     * the rendered line edge. Keeping these metrics shared prevents GUI scaling from
     * magnifying the small advance/kerning discrepancy into a visibly displaced caret.
     */
    private fun lineXAt(line: String, unit: Int): Float {
        if (unit <= 0 || line.isEmpty()) return 0f
        val result = MsdfTextProcessor.layout(line, baseFontSize)
        for (instance in result.instances) {
            if (instance.glyphIndex >= unit) return instance.penX
        }
        return result.width
    }

    private fun lineAdvancePx(): Float {
        val single = getTextHeight("M", baseFontSize)
        val double = getTextHeight("M\nM", baseFontSize)
        return (double - single).coerceAtLeast(baseFontSize * 0.5f)
    }

    private fun getCodeUnitIndexForCodePoint(codePointIndex: Int): Int {
        if (codePointIndex <= 0) return 0
        return stringBuilder.offsetByCodePoints(
            0, min(
                codePointIndex,
                stringBuilder.codePointCount(0, stringBuilder.length)
            )
        )
    }

    private fun clearSelection() {
        hasSelection = false
        selectionStart = 0
        selectionEnd = 0
    }

    private fun deleteSelectedText() {
        if (!hasSelection) return

        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)

        val startIndex = getCodeUnitIndexForCodePoint(start)
        val endIndex = getCodeUnitIndexForCodePoint(end)

        stringBuilder.delete(startIndex, endIndex)
        caretPos = start
        clearSelection()
        updateTextComponent()
    }

    private val selectedText: String
        get() {
            if (!hasSelection) return ""

            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)

            val startIndex = getCodeUnitIndexForCodePoint(start)
            val endIndex = getCodeUnitIndexForCodePoint(end)

            return stringBuilder.substring(startIndex, endIndex)
        }

    private fun selectAll() {
        selectionStart = 0
        selectionEnd = stringBuilder.codePointCount(0, stringBuilder.length)
        caretPos = selectionEnd
        hasSelection = true
    }

    private fun copyToClipboard() {
        val selectedText = this.selectedText
        if (!selectedText.isEmpty()) {
            UiEnvironment.get().setClipboard(selectedText)
        }
    }

    private fun pasteFromClipboard() {
        val clipboardText = UiEnvironment.get().clipboard()
        if (!clipboardText.isEmpty()) {
            if (hasSelection) {
                deleteSelectedText()
            }

            val remainingCapacity = maxLength - stringBuilder.codePointCount(0, stringBuilder.length)
            if (remainingCapacity <= 0) return

            var textToInsert = clipboardText
            val textCodePoints = clipboardText.codePointCount(0, clipboardText.length)
            if (textCodePoints > remainingCapacity) {
                val endIndex = clipboardText.offsetByCodePoints(0, remainingCapacity)
                textToInsert = clipboardText.substring(0, endIndex)
            }

            if (!allowLineBreak) {
                textToInsert = textToInsert.replace("[\\r\\n]+".toRegex(), "")
            }

            val potentialText =
                StringBuilder(stringBuilder).insert(getCodeUnitIndexForCodePoint(caretPos), textToInsert).toString()
            if (inputValidator == null || inputValidator!!.test(potentialText)) {
                stringBuilder.insert(getCodeUnitIndexForCodePoint(caretPos), textToInsert)
                caretPos += textToInsert.codePointCount(0, textToInsert.length)
                clearSelection()
                updateTextComponent()
            }
        }
    }

    private fun cutToClipboard() {
        copyToClipboard()
        deleteSelectedText()
    }

    override fun canFocus(): Boolean {
        return true
    }

    override fun onFocusGained() {
        val event = FocusGainedEvent(this)
        NeoForge.EVENT_BUS.post<FocusGainedEvent>(event)
        if (event.isCanceled()) return

        activeTextBox = this
        UiEnvironment.get().textInputFocusChanged(true)
        showCaret = true
        lastBlinkTime = System.currentTimeMillis()
    }

    override fun onFocusLost() {
        val event = FocusLostEvent(this)
        NeoForge.EVENT_BUS.post<FocusLostEvent>(event)
        if (event.isCanceled()) return

        clearPreedit()
        if (activeTextBox === this) activeTextBox = null
        UiEnvironment.get().textInputFocusChanged(false)
        showCaret = false
        if (onFocusLostCallback != null) onFocusLostCallback!!.run()
    }

    fun setWhenEnter(callback: Consumer<String>?): TextBoxWidget {
        whenEnter = callback
        return this
    }

    fun setOnFocusLost(callback: Runnable?): TextBoxWidget {
        onFocusLostCallback = callback
        return this
    }

    fun setClearWhenEnter(clear: Boolean): TextBoxWidget {
        clearWhenEnter = clear
        return this
    }

    fun setInputValidator(validator: Predicate<String>?): TextBoxWidget {
        inputValidator = validator
        return this
    }

    fun setAllowLineBreak(allowLineBreak: Boolean): TextBoxWidget {
        this.allowLineBreak = allowLineBreak
        return this
    }

    fun getTextMaxLength(): Int = maxLength

    class FocusGainedEvent(val textBoxWidget: TextBoxWidget) : Event(), ICancellableEvent

    class FocusLostEvent(val textBoxWidget: TextBoxWidget) : Event(), ICancellableEvent

    companion object {
        @Volatile
        private var activeTextBox: TextBoxWidget? = null

        fun hasActiveTextInput(): Boolean = activeTextBox?.isFocused == true

        fun handlePreeditInput(event: PreeditEvent?): Boolean {
            return activeTextBox?.updatePreedit(event) == true
        }

        /** True while any [TextBoxWidget] holds keyboard focus (IME/text editing active). */
        fun isAnyTextEditing(): Boolean = activeTextBox != null

        private fun String.takeCodePoints(count: Int): String {
            if (count <= 0 || isEmpty()) return ""
            val codePointCount = codePointCount(0, length)
            if (codePointCount <= count) return this
            return substring(0, offsetByCodePoints(0, count))
        }
    }
}
