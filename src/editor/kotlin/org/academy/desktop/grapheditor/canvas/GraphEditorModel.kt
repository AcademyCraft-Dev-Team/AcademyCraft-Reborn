package org.academy.desktop.grapheditor.canvas

import org.academy.api.client.render.graph.model.Edge
import org.academy.api.client.render.graph.model.Graph
import org.academy.api.client.render.graph.model.GraphNode
import org.academy.api.client.render.graph.model.GraphParameter
import org.academy.api.client.render.graph.model.Port
import org.academy.api.client.render.graph.model.PortDirection
import org.academy.api.client.render.graph.registry.NodeRegistry
import org.academy.api.client.render.graph.registry.NodeType
import org.academy.api.client.render.graph.registry.PortSpec
import org.academy.api.client.render.graph.type.TypeConversions
import org.academy.api.client.render.graph.type.Value
import org.academy.api.client.render.graph.type.ValueType
import org.academy.desktop.grapheditor.command.AddNodeCommand
import org.academy.desktop.grapheditor.command.AddParameterCommand
import org.academy.desktop.grapheditor.command.AddFrameCommand
import org.academy.desktop.grapheditor.command.AddNoteCommand
import org.academy.desktop.grapheditor.command.Command
import org.academy.desktop.grapheditor.command.ConnectCommand
import org.academy.desktop.grapheditor.command.CompositeCommand
import org.academy.desktop.grapheditor.command.DisconnectCommand
import org.academy.desktop.grapheditor.command.MoveFrameCommand
import org.academy.desktop.grapheditor.command.MoveNodeCommand
import org.academy.desktop.grapheditor.command.MoveNoteCommand
import org.academy.desktop.grapheditor.command.ReconnectCommand
import org.academy.desktop.grapheditor.command.RemoveFrameCommand
import org.academy.desktop.grapheditor.command.RemoveNodeCommand
import org.academy.desktop.grapheditor.command.RemoveNoteCommand
import org.academy.desktop.grapheditor.command.RemoveParameterCommand
import org.academy.desktop.grapheditor.command.RenameFrameCommand
import org.academy.desktop.grapheditor.command.ReorderNodeCommand
import org.academy.desktop.grapheditor.command.ResizeFrameCommand
import org.academy.desktop.grapheditor.command.SetNoteContentCommand
import org.academy.desktop.grapheditor.command.SetOutputCommand
import org.academy.desktop.grapheditor.command.SetParameterCommand
import org.academy.desktop.grapheditor.command.SetPropertyCommand
import org.academy.desktop.grapheditor.command.UndoManager
import org.academy.desktop.grapheditor.document.EditorMetadata
import org.academy.desktop.grapheditor.document.FrameData
import org.academy.desktop.grapheditor.document.NoteData

/**
 * 编辑器侧可变文档模型。编辑操作在此累积，经 [toGraph] 产出核心不可变 [Graph] 供编译/预览。
 * 端口由 [NodeType] 派生，保证目录是端口规格的唯一事实源。
 *
 * 所有 mutation 均以命令形式执行（M9-02），经 [UndoManager] 支持撤销/重做；
 * 每次 execute/undo/redo 触发 [markDirty] 以驱动预览重编译。
 */
class GraphEditorModel(private val registry: NodeRegistry) {
    val nodes = LinkedHashMap<String, EdNode>()
    val edges = LinkedHashMap<String, EdEdge>()
    val parameters = mutableListOf<GraphParameter>()
    val outputNodeIds = mutableListOf<String>()
    val frames = LinkedHashMap<String, FrameData>()
    val notes = LinkedHashMap<String, NoteData>()

    var version = 0
        private set

    private var nextId = 0
    private var decorationNextId = 0

    private var subGraphRegistry: org.academy.api.client.render.graph.subgraph.SubGraphRegistry? = null

    fun setSubGraphRegistry(registry: org.academy.api.client.render.graph.subgraph.SubGraphRegistry?) {
        subGraphRegistry = registry
    }

    private val undoManager = UndoManager(onMutate = ::markDirty)

    val canUndo: Boolean get() = undoManager.canUndo
    val canRedo: Boolean get() = undoManager.canRedo

