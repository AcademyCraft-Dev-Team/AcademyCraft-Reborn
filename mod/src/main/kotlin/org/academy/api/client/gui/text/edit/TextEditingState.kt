package org.academy.api.client.gui.text.edit

import net.minecraft.util.Mth
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TextEditingState(var maxLength: Int) {
    private val committed = StringBuilder()

    var caretPos: Int = 0
        private set
    var selectionStart: Int = 0
        private set
    var selectionEnd: Int = 0
        private set
    var hasSelection: Boolean = false
        private set
    var preeditText: String = ""
        private set

    var inputValidator: Predicate<String>? = null
    var allowLineBreak: Boolean = false

    var onCommittedTextChanged: ((String) -> Unit)? = null

    private var lastNotifiedText = ""

    val committedText: String get() = committed.toString()

    val composedText: String
        get() = if (preeditText.isEmpty()) committedText
        else StringBuilder(committed).insert(caretUnit, preeditText).toString()

    val codePointCount: Int get() = committed.codePointCount(0, committed.length)

    val caretUnit: Int get() = codeUnitIndex(caretPos)

    fun setText(text: String) {
        committed.setLength(0)
        val count = text.codePointCount(0, text.length)
        if (count > maxLength) {
            committed.append(text, 0, text.offsetByCodePoints(0, maxLength))
        } else {
            committed.append(text)
        }
        caretPos = committed.codePointCount(0, committed.length)
        clearSelection()
        notifyIfChanged()
    }

    fun selectAll() {
        selectionStart = 0
        selectionEnd = codePointCount
        caretPos = selectionEnd
        hasSelection = true
    }

    fun setCaret(position: Int) {
        caretPos = Mth.clamp(position, 0, codePointCount)
        clearSelection()
    }

    fun beginSelection(position: Int) {
        setCaret(position)
        selectionStart = caretPos
        selectionEnd = caretPos
    }

    fun dragSelection(anchor: Int, position: Int) {
        val a = Mth.clamp(anchor, 0, codePointCount)
        val p = Mth.clamp(position, 0, codePointCount)
        selectionStart = a
        selectionEnd = p
        hasSelection = a != p
        caretPos = p
    }

    fun clearSelection() {
        hasSelection = false
        selectionStart = 0
        selectionEnd = 0
    }

    fun deleteSelectedText() {
        if (!hasSelection) return
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        committed.delete(codeUnitIndex(start), codeUnitIndex(end))
        caretPos = start
        clearSelection()
        notifyIfChanged()
    }

    val selectedText: String
        get() {
            if (!hasSelection) return ""
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            return committed.substring(codeUnitIndex(start), codeUnitIndex(end))
        }

    fun insertCodePoint(codePoint: Int): Boolean {
        if (Character.isISOControl(codePoint)) return false
        if (!allowLineBreak && (codePoint == '\n'.code || codePoint == '\r'.code)) return false
        if (codePointCount - selectionSize() >= maxLength) return false
        return commitInsert(String(Character.toChars(codePoint)))
    }

    fun backspace(): Boolean {
        if (hasSelection) {
            deleteSelectedText()
            return true
        }
        if (caretPos <= 0) return false
        caretPos--
        val deleteIndex = codeUnitIndex(caretPos)
        val charCount = Character.charCount(committed.codePointAt(min(deleteIndex, committed.length - 1)))
        committed.delete(deleteIndex, deleteIndex + charCount)
        notifyIfChanged()
        return true
    }

    fun delete(): Boolean {
        if (hasSelection) {
            deleteSelectedText()
            return true
        }
        if (caretPos >= codePointCount) return false
        val deleteIndex = codeUnitIndex(caretPos)
        val charCount = Character.charCount(committed.codePointAt(min(deleteIndex, committed.length - 1)))
        committed.delete(deleteIndex, deleteIndex + charCount)
        notifyIfChanged()
        return true
    }

    fun moveLeft(extend: Boolean) {
        prepareMove(extend)
        if (caretPos > 0) {
            caretPos--
            if (extend) selectionEnd = caretPos
        }
    }

    fun moveRight(extend: Boolean) {
        prepareMove(extend)
        if (caretPos < codePointCount) {
            caretPos++
            if (extend) selectionEnd = caretPos
        }
    }

    fun moveHome(extend: Boolean) {
        prepareMove(extend)
        caretPos = 0
        if (extend) selectionEnd = caretPos
    }

    fun moveEnd(extend: Boolean) {
        prepareMove(extend)
        caretPos = codePointCount
        if (extend) selectionEnd = caretPos
    }

    fun insertNewline(): Boolean {
        if (!allowLineBreak) return false
        if (codePointCount - selectionSize() >= maxLength) return false
        return commitInsert("\n")
    }

    fun insertString(text: String): Boolean {
        if (text.isEmpty()) return false

        val remaining = maxLength - (codePointCount - selectionSize())
        if (remaining <= 0) return false

        var toInsert = text
        if (text.codePointCount(0, text.length) > remaining) {
            toInsert = text.substring(0, text.offsetByCodePoints(0, remaining))
        }
        if (!allowLineBreak) {
            toInsert = toInsert.replace("[\\r\\n]+".toRegex(), "")
        }
        if (toInsert.isEmpty()) return false
        return commitInsert(toInsert)
    }

    private fun commitInsert(insertText: String): Boolean {
        caretPos = Mth.clamp(caretPos, 0, codePointCount)
        val potential = buildPotential(insertText)
        if (potential.codePointCount(0, potential.length) > maxLength) return false
        if (inputValidator != null && !inputValidator!!.test(potential)) return false

        if (hasSelection) {
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            committed.delete(codeUnitIndex(start), codeUnitIndex(end))
            caretPos = start
        }
        val insertAt = codeUnitIndex(caretPos)
        committed.insert(insertAt, insertText)
        caretPos += insertText.codePointCount(0, insertText.length)
        clearSelection()
        notifyIfChanged()
        return true
    }

    private fun buildPotential(insertText: String): String {
        val sb = StringBuilder(committed)
        if (hasSelection) {
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            sb.delete(codeUnitIndex(start), codeUnitIndex(end))
        }
        val insertAt = if (hasSelection) codeUnitIndex(min(selectionStart, selectionEnd)) else caretUnit
        sb.insert(insertAt, insertText)
        return sb.toString()
    }

    private fun selectionSize(): Int =
        if (hasSelection) abs(selectionEnd - selectionStart) else 0

    fun updatePreedit(fullText: String?) {
        val remaining = maxLength - codePointCount
        preeditText = fullText?.takeCodePoints(remaining.coerceAtLeast(0)) ?: ""
    }

    fun clearPreedit() {
        preeditText = ""
    }

    private fun prepareMove(extend: Boolean) {
        if (!extend) {
            clearSelection()
        } else if (!hasSelection) {
            selectionStart = caretPos
            hasSelection = true
        }
    }

    private fun notifyIfChanged() {
        val current = committed.toString()
        if (current != lastNotifiedText) {
            lastNotifiedText = current
            onCommittedTextChanged?.invoke(current)
        }
    }

    private fun codeUnitIndex(codePointIndex: Int): Int {
        if (codePointIndex <= 0) return 0
        return committed.offsetByCodePoints(0, min(codePointIndex, codePointCount))
    }

    private fun String.takeCodePoints(count: Int): String {
        if (count <= 0 || isEmpty()) return ""
        val cpCount = codePointCount(0, length)
        if (cpCount <= count) return this
        return substring(0, offsetByCodePoints(0, count))
    }
}
