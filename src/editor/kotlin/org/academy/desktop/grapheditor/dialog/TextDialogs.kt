package org.academy.desktop.grapheditor.dialog

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.type.ImString

/**
 * 单行输入对话框（重命名 frame 等）。
 */
class PromptDialog {
    private val buffer = ImString(64)
    private var open = false
        private set
    private var title = ""
    private var label = ""
    private var onSubmit: ((String) -> Unit)? = null

    fun open(title: String, label: String, initial: String, onSubmit: (String) -> Unit) {
        this.title = title
        this.label = label
        this.onSubmit = onSubmit
        buffer.set(initial)
        open = true
        ImGui.setKeyboardFocusHere(0)
    }

    fun render() {
        if (!open) return
        ImGui.openPopup(title)
        if (ImGui.beginPopupModal(title)) {
            ImGui.text(label)
            ImGui.inputText("##prompt_input", buffer)
            if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0)
            ImGui.separator()
            if (ImGui.button("OK") || ImGui.isKeyPressed(ImGuiKey.Enter)) {
                onSubmit?.invoke(buffer.get())
                close()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) close()
            ImGui.endPopup()
        }
    }

    private fun close() {
        open = false
        onSubmit = null
    }
}

/**
 * sticky note 编辑对话框（标题 + 正文）。
 */
class NoteEditDialog {
    private val titleBuf = ImString(64)
    private val bodyBuf = ImString(2048)
    private var open = false
        private set
    private var onSubmit: ((String, String) -> Unit)? = null

    fun open(title: String, body: String, onSubmit: (String, String) -> Unit) {
        titleBuf.set(title)
        bodyBuf.set(body)
        this.onSubmit = onSubmit
        open = true
    }

    fun render() {
        if (!open) return
        ImGui.openPopup("Edit Note")
        if (ImGui.beginPopupModal("Edit Note")) {
            ImGui.inputText("Title##note_title", titleBuf)
            ImGui.inputTextMultiline("Body##note_body", bodyBuf, 280f, 120f)
            ImGui.separator()
            if (ImGui.button("OK")) {
                onSubmit?.invoke(titleBuf.get(), bodyBuf.get())
                close()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) close()
            ImGui.endPopup()
        }
    }

    private fun close() {
        open = false
        onSubmit = null
    }
}