    fun nodeType(typeId: String): NodeType? = registry.find(typeId)

    class EdNode(
        val id: String,
        val typeId: String,
        val properties: MutableMap<String, String>,
        var x: Float,
        var y: Float,
    )

    class EdEdge(val fromNode: String, val fromPort: String, val toNode: String, val toPort: String)

    fun markDirty() {
        version++
    }

    // ---- 撤销 / 重做 ----

    fun undo() {
        undoManager.undo()
    }

    fun redo() {
        undoManager.redo()
    }

    fun clearHistory() {
        undoManager.clear()
    }

    /** 提交任意命令（供剪贴板/对齐等编辑器功能复用命令栈）。 */
    fun submit(command: Command) {
        undoManager.execute(command)
    }

    // ---- 内部变更原语（命令与加载调用，不触发 markDirty）----

    /** 分配一个新节点 id（不创建节点）。 */
    fun allocateId(): String = "n${nextId++}"

    fun createNode(typeId: String, x: Float, y: Float): EdNode {
        val node = EdNode("n${nextId++}", typeId, LinkedHashMap(), x, y)
        nodes[node.id] = node
        return node
    }

    fun createNodeWithId(id: String, typeId: String, properties: Map<String, String>, x: Float, y: Float): EdNode {
        val node = EdNode(id, typeId, properties.toMutableMap(), x, y)
        nodes[id] = node
        return node
    }

    fun deleteNode(id: String) {
        nodes.remove(id)
        edges.entries.removeIf { it.value.fromNode == id || it.value.toNode == id }
        outputNodeIds.remove(id)
    }

    // ---- 公开 mutation API（全部经命令提交）----

    fun addNode(typeId: String, x: Float, y: Float): EdNode {
        val command = AddNodeCommand(this, typeId, x, y)
        undoManager.execute(command)
        return nodes[command.nodeId] ?: error("node was created")
    }

    fun removeNode(id: String) {
        removeNodes(listOf(id))
    }

    fun removeNodes(ids: List<String>) {
        val commands = ids.mapNotNull { id ->
            if (nodes.containsKey(id)) RemoveNodeCommand(this, id) else null
        }
        if (commands.isEmpty()) return
        undoManager.execute(CompositeCommand(commands, "Delete ${commands.size} node${if (commands.size > 1) "s" else ""}"))
    }

    fun connect(fromNode: String, fromPort: String, toNode: String, toPort: String): Boolean {
        val fromSpec = portSpec(nodes[fromNode], fromPort) ?: return false
        val toSpec = portSpec(nodes[toNode], toPort) ?: return false
        if (fromSpec.direction() != PortDirection.OUTPUT || toSpec.direction() != PortDirection.INPUT) return false
        if (!TypeConversions.INSTANCE.canConvert(fromSpec.type(), toSpec.type())) return false
        undoManager.execute(ConnectCommand(this, fromNode, fromPort, toNode, toPort))
        return true
    }

    fun disconnect(toNode: String, toPort: String) {
        if (!edges.containsKey("$toNode:$toPort")) return
        undoManager.execute(DisconnectCommand(this, toNode, toPort))
    }

    fun reconnect(fromNode: String, fromPort: String, toNode: String, toPort: String): Boolean {
        val fromSpec = portSpec(nodes[fromNode], fromPort) ?: return false
        val toSpec = portSpec(nodes[toNode], toPort) ?: return false
        if (fromSpec.direction() != PortDirection.OUTPUT || toSpec.direction() != PortDirection.INPUT) return false
        if (!TypeConversions.INSTANCE.canConvert(fromSpec.type(), toSpec.type())) return false
        undoManager.execute(ReconnectCommand(this, fromNode, fromPort, toNode, toPort))
        return true
    }

    fun setOutput(nodeId: String) {
        undoManager.execute(SetOutputCommand(this, nodeId))
    }

    fun moveNode(nodeId: String, newX: Float, newY: Float) {
        val node = nodes[nodeId] ?: return
        if (node.x == newX && node.y == newY) return
        undoManager.execute(MoveNodeCommand(this, nodeId, node.x, node.y, newX, newY))
    }

