package org.academy.desktop.grapheditor.commandpalette

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString

/**
 * 命令面板（Ctrl+P）：搜索节点类型与编辑命令并回车/点击执行。
 * 动作列表由宿主构建（节点添加 + 编辑命令）。
 */
class CommandPalette {
    private val input = ImString(64)
    private var actions: List<Pair<String, () -> Unit>> = emptyList()

    var open = false
        private set

    fun setActions(actions: List<Pair<String, () -> Unit>>) {
        this.actions = actions
    }

    fun open() {
        open = true
        input.set("")
        ImGui.setKeyboardFocusHere(0)
    }

    fun toggle() {
        if (open) open = false else open()
    }

    fun render() {
        if (!open) return
        ImGui.openPopup("Command Palette")
        if (ImGui.beginPopupModal("Command Palette", WINDOW_FLAGS)) {
            ImGui.inputText("##palette_input", input)
            if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere(0)

            val query = input.get().trim().lowercase()
            val visible = actions.filter { query.isEmpty() || it.first.lowercase().contains(query) }

            if (ImGui.isKeyPressed(ImGuiKey.Enter) && visible.isNotEmpty()) {
                visible.first().second()
                close()
            }

            for ((label, action) in visible) {
                if (ImGui.menuItem(label)) {
                    action()
                    close()
                    break
                }
            }

            if (ImGui.isKeyPressed(ImGuiKey.Escape)) close()
            ImGui.endPopup()
        }
    }

    private fun close() {
        open = false
        input.set("")
    }

    companion object {
        private val WINDOW_FLAGS = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoResize
    }
}
