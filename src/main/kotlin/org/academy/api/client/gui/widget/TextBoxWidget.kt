package org.academy.api.client.gui.widget

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.PreeditEvent
import net.minecraft.util.Mth
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
    private var cachedLayout: MsdfTextProcessor.LayoutResult? = null
    private var cachedLayoutText: String? = null
    private var cachedLayoutFontSize = -1f

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
        val finalScale = scale * layoutScale
        val (originX, originY) = textOrigin(getTextWidth(placeholder), getTextHeight(placeholder))

        context.pose().pushPose()
        context.pose().translate(originX, originY)
        context.pose().scale(finalScale, finalScale)
        val commands = GlyphCommandGenerator.generate(
            placeholder, baseFontSize, 0f, 0.5f, 0.5f, 0.5f, alpha * context.accumulatedAlpha
        )
        for (command in commands) context.submit(command)
        context.pose().popPose()
    }

    private fun renderCaret(context: RenderContext) {
        val layout = textLayout()
        val finalScale = scale * layoutScale
        val empty = layout.instances.isEmpty()
        val (originX, originY) = textOrigin(
            if (empty) 0f else layout.width,
            if (empty) baseFontSize else layout.height
        )

        val caretUnit = getCodeUnitIndexForCodePoint(caretPos) + preeditText.length
        val line = layout.lines.firstOrNull { caretUnit <= it.codeUnitEnd } ?: layout.lines.last()
        val caretX = originX + penRightAt(layout, line, caretUnit) * finalScale
        val caretY = originY + line.bandTop * finalScale

        context.pose().pushPose()
        context.pose().translate(caretX, caretY)
        context.submit(
            FillRectDrawCommand(
                0.5f,
                baseFontSize * finalScale,
                1f,
                1f,
                1f,
                alpha * context.accumulatedAlpha
            )
        )
        context.pose().popPose()
    }

    private fun renderSelection(context: RenderContext) {
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        if (start >= end) return

        val layout = textLayout()
        if (layout.instances.isEmpty()) return

        val startUnit = getCodeUnitIndexForCodePoint(start)
        val endUnit = getCodeUnitIndexForCodePoint(end)
        val finalScale = scale * layoutScale
        val (originX, originY) = textOrigin(layout.width, layout.height)
        val stripHeight = baseFontSize * finalScale

        for (line in layout.lines) {
            val overlapStart = max(startUnit, line.codeUnitStart)
            val overlapEnd = min(endUnit, line.codeUnitEnd)
            if (overlapStart >= overlapEnd) continue
            val x0 = penRightAt(layout, line, overlapStart)
            val x1 = penRightAt(layout, line, overlapEnd)
            context.pose().pushPose()
            context.pose().translate(originX + x0 * finalScale, originY + line.bandTop * finalScale)
            context.submit(
                FillRectDrawCommand(
                    (x1 - x0).coerceAtLeast(0f),
                    stripHeight,
                    0.3f, 0.5f, 0.8f,
                    alpha * context.accumulatedAlpha * 0.5f
                )
            )
            context.pose().popPose()
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
        val layout = textLayout()
        if (layout.instances.isEmpty()) return 0
        val fullText = composedText
        val finalScale = scale * layoutScale
        val (originX, originY) = textOrigin(layout.width, layout.height)

        val textY = ((mouseY - getAbsoluteY()).toFloat() - originY) / finalScale
        var target = layout.lines.first()
        var bestDist = Float.MAX_VALUE
        for (line in layout.lines) {
            val dist = when {
                textY < line.bandTop -> line.bandTop - textY
                textY > line.bandBottom -> textY - line.bandBottom
                else -> 0f
            }
            if (dist < bestDist) {
                bestDist = dist
                target = line
            }
        }

        val textX = ((mouseX - getAbsoluteX()).toFloat() - originX) / finalScale
        val unitInLine = caretCodeUnitsInLine(layout, target, textX)
        val unitOffset = target.codeUnitStart + unitInLine
        return fullText.codePointCount(0, unitOffset)
    }

    private fun textLayout(): MsdfTextProcessor.LayoutResult {
        val text = composedText
        var cached = cachedLayout
        if (cached == null || text !== cachedLayoutText || baseFontSize != cachedLayoutFontSize) {
            cached = MsdfTextProcessor.layout(text, baseFontSize)
            cachedLayoutText = text
            cachedLayoutFontSize = baseFontSize
            cachedLayout = cached
        }
        return cached
    }

    /** Shared padding+gravity alignment; block dimensions are unscaled font px. */
    private fun textOrigin(blockWidth: Float, blockHeight: Float): Pair<Float, Float> {
        val lp = layoutParams
        val finalScale = scale * layoutScale
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val availableHeight = height - lp.paddingTop - lp.paddingBottom

        var offsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) offsetX =
            (availableWidth - blockWidth * finalScale) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) offsetX =
            availableWidth - blockWidth * finalScale

        var offsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) offsetY =
            (availableHeight - blockHeight * finalScale) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) offsetY =
            availableHeight - blockHeight * finalScale

        return Pair(lp.paddingLeft + offsetX, lp.paddingTop + offsetY)
    }

    /** Max advance edge within [line] up to absolute code-unit index [unit]. */
    private fun penRightAt(
        layout: MsdfTextProcessor.LayoutResult,
        line: MsdfTextProcessor.LineLayout,
        unit: Int
    ): Float {
        val end = min(unit, line.codeUnitEnd)
        var x = 0f
        for (instance in layout.instances) {
            if (instance.glyphIndex < line.codeUnitStart) continue
            if (instance.glyphIndex >= end) break
            val right = instance.penX + instance.advance
            if (right > x) x = right
        }
        return x
    }

    /**
     * Finds the caret (code-unit offset within [line]) for a text-space X position.
     * The caret sits between glyph advance boundaries: for each glyph we compare
     * the click against the midpoint of the gap to its predecessor.
     */
    private fun caretCodeUnitsInLine(
        layout: MsdfTextProcessor.LayoutResult,
        line: MsdfTextProcessor.LineLayout,
        localX: Float
    ): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        var prevPen = 0f
        for (instance in layout.instances) {
            if (instance.glyphIndex < line.codeUnitStart) continue
            if (instance.glyphIndex >= line.codeUnitEnd) break
            val gapMid = (prevPen + instance.penX) / 2f
            val dist = kotlin.math.abs(localX - gapMid)
            if (dist < bestDist) {
                bestDist = dist
                best = instance.glyphIndex - line.codeUnitStart
            }
            prevPen = instance.penX + instance.advance
        }
        val endMid = (prevPen + penRightAt(layout, line, line.codeUnitEnd)) / 2f
        if (kotlin.math.abs(localX - endMid) < bestDist) {
            best = line.codeUnitEnd - line.codeUnitStart
        }
        return best
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
