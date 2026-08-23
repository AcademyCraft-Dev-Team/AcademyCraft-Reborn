package org.academy.desktop.grapheditor.clipboard

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.academy.api.client.render.graph.model.Edge
import org.academy.api.client.render.graph.model.Graph
import org.academy.api.client.render.graph.model.GraphNode
import org.academy.api.client.render.graph.registry.NodeRegistry
import org.academy.api.client.render.graph.serialize.JsonGraphCodec
import org.academy.desktop.grapheditor.canvas.GraphEditorModel

/**
 * 节点剪贴板：把选中节点（含内部边）序列化为 JSON snippet（复用 [JsonGraphCodec]），
 * 支持粘贴到指定坐标 / 偏移复制（Duplicate）。复制与粘贴均可整体撤销。
 */
class GraphClipboard(private val registry: NodeRegistry) {
    private val codec = JsonGraphCodec(registry)
    private val gson = Gson()

    /** 序列化选中节点 + 内部边为 JSON 字符串；选择为空返回 null。 */
    fun copy(model: GraphEditorModel, selected: Set<String>): String? {
        if (selected.isEmpty()) return null
        val nodes = model.nodes.filterKeys { it in selected }
        if (nodes.isEmpty()) return null
        val graphNodes = nodes.values.map { n ->
            GraphNode(n.id, n.typeId, n.properties.toMap(), model.portsFor(n), n.x, n.y)
        }
        val inSelection = nodes.keys.toSet()
        val graphEdges = model.edges.values
            .filter { it.fromNode in inSelection && it.toNode in inSelection }
            .map { e -> Edge(Edge.PortRef(e.fromNode, e.fromPort), Edge.PortRef(e.toNode, e.toPort)) }
        return gson.toJson(codec.encode(Graph("clipboard", graphNodes, graphEdges, emptyList(), emptyList())))
    }

    /** 解码剪贴板 JSON；格式非法返回 null。 */
    fun decode(snippet: String?): Graph? {
        if (snippet.isNullOrBlank()) return null
        return try {
            val json = gson.fromJson(snippet, JsonObject::class.java) ?: return null
            codec.decode(json)
        } catch (_: Exception) {
            null
        }
    }

    /** 粘贴到画布 [targetX, targetY]（图坐标），粘贴体中心对齐目标点。返回创建节点 id。 */
    fun pasteAt(model: GraphEditorModel, snippet: String?, targetX: Float, targetY: Float): List<String> {
        val graph = decode(snippet) ?: return emptyList()
        if (graph.nodes().isEmpty()) return emptyList()
        val (dx, dy) = centerOffset(graph, targetX, targetY)
        return executePaste(model, graph, dx, dy)
    }

    /** 复制(Duplicate)：在源位置右下方固定偏移粘贴选中节点。返回创建节点 id。 */
    fun duplicate(model: GraphEditorModel, selected: Set<String>): List<String> {
        val snippet = copy(model, selected) ?: return emptyList()
        val graph = decode(snippet) ?: return emptyList()
        return executePaste(model, graph, DUPLICATE_OFFSET, DUPLICATE_OFFSET)
    }

    private fun executePaste(model: GraphEditorModel, graph: Graph, dx: Float, dy: Float): List<String> {
        val command = PasteNodesCommand(model, graph, dx, dy)
        model.submit(command)
        return command.pastedNodeIds
    }

    private fun centerOffset(graph: Graph, targetX: Float, targetY: Float): Pair<Float, Float> {
        if (graph.nodes().isEmpty()) return Pair(0f, 0f)
        val xs = graph.nodes().map { it.x() }
        val ys = graph.nodes().map { it.y() }
        val cx = (xs.min() + xs.max()) / 2f
        val cy = (ys.min() + ys.max()) / 2f
        return Pair(targetX - cx, targetY - cy)
    }

    companion object {
        const val DUPLICATE_OFFSET = 24f
    }
}
