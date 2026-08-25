package org.academy.desktop.grapheditor.command

import org.academy.api.client.render.graph.model.GraphParameter
import org.academy.desktop.grapheditor.canvas.GraphEditorModel

/**
 * 以模型为宿主的命令基类。
 */
abstract class ModelCommand(protected val model: GraphEditorModel) : Command

/** 添加节点。undo 移除节点（及由此产生的边）。redo 复用同一 node id。 */
class AddNodeCommand(
    model: GraphEditorModel,
    private val typeId: String,
    private val x: Float,
    private val y: Float,
) : ModelCommand(model) {
    var nodeId: String = ""
        private set

    override fun execute() {
        if (nodeId.isEmpty()) {
            nodeId = model.createNode(typeId, x, y).id
        } else {
            model.createNodeWithId(nodeId, typeId, emptyMap(), x, y)
        }
    }

    override fun undo() {
        model.deleteNode(nodeId)
    }

    override fun label(): String = "Add node"
}

/** 删除节点。记录节点快照与关联边，undo 完整还原。 */
class RemoveNodeCommand(
    model: GraphEditorModel,
    private val nodeId: String,
) : ModelCommand(model) {
    private var typeId: String = ""
    private var x: Float = 0f
    private var y: Float = 0f
    private val properties = LinkedHashMap<String, String>()
    private val edges = mutableListOf<GraphEditorModel.EdEdge>()
    private var wasOutput = false
    private var outputIndex = -1

    override fun execute() {
        val node = model.nodes[nodeId] ?: return
        typeId = node.typeId
        x = node.x
        y = node.y
        properties.clear()
        properties.putAll(node.properties)
        edges.clear()
        edges.addAll(model.edges.values.filter { it.fromNode == nodeId || it.toNode == nodeId })
        wasOutput = model.outputNodeIds.contains(nodeId)
        outputIndex = model.outputNodeIds.indexOf(nodeId)
        model.deleteNode(nodeId)
    }

    override fun undo() {
        model.createNodeWithId(nodeId, typeId, properties, x, y)
        for (edge in edges) {
            model.edges["${edge.toNode}:${edge.toPort}"] = edge
        }
        if (wasOutput) {
            val index = if (outputIndex >= 0) outputIndex.coerceAtMost(model.outputNodeIds.size) else model.outputNodeIds.size
            model.outputNodeIds.add(index, nodeId)
        }
    }

    override fun label(): String = "Delete node"
}

/** 调整节点执行顺序（VFX 有序 passes：nodes 列表顺序即执行顺序）。do/undo 均重建 LinkedHashMap 序。 */
class ReorderNodeCommand(
    model: GraphEditorModel,
    private val nodeId: String,
    private val fromIndex: Int,
    private val toIndex: Int,
) : ModelCommand(model) {
    override fun execute() {
        model.reorderNode(nodeId, toIndex)
    }

    override fun undo() {
        model.reorderNode(nodeId, fromIndex)
    }

    override fun label(): String = "Reorder node"
}

/** 连接边。若输入端口已有边则记录旧边，undo 时恢复。 */
class ConnectCommand(
    model: GraphEditorModel,
    private val fromNode: String,
    private val fromPort: String,
    private val toNode: String,
    private val toPort: String,
) : ModelCommand(model) {
    private var previous: GraphEditorModel.EdEdge? = null

    override fun execute() {
        val key = "$toNode:$toPort"
        previous = model.edges[key]
        model.edges[key] = GraphEditorModel.EdEdge(fromNode, fromPort, toNode, toPort)
    }

    override fun undo() {
        val key = "$toNode:$toPort"
        if (previous != null) {
            model.edges[key] = previous!!
        } else {
            model.edges.remove(key)
        }
    }

    override fun label(): String = "Connect"
}

/** 断开边。记录旧边，undo 恢复。 */
class DisconnectCommand(
    model: GraphEditorModel,
    private val toNode: String,
    private val toPort: String,
) : ModelCommand(model) {
    private var previous: GraphEditorModel.EdEdge? = null

    override fun execute() {
        val key = "$toNode:$toPort"
        previous = model.edges[key]
        model.edges.remove(key)
    }

    override fun undo() {
        previous?.let { model.edges["${it.toNode}:${it.toPort}"] = it }
    }

    override fun label(): String = "Disconnect"
}

