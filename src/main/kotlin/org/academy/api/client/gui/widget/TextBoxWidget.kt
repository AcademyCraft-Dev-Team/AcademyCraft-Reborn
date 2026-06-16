package org.academy.api.client.gui.widget

import net.minecraft.client.Minecraft
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.render.RenderContext
import org.lwjgl.glfw.GLFW
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
    override var text: String
        get() = stringBuilder.toString()
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

    private fun renderCaret(context: RenderContext) {
        val lp = layoutParams
        val finalScale = layoutScale * scale
        val textBeforeCaret = stringBuilder.substring(0, getCodeUnitIndexForCodePoint(caretPos))
        val caretXOffset = getTextWidth(textBeforeCaret) * finalScale
        val availableHeight = height - lp.paddingTop - lp.paddingBottom
        val visualTextHeight = availableHeight * finalScale
        val availableWidth = width - lp.paddingLeft - lp.paddingRight

        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX =
            (availableWidth - getTextWidth(stringBuilder.toString()) * finalScale) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX =
            availableWidth - getTextWidth(stringBuilder.toString()) * finalScale
        var alignmentOffsetY = 0f
        val verticalGravity = (lp.gravity shr Gravity.AXIS_Y_SHIFT) and 0x7
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY =
            availableHeight - visualTextHeight

        val finalX = lp.paddingLeft + alignmentOffsetX + caretXOffset
        val finalY = lp.paddingTop + alignmentOffsetY

        context.pose().pushPose()
        context.pose().translate(finalX, finalY)
        context.submit(
            FillRectDrawCommand(
                0.5f,
                visualTextHeight,
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
        val fullText = stringBuilder.toString()

        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)

        if (start >= end) return

        val textBeforeStart = fullText.substring(0, getCodeUnitIndexForCodePoint(start))
        val selectedText = fullText.substring(
            getCodeUnitIndexForCodePoint(start),
            getCodeUnitIndexForCodePoint(end)
        )

        val startXOffset = getTextWidth(textBeforeStart) * finalScale + 0.5f
        val selectionWidth = getTextWidth(selectedText) * finalScale
        val visualTextHeight = getTextHeight(fullText) * finalScale

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
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f
        else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetY =
            availableHeight - visualTextHeight

        val finalX = lp.paddingLeft + alignmentOffsetX + startXOffset
        val finalY = lp.paddingTop + alignmentOffsetY

        context.pose().pushPose()
        context.pose().translate(finalX, finalY)
        context.submit(
            FillRectDrawCommand(
                selectionWidth,
                visualTextHeight,
                0.3f,
                0.5f,
                0.8f,
                alpha * context.accumulatedAlpha * 0.5f
            )
        )
        context.pose().popPose()
    }

    override fun onCharTyped(event: CharTypedEvent) {
        if (!isFocused || stringBuilder.codePointCount(
                0,
                stringBuilder.length
            ) >= maxLength || Character.isISOControl(event.codePoint)
        ) return

        if (!allowLineBreak && (event.codePoint == '\n'.code || event.codePoint == '\r'.code)) {
            return
        }

        caretPos = Math.clamp(caretPos.toLong(), 0, stringBuilder.codePointCount(0, stringBuilder.length))

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
            GLFW.GLFW_KEY_BACKSPACE -> {
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

            GLFW.GLFW_KEY_DELETE -> {
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

            GLFW.GLFW_KEY_RIGHT -> {
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

            GLFW.GLFW_KEY_LEFT -> {
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

            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
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

            GLFW.GLFW_KEY_END -> {
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

            GLFW.GLFW_KEY_HOME -> {
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

            GLFW.GLFW_KEY_A -> {
                if (event.hasControlDownWithQuirk()) {
                    selectAll()
                    true
                } else false
            }

            GLFW.GLFW_KEY_C -> {
                if (event.hasControlDownWithQuirk() && hasSelection) {
                    copyToClipboard()
                    true
                } else false
            }

            GLFW.GLFW_KEY_V -> {
                if (event.hasControlDownWithQuirk()) {
                    pasteFromClipboard()
                    true
                } else false
            }

            GLFW.GLFW_KEY_X -> {
                if (event.hasControlDownWithQuirk() && hasSelection) {
                    cutToClipboard()
                    true
                } else false
            }

            else -> false
        }
        if (handled) event.consume()
    }

    private fun updateTextComponent() {
        super.text = stringBuilder.toString()
    }

    override fun tick() {
        if (!isFocused) return
        val now = System.currentTimeMillis()
        if (now - lastBlinkTime >= 500) {
            showCaret = !showCaret
            lastBlinkTime = now
        }
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == 0 && isMouseOver(event.x, event.y)) {
            mouseDragging = true
            dragStartPos = getCaretPosAtMouse(event.x)
            caretPos = dragStartPos
            selectionStart = dragStartPos
            selectionEnd = dragStartPos
            hasSelection = false

            showCaret = true
            lastBlinkTime = System.currentTimeMillis()
            event.consume()
        }
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (event.button == 0) {
            mouseDragging = false
        }
    }

    override fun onMouseDragged(event: MouseEvent) {
        if (mouseDragging && event.button == 0) {
            val newCaretPos = getCaretPosAtMouse(event.x)

            if (!hasSelection && newCaretPos != dragStartPos) {
                hasSelection = true
            }

            if (hasSelection) {
                selectionStart = dragStartPos
                selectionEnd = newCaretPos
            }

            caretPos = newCaretPos
            event.consume()
        }
    }

    private fun getCaretPosAtMouse(mouseX: Double): Int {
        val lp = layoutParams
        val finalScale = scale * layoutScale
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val visualTextWidth = getTextWidth(stringBuilder.toString()) * finalScale
        var alignmentOffsetX = 0f
        val horizontalGravity = (lp.gravity shr Gravity.AXIS_X_SHIFT) and 0x7
        if (horizontalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f
        else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) alignmentOffsetX =
            availableWidth - visualTextWidth
        val localX = mouseX.toFloat() - getAbsoluteX() - lp.paddingLeft - alignmentOffsetX
        val fullText = stringBuilder.toString()
        var caretPos = 0
        val codePointCount = fullText.codePointCount(0, fullText.length)
        for (i in 1..codePointCount) {
            val codeUnitIndex = fullText.offsetByCodePoints(0, i)
            if (getTextWidth(fullText.substring(0, codeUnitIndex)) * finalScale > localX) break
            caretPos = i
        }
        return caretPos
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
            Minecraft.getInstance().keyboardHandler.clipboard = selectedText
        }
    }

    private fun pasteFromClipboard() {
        val clipboardText = Minecraft.getInstance().keyboardHandler.clipboard
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

        Minecraft.getInstance().textInputManager().onTextInputFocusChange(true)
        showCaret = true
        lastBlinkTime = System.currentTimeMillis()
    }

    override fun onFocusLost() {
        val event = FocusLostEvent(this)
        NeoForge.EVENT_BUS.post<FocusLostEvent>(event)
        if (event.isCanceled()) return

        Minecraft.getInstance().textInputManager().onTextInputFocusChange(false)
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

    class FocusGainedEvent(val textBoxWidget: TextBoxWidget) : Event(), ICancellableEvent

    class FocusLostEvent(val textBoxWidget: TextBoxWidget) : Event(), ICancellableEvent
}