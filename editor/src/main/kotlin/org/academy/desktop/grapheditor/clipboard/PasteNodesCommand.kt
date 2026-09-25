package org.academy.desktop.grapheditor.clipboard

import org.academy.api.client.render.graph.model.Graph
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.command.ModelCommand

/**
 * 粘贴命令：把剪贴板解码出的子图按偏移写入模型，一次命令整体可撤销。
 * 节点 id 在首次 execute 时一次性分配并缓存，redo 复用同一组 id（undo/redo 后身份稳定）。
 */
class PasteNodesCommand(
    model: GraphEditorModel,
    private val graph: Graph,
    private val offsetX: Float,
    private val offsetY: Float,
) : ModelCommand(model) {
    private val idMap = mutableMapOf<String, String>()
    private val created = mutableListOf<String>()

    /** 本次粘贴创建的所有节点 id（供宿主更新选择）。 */
    val pastedNodeIds: List<String> get() = created.toList()

    override fun execute() {
        if (idMap.isEmpty()) {
            for (n in graph.nodes()) idMap[n.id()] = model.allocateId()
        }
        created.clear()
        for (n in graph.nodes()) {
            val id = idMap[n.id()]!!
            val node = model.createNodeWithId(id, n.type(), n.properties(), n.x() + offsetX, n.y() + offsetY)
            created.add(id)
        }
        for (e in graph.edges()) {
            val from = idMap[e.from().nodeId()] ?: continue
            val to = idMap[e.to().nodeId()] ?: continue
            model.edges["$to:${e.to().portId()}"] =
                GraphEditorModel.EdEdge(from, e.from().portId(), to, e.to().portId())
        }
    }

    override fun undo() {
        for (id in created) model.deleteNode(id)
    }

    override fun label(): String = "Paste ${created.size} node${if (created.size == 1) "" else "s"}"
}
