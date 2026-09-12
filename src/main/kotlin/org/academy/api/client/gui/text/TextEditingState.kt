package org.academy.api.client.gui.text

import net.minecraft.util.Mth
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 文本编辑器的设备无关状态：提交文本、预编辑（IME）、caret、选区与输入约束。
 *
 * 纯逻辑、无渲染、无控件依赖，便于单独测试；[TextInputWidget] 只负责绘制与事件编排。
 * caret/selection 偏移使用 UTF-16 code unit（与 AWT/Android 一致），对外的 caretPos 使用
 * code point 计数。
 */
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

    /** 提交文本（不含 preedit）变化时回调；用于 `onTextChanged`/`bindText`。 */
    var onCommittedTextChanged: ((String) -> Unit)? = null

    private var lastNotifiedText = ""

    /** 提交文本（不含 IME preedit）。 */
    val committedText: String get() = committed.toString()

    /** 实际显示文本：focused 且有 preedit 时把 preedit 插入 caret 处。 */
    val composedText: String
        get() = if (preeditText.isEmpty()) committedText
        else StringBuilder(committed).insert(caretUnit, preeditText).toString()

    val codePointCount: Int get() = committed.codePointCount(0, committed.length)

    val caretUnit: Int get() = codeUnitIndex(caretPos)

    /** 重置为 [text]，超长按 code point 截断，caret 移到末尾并清空选区。 */
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

    /** 鼠标按下：caret 移到 [position]（code point），清空选区。 */
    fun setCaret(position: Int) {
        caretPos = Mth.clamp(position, 0, codePointCount)
        clearSelection()
    }

    /** 鼠标按下开始拖选：锚点与 caret 都落在 [position]。 */
    fun beginSelection(position: Int) {
        setCaret(position)
        selectionStart = caretPos
        selectionEnd = caretPos
    }

    /** 鼠标拖选：以 [anchor] 为固定端，caret 移到 [position]。 */
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

    /** charTyped：插入一个 code point，受 maxLength/validator/换行约束。 */
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

    /** 回车：允许换行则插入 `\n`，否则返回 false 由控件处理 `whenEnter`。 */
    fun insertNewline(): Boolean {
        if (!allowLineBreak) return false
        if (codePointCount - selectionSize() >= maxLength) return false
        return commitInsert("\n")
    }

    /** 粘贴/程序化插入，返回是否实际改变文本。 */
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

    /**
     * 统一插入路径：先构造"删除选区 + 插入"后的潜在文本并验证（maxLength + validator），
     * 通过后再一次性提交，避免"先删后验"导致拒绝时选区已丢失、回调已触发。
     */
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

    /** 非修改地构造提交后的潜在文本（caret 在选区时落在选区起始处）。 */
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

    /** 用 IME 预编辑文本替换当前 preedit（[fullText] 为 null 表示清空）。 */
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