    fun setProperty(nodeId: String, propId: String, newValue: String) {
        val node = nodes[nodeId] ?: return
        val old = node.properties[propId] ?: defaultPropertyValue(node, propId) ?: return
        if (old == newValue) return
        undoManager.execute(SetPropertyCommand(this, nodeId, propId, old, newValue))
    }

    private fun defaultPropertyValue(node: EdNode, propId: String): String? {
        val type = registry.find(node.typeId) ?: return null
        return type.properties().firstOrNull { it.id() == propId }?.defaultValue()?.let { propertyValueString(it) }
    }

    private fun propertyValueString(value: Value): String = when (value.type()) {
        ValueType.FLOAT -> value.asFloat().toString()
        ValueType.COLOR -> {
            val c = value.asColor()
            "${c.x},${c.y},${c.z},${c.w}"
        }
        else -> ""
    }

    fun addParameter(param: GraphParameter) {
        undoManager.execute(AddParameterCommand(this, param, parameters.size))
    }

    fun removeParameter(index: Int) {
        if (index !in parameters.indices) return
        undoManager.execute(RemoveParameterCommand(this, index))
    }

    fun replaceParameter(index: Int, newParam: GraphParameter) {
        if (index !in parameters.indices) return
        val old = parameters[index]
        if (old == newParam) return
        undoManager.execute(SetParameterCommand(this, index, old, newParam))
    }

    // ---- 分组 frame ----

    fun addFrame(title: String, x: Float, y: Float, w: Float, h: Float): FrameData {
        val frame = FrameData("f${decorationNextId++}", title, EditorMetadata.DEFAULT_FRAME_COLOR, x, y, w, h)
        undoManager.execute(AddFrameCommand(this, frame))
        return frame
    }

    fun removeFrame(id: String) {
        if (!frames.containsKey(id)) return
        undoManager.execute(RemoveFrameCommand(this, id))
    }

    fun moveFrame(id: String, newX: Float, newY: Float) {
        val frame = frames[id] ?: return
        if (frame.x == newX && frame.y == newY) return
        undoManager.execute(MoveFrameCommand(this, id, frame.x, frame.y, newX, newY))
    }

    fun resizeFrame(id: String, newW: Float, newH: Float) {
        val frame = frames[id] ?: return
        if (frame.w == newW && frame.h == newH) return
        undoManager.execute(ResizeFrameCommand(this, id, frame.w, frame.h, newW, newH))
    }

    fun renameFrame(id: String, title: String) {
        val frame = frames[id] ?: return
        if (frame.title == title) return
        undoManager.execute(RenameFrameCommand(this, id, frame.title, title))
    }

    // ---- sticky note ----

    fun addNote(title: String, x: Float, y: Float): NoteData {
        val note = NoteData("note${decorationNextId++}", title, "", EditorMetadata.DEFAULT_NOTE_COLOR, x, y, 180f, 120f)
        undoManager.execute(AddNoteCommand(this, note))
        return note
    }

    fun removeNote(id: String) {
        if (!notes.containsKey(id)) return
        undoManager.execute(RemoveNoteCommand(this, id))
    }

    fun moveNote(id: String, newX: Float, newY: Float) {
        val note = notes[id] ?: return
        if (note.x == newX && note.y == newY) return
        undoManager.execute(MoveNoteCommand(this, id, note.x, note.y, newX, newY))
    }

    fun setNoteContent(id: String, title: String, body: String, color: Int) {
        val note = notes[id] ?: return
        if (note.title == title && note.body == body && note.color == color) return
        undoManager.execute(SetNoteContentCommand(this, id, note.title, title, note.body, body, note.color, color))
    }

    // ---- 节点执行顺序（VFX 有序 passes：nodes 列表顺序即执行顺序）----

    /** 把节点移动到列表指定下标（重建 LinkedHashMap 序，不改节点内容）。 */
    fun reorderNode(nodeId: String, toIndex: Int) {
        if (!nodes.containsKey(nodeId)) return
        val ids = nodes.keys.toMutableList()
        ids.remove(nodeId)
        ids.add(toIndex.coerceIn(0, ids.size), nodeId)
        val reordered = LinkedHashMap<String, EdNode>()
        for (id in ids) {
            reordered[id] = nodes[id]!!
        }
        nodes.clear()
        nodes.putAll(reordered)
    }

