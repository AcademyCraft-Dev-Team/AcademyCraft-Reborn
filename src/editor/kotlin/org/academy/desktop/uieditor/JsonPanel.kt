package org.academy.desktop.uieditor

import imgui.ImGui
import imgui.type.ImString
import org.academy.api.client.gui.editor.UiEditorDocument

/**
 * JSON 面板：只读展示文档美化 JSON；「Edit」切到多行可编辑区，「Apply」经
 * [UiEditorDocument.applyJsonText] 回写文档。错误在面板内以红色提示。
 */
class JsonPanel(private val getDoc: () -> UiEditorDocument) {
    private val doc: UiEditorDocument get() = getDoc()
    private var editMode = false
    private var buffer = ImString(65536)
    private var error: String? = null

    /** 文档切换后复位编辑状态。 */
    fun reset() {
        editMode = false
        error = null
        buffer.set(doc.prettyJson())
    }

    fun render() {
        ImGui.text("JSON")
        ImGui.sameLine()
        if (ImGui.button(if (editMode) "Done##jsondone" else "Edit##jsonedit")) {
            editMode = !editMode
            error = null
            if (editMode) buffer.set(doc.prettyJson())
        }
        ImGui.sameLine()
        if (editMode && ImGui.button("Apply##jsonapply")) {
            val err = doc.applyJsonText(buffer.get())
            if (err == null) {
                editMode = false
                error = null
            } else {
                error = err
            }
        }
        ImGui.separator()

        if (editMode) {
            val h = ImGui.getContentRegionAvailY().coerceAtLeast(120f)
            if (ImGui.inputTextMultiline("##jsonbuffer", buffer, 0f, h)) Unit
        } else {
            val h = ImGui.getContentRegionAvailY().coerceAtLeast(120f)
            if (ImGui.beginChild("##jsonview", 0f, h)) {
                for (line in doc.prettyJson().split('\n')) ImGui.textUnformatted(line)
            }
            ImGui.endChild()
        }

        if (error != null) {
            ImGui.textColored(1f, 0.45f, 0.45f, 1f, error!!)
        }
    }
}