/** 边重连：断开 [toNode:toPort] 旧边并连接新边，一次命令完成，undo 恢复旧连接。 */
class ReconnectCommand(
    model: GraphEditorModel,
    private val fromNode: String,
    private val fromPort: String,
    private val toNode: String,
    private val toPort: String,
) : ModelCommand(model) {
    private var previous: GraphEditorModel.EdEdge? = null

    override fun execute() {
        val key = "$toNode:$toPort"
        previous = model.edges[key]
        model.edges[key] = GraphEditorModel.EdEdge(fromNode, fromPort, toNode, toPort)
    }

    override fun undo() {
        val key = "$toNode:$toPort"
        if (previous != null) {
            model.edges[key] = previous!!
        } else {
            model.edges.remove(key)
        }
    }

    override fun label(): String = "Reconnect"
}

/** 设置图输出节点。记录旧输出列表。 */
class SetOutputCommand(
    model: GraphEditorModel,
    private val nodeId: String,
) : ModelCommand(model) {
    private var previous: List<String> = emptyList()

    override fun execute() {
        previous = model.outputNodeIds.toList()
        model.outputNodeIds.clear()
        model.outputNodeIds.add(nodeId)
    }

    override fun undo() {
        model.outputNodeIds.clear()
        model.outputNodeIds.addAll(previous)
    }

    override fun label(): String = "Set output"
}

/** 修改节点属性。连续拖拽可合并（mergeKey = prop:nodeId:propId）。 */
class SetPropertyCommand(
    model: GraphEditorModel,
    private val nodeId: String,
    private val propId: String,
    private val oldValue: String,
    private val newValue: String,
) : ModelCommand(model) {
    override fun execute() {
        model.nodes[nodeId]?.properties?.put(propId, newValue)
    }

    override fun undo() {
        model.nodes[nodeId]?.properties?.put(propId, oldValue)
    }

    override fun mergeKey(): String = "prop:$nodeId:$propId"

    override fun mergeWith(next: Command): Command? {
        if (next !is SetPropertyCommand || next.nodeId != nodeId || next.propId != propId) return null
        return SetPropertyCommand(model, nodeId, propId, oldValue, next.newValue)
    }

    override fun label(): String = "Set property $propId"
}

/** 移动节点。连续拖拽可合并（mergeKey = move:nodeId）。 */
class MoveNodeCommand(
    model: GraphEditorModel,
    private val nodeId: String,
    private val oldX: Float,
    private val oldY: Float,
    private val newX: Float,
    private val newY: Float,
) : ModelCommand(model) {
    override fun execute() {
        model.nodes[nodeId]?.let { node ->
            node.x = newX
            node.y = newY
        }
    }

    override fun undo() {
        model.nodes[nodeId]?.let { node ->
            node.x = oldX
            node.y = oldY
        }
    }

    override fun mergeKey(): String = "move:$nodeId"

    override fun mergeWith(next: Command): Command? {
        if (next !is MoveNodeCommand || next.nodeId != nodeId) return null
        return MoveNodeCommand(model, nodeId, oldX, oldY, next.newX, next.newY)
    }

    override fun label(): String = "Move node"
}

/** 添加黑板参数。 */
class AddParameterCommand(
    model: GraphEditorModel,
    private val param: GraphParameter,
    private val index: Int,
) : ModelCommand(model) {
    override fun execute() {
        model.parameters.add(index.coerceAtMost(model.parameters.size), param)
    }

    override fun undo() {
        if (index in model.parameters.indices) model.parameters.removeAt(index)
    }

    override fun label(): String = "Add parameter"
}

/** 移除黑板参数。记录原参数，undo 还原。 */
class RemoveParameterCommand(
    model: GraphEditorModel,
    private val index: Int,
) : ModelCommand(model) {
    private var param: GraphParameter? = null

    override fun execute() {
        if (index !in model.parameters.indices) return
        param = model.parameters[index]
        model.parameters.removeAt(index)
    }

    override fun undo() {
        param?.let { model.parameters.add(index.coerceAtMost(model.parameters.size), it) }
    }

    override fun label(): String = "Remove parameter"
}

/** 修改黑板参数（整体替换为 [new]，含默认值变更）。 */
class SetParameterCommand(
    model: GraphEditorModel,
    private val index: Int,
    private val old: GraphParameter,
    private val new: GraphParameter,
) : ModelCommand(model) {
    override fun execute() {
        if (index in model.parameters.indices) model.parameters[index] = new
    }

    override fun undo() {
        if (index in model.parameters.indices) model.parameters[index] = old
    }

    override fun label(): String = "Edit parameter"
}
