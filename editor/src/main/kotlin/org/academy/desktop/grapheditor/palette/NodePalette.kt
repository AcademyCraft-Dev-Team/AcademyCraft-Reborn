package org.academy.desktop.grapheditor.palette

import imgui.ImGui
import imgui.type.ImString
import org.academy.api.client.render.graph.registry.NodeRegistry
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.canvas.GraphEditorModelRef

/**
 * 节点面板：按分类展示节点目录，支持搜索，点击在画布中心处添加节点。
 */
class NodePalette(
    private val registry: NodeRegistry,
    private val modelRef: GraphEditorModelRef,
    private val spawnPosition: () -> Pair<Float, Float>,
) {
    private val model: GraphEditorModel get() = modelRef.model
    private val search = ImString(64)

    fun render() {
        ImGui.text("Node Palette")
        ImGui.inputText("##search", search)
        val query = search.get().trim().lowercase()

        val grouped = registry.all().groupBy { it.category() }.toSortedMap()
        for ((category, types) in grouped) {
            val filtered = types.filter {
                query.isEmpty() || it.displayName().lowercase().contains(query) || it.id().lowercase().contains(query)
            }
            if (filtered.isEmpty()) continue

            if (ImGui.collapsingHeader(category)) {
                for (type in filtered) {
                    if (ImGui.button("${type.displayName()}##${type.id()}")) {
                        val pos = spawnPosition()
                        model.addNode(type.id(), pos.first, pos.second)
                    }
                }
            }
        }
    }
}
