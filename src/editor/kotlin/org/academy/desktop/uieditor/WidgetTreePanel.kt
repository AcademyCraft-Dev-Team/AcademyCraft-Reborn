package org.academy.desktop.uieditor

import imgui.ImGui
import imgui.ImVec2
import org.academy.api.client.gui.editor.UiEditorDocument
import org.academy.api.client.gui.serialize.WidgetNode

/**
 * 结构树面板：以可折叠树展示 [UiEditorDocument.root]，点击选择控制点。
 * 折叠状态是本面板的视图状态（非文档内容），经 [select] 通知宿主刷新选中框。
 */
class WidgetTreePanel(
    private val getDoc: () -> UiEditorDocument,
    private val select: (List<String>) -> Unit,
) {
    private val doc: UiEditorDocument get() = getDoc()
    private val expanded = HashSet<String>()

    fun collapseAll() {
        expanded.clear()
        expanded.add("")
    }

    fun expandAll() {
        expanded.clear()
        expandAll(doc.root, "")
    }

    private fun expandAll(node: WidgetNode, path: String) {
        expanded.add(path)
        val base = if (path.isEmpty()) "" else "$path/"
        for (child in node.children) expandAll(child, base + child.name)
    }

    /** 将展开状态固化为 ImGui 布局（保留根节点）。 */
    fun render() {
        if (expanded.isEmpty()) expanded.add("")
        ImGui.text("Widget Tree")
        ImGui.separator()
        val selected = doc.selectedPath
        addNode(doc.root, 0, emptyList(), selected)
        ImGui.separator()
    }

    private fun addNode(node: WidgetNode, depth: Int, path: List<String>, selected: List<String>) {
        val name = node.name.ifBlank { node.type }
        val label = "$name  [${node.type}]"
        val hasChildren = node.children.isNotEmpty()
        val key = path.joinToString("/")
        val isSelected = path == selected

        ImGui.indent(depth * 12f)
        if (hasChildren) {
            val open = expanded.contains(key)
            val arrow = if (open) "\u25BE" else "\u25B8"
            if (ImGui.button(arrow, ImVec2(16f, ImGui.getFrameHeight()))) {
                if (open) expanded.remove(key) else expanded.add(key)
            }
            ImGui.sameLine()
        }
        if (ImGui.selectable(label, isSelected)) {
            select(path)
        }
        ImGui.unindent(depth * 12f)

        if (hasChildren && expanded.contains(key)) {
            for (child in node.children) addNode(child, depth + 1, path + child.name, selected)
        }
    }
}