    /** 把节点在 VFX 执行顺序中上移/下移（可撤销）。 */
    fun moveNodeExecutionOrder(nodeId: String, delta: Int) {
        val ids = nodes.keys.toList()
        val index = ids.indexOf(nodeId)
        if (index < 0) return
        val toIndex = (index + delta).coerceIn(0, ids.size - 1)
        if (toIndex == index) return
        undoManager.execute(ReorderNodeCommand(this, nodeId, index, toIndex))
    }

    /** 清空文档并清除历史（新建图）。 */
    fun reset() {
        nodes.clear()
        edges.clear()
        parameters.clear()
        outputNodeIds.clear()
        frames.clear()
        notes.clear()
        nextId = 0
        decorationNextId = 0
        clearHistory()
        markDirty()
    }

    /** 批量装载 sidecar 元数据（frame/note），替换现有并清历史。 */
    fun loadMetadata(metadata: EditorMetadata) {
        frames.clear()
        notes.clear()
        frames.putAll(metadata.frames)
        notes.putAll(metadata.notes)
        decorationNextId = maxOf(
            frames.keys.mapNotNull { it.removePrefix("f").toIntOrNull() }.maxOrNull() ?: -1,
            notes.keys.mapNotNull { it.removePrefix("note").toIntOrNull() }.maxOrNull() ?: -1
        ) + 1
        clearHistory()
        markDirty()
    }

    fun portsFor(node: EdNode): List<Port> {
        if (node.typeId == "subgraph") return subGraphPorts(node)
        val type = registry.find(node.typeId) ?: return emptyList()
        return type.ports().map { Port(it.id(), it.name(), it.direction(), it.type(), it.defaultValue()) }
    }

    /** subgraph 节点端口由引用的子图动态派生：参数 → 输入 `in<i>`，输出端口 `out`。 */
    private fun subGraphPorts(node: EdNode): List<Port> {
        val sub = subGraphRegistry?.find(node.properties["graph"] ?: "") ?: return emptyList()
        val ports = mutableListOf<Port>()
        sub.parameters().forEachIndexed { i, p ->
            ports.add(Port("in$i", p.name(), PortDirection.INPUT, p.type(), p.defaultValue()))
        }
        ports.add(Port("out", "Out", PortDirection.OUTPUT, ValueType.COLOR, org.academy.api.client.render.graph.type.Value.color(1f, 1f, 1f, 1f)))
        return ports
    }

    fun portSpec(node: EdNode?, portId: String): PortSpec? {
        if (node == null) return null
        val type = registry.find(node.typeId) ?: return null
        return type.ports().firstOrNull { it.id() == portId }
    }

    fun load(graph: Graph) {
        nodes.clear()
        edges.clear()
        parameters.clear()
        outputNodeIds.clear()

        for (n in graph.nodes()) {
            nodes[n.id()] = EdNode(n.id(), n.type(), n.properties().toMutableMap(), n.x(), n.y())
        }
        for (e in graph.edges()) {
            edges["${e.to().nodeId()}:${e.to().portId()}"] =
                EdEdge(e.from().nodeId(), e.from().portId(), e.to().nodeId(), e.to().portId())
        }
        parameters.addAll(graph.parameters())
        outputNodeIds.addAll(graph.outputs())

        nextId = (graph.nodes().mapNotNull { it.id().removePrefix("n").toIntOrNull() }.maxOrNull() ?: -1) + 1
        clearHistory()
        markDirty()
    }

    fun toGraph(): Graph {
        val graphNodes = nodes.values.map { n ->
            GraphNode(n.id, n.typeId, n.properties.toMap(), portsFor(n), n.x, n.y)
        }
        val graphEdges = edges.values.map { e ->
            Edge(Edge.PortRef(e.fromNode, e.fromPort), Edge.PortRef(e.toNode, e.toPort))
        }
        return Graph("editor", graphNodes, graphEdges, parameters.toList(), outputNodeIds.toList())
    }
}
