package org.academy.desktop.grapheditor.container

import org.academy.api.client.render.graph.model.GraphParameter
import org.academy.api.client.render.graph.model.Port
import org.academy.api.client.render.graph.registry.NodeRegistry
import org.academy.api.client.render.vfxgraph.model.*
import org.academy.desktop.grapheditor.command.Command
import org.academy.desktop.grapheditor.command.UndoManager
import org.academy.desktop.grapheditor.container.command.*

/**
 * VFX 容器图编辑模型（M26）：编辑 [VfxSystem]（contexts/blocks/operators/flow/data edges）的
 * 可变状态，经 [toSystem] 产出核心不可变模型，经 [load] 装载。
 *
 * <p>与扁平 [GraphEditorModel] 平行（SHADER 模式仍用扁平模型）。所有 mutation 经命令提交
 * （[UndoManager]），每次 execute/undo/redo 触发 [markDirty]（version++）驱动预览重建。</p>
 *
 * <p>块无画布坐标（context 内按插入序垂直排列，即执行顺序）；context/operator 有坐标。</p>
 */
class VfxContainerModel(private val registry: NodeRegistry) {

    /** 块/算子公共视图（属性编辑统一入口）。 */
    interface EdNodeLike {
        val id: String
        val typeId: String
        val properties: MutableMap<String, String>
    }

    class EdContext(
        val id: String,
        val type: VfxContextType,
        val name: String,
        var x: Float,
        var y: Float,
    ) {
        /** 块列表（插入序 = 执行顺序）。 */
        val blocks = LinkedHashMap<String, EdBlock>()
    }

    class EdBlock(
        override val id: String,
        override val typeId: String,
        override val properties: MutableMap<String, String>,
    ) : EdNodeLike

    class EdOperator(
        override val id: String,
        override val typeId: String,
        override val properties: MutableMap<String, String>,
        var x: Float,
        var y: Float,
    ) : EdNodeLike

    class EdFlowEdge(val fromContext: String, val toContext: String)

    class EdBlockFlowEdge(val fromBlock: String, val toBlock: String)

    class EdDataEdge(val fromNode: String, val fromPort: String, val toNode: String, val toPort: String)

    val contexts = LinkedHashMap<String, EdContext>()
    val operators = LinkedHashMap<String, EdOperator>()
    val flowEdges = LinkedHashSet<EdFlowEdge>()
    val blockFlows = LinkedHashSet<EdBlockFlowEdge>()
    val dataEdges = LinkedHashSet<EdDataEdge>()
    val parameters = mutableListOf<GraphParameter>()
    val outputs = mutableListOf<String>()

    var version = 0
        private set

    /** 模拟内容版本：仅「影响模拟」的变更递增（增删块/算子/context、连线、属性、参数、输出）。
     *  布局变更（移动 context/operator）只动 [version] 不递增本值——预览据此避免移动节点时重建模拟器。 */
    var simVersion = 0
        private set

    private var nextContextId = 0
    private var nextNodeId = 0

    private val undoManager = UndoManager(onMutate = ::markDirty)

    val canUndo: Boolean get() = undoManager.canUndo
    val canRedo: Boolean get() = undoManager.canRedo

    fun nodeType(typeId: String) = registry.find(typeId)

    fun markDirty() {
        version++
    }

    /** 标记模拟内容变更（影响模拟的编辑调用，供预览判断是否重建模拟器）。 */
    fun markSimChanged() {
        simVersion++
        markDirty()
    }

    fun undo() = undoManager.undo()
    fun redo() = undoManager.redo()
    fun clearHistory() = undoManager.clear()
    fun submit(command: Command) = undoManager.execute(command)

    // ---- 查询 ----

    /** 按 id 查块或算子；无则 null。 */
    fun findNode(nodeId: String): EdNodeLike? = findBlock(nodeId) ?: findOperator(nodeId)

    fun findBlock(nodeId: String): EdBlock? {
        for (ctx in contexts.values) {
            ctx.blocks[nodeId]?.let { return it }
        }
        return null
    }

    fun findOperator(nodeId: String): EdOperator? = operators[nodeId]

    /** 块/算子所属 context id（算子返回 null）。 */
    fun contextOf(nodeId: String): String? {
        for ((id, ctx) in contexts) {
            if (ctx.blocks.containsKey(nodeId)) return id
        }
        return null
    }

    /** 节点的目录端口（块/算子统一）。 */
    fun portsFor(nodeId: String): List<Port> {
        val block = findBlock(nodeId)
        val typeId = block?.typeId ?: findOperator(nodeId)?.typeId ?: return emptyList()
        val type = registry.find(typeId) ?: return emptyList()
        return type.ports().map { Port(it.id(), it.name(), it.direction(), it.type(), it.defaultValue()) }
    }

    /** 节点首个输入端口 id；无则 null。 */
    fun firstInputPort(nodeId: String): String? =
        portsFor(nodeId).firstOrNull { it.direction() == org.academy.api.client.render.graph.model.PortDirection.INPUT }
            ?.id()

    /** 节点首个输出端口 id；无则 null。 */
    fun firstOutputPort(nodeId: String): String? =
        portsFor(nodeId).firstOrNull { it.direction() == org.academy.api.client.render.graph.model.PortDirection.OUTPUT }
            ?.id()

    // ---- id 分配 ----

    fun allocateContextId(): String = "ctx${nextContextId++}"

    fun allocateNodeId(): String = "n${nextNodeId++}"

    // ---- 内部变更原语（命令与加载调用，不触发 markDirty）----

    fun createContext(id: String, type: VfxContextType, name: String, x: Float, y: Float): EdContext {
        val ctx = EdContext(id, type, name, x, y)
        contexts[id] = ctx
        return ctx
    }

    fun createBlock(contextId: String, blockId: String, typeId: String, properties: Map<String, String>): EdBlock {
        val block = EdBlock(blockId, typeId, properties.toMutableMap())
        contexts[contextId]?.blocks?.let { it[blockId] = block }
        return block
    }

    fun createOperator(id: String, typeId: String, properties: Map<String, String>, x: Float, y: Float): EdOperator {
        val op = EdOperator(id, typeId, properties.toMutableMap(), x, y)
        operators[id] = op
        return op
    }

    fun removeNodeInternal(nodeId: String) {
        for (ctx in contexts.values) {
            ctx.blocks.remove(nodeId)
        }
        operators.remove(nodeId)
        dataEdges.removeIf { it.fromNode == nodeId || it.toNode == nodeId }
        outputs.remove(nodeId)
    }

    fun removeContextInternal(contextId: String) {
        val ctx = contexts.remove(contextId) ?: return
        val blockIds = ctx.blocks.keys.toSet()
        flowEdges.removeIf { it.fromContext == contextId || it.toContext == contextId }
        dataEdges.removeIf { blockIds.contains(it.fromNode) || blockIds.contains(it.toNode) }
    }

    // ---- 公开 mutation API（全部经命令提交）----

    fun addContext(type: VfxContextType, x: Float, y: Float): EdContext {
        val id = allocateContextId()
        undoManager.execute(AddContextCommand(this, id, type, x, y))
        markSimChanged()
        return contexts[id] ?: error("context created")
    }

    fun removeContext(id: String) {
        if (!contexts.containsKey(id)) return
        undoManager.execute(RemoveContextCommand(this, id))
        markSimChanged()
    }

    fun addBlock(contextId: String, typeId: String): EdBlock {
        val id = allocateNodeId()
        undoManager.execute(AddBlockCommand(this, contextId, id, typeId))
        markSimChanged()
        return contexts[contextId]?.blocks?.get(id) ?: error("block created")
    }

    fun removeBlock(contextId: String, blockId: String) {
        if (contexts[contextId]?.blocks?.containsKey(blockId) != true) return
        undoManager.execute(RemoveBlockCommand(this, contextId, blockId))
        markSimChanged()
    }

    fun addOperator(typeId: String, x: Float, y: Float): EdOperator {
        val id = allocateNodeId()
        undoManager.execute(AddOperatorCommand(this, id, typeId, x, y))
        markSimChanged()
        return operators[id] ?: error("operator created")
    }

    fun removeOperator(id: String) {
        if (!operators.containsKey(id)) return
        undoManager.execute(RemoveOperatorCommand(this, id))
        markSimChanged()
    }

    fun connectFlow(from: String, to: String): Boolean {
        if (from == to) return false
        if (!contexts.containsKey(from) || !contexts.containsKey(to)) return false
        if (flowEdges.any { it.fromContext == from && it.toContext == to }) return false
        undoManager.execute(ConnectFlowCommand(this, from, to))
        markSimChanged()
        return true
    }

    fun disconnectFlow(from: String, to: String) {
        val edge = flowEdges.firstOrNull { it.fromContext == from && it.toContext == to } ?: return
        undoManager.execute(DisconnectFlowCommand(this, edge))
        markSimChanged()
    }

    /** 块级批次 flow：spawn 块 → init 块（M28b）。源须在 SPAWN context、目标须在 INITIALIZE context。 */
    fun connectBlockFlow(fromBlock: String, toBlock: String): Boolean {
        val fromCtx = contextOf(fromBlock)?.let { contexts[it] }
        val toCtx = contextOf(toBlock)?.let { contexts[it] }
        if (fromCtx?.type != VfxContextType.SPAWN) return false
        if (toCtx?.type != VfxContextType.INITIALIZE) return false
        if (fromBlock == toBlock) return false
        if (blockFlows.any { it.fromBlock == fromBlock && it.toBlock == toBlock }) return false
        undoManager.execute(ConnectBlockFlowCommand(this, fromBlock, toBlock))
        markSimChanged()
        return true
    }

    fun disconnectBlockFlow(fromBlock: String, toBlock: String) {
        val edge = blockFlows.firstOrNull { it.fromBlock == fromBlock && it.toBlock == toBlock } ?: return
        undoManager.execute(DisconnectBlockFlowCommand(this, edge))
        markSimChanged()
    }

    fun connectData(from: String, fromPort: String, to: String, toPort: String): Boolean {
        val fromType = portsFor(from).firstOrNull { it.id() == fromPort }
            ?.let { if (it.direction() == org.academy.api.client.render.graph.model.PortDirection.OUTPUT) it else null }
            ?: return false
        val toType = portsFor(to).firstOrNull { it.id() == toPort }
            ?.let { if (it.direction() == org.academy.api.client.render.graph.model.PortDirection.INPUT) it else null }
            ?: return false
        if (!org.academy.api.client.render.graph.type.TypeConversions.INSTANCE.canConvert(
                fromType.type(),
                toType.type()
            )
        ) {
            return false
        }
        if (dataEdges.any { it.toNode == to && it.toPort == toPort }) {
            disconnectData(to, toPort)
        }
        undoManager.execute(ConnectDataCommand(this, from, fromPort, to, toPort))
        markSimChanged()
        return true
    }

    fun disconnectData(to: String, toPort: String) {
        val edge = dataEdges.firstOrNull { it.toNode == to && it.toPort == toPort } ?: return
        undoManager.execute(DisconnectDataCommand(this, edge))
        markSimChanged()
    }

    fun setProperty(nodeId: String, propId: String, newValue: String) {
        val node = findNode(nodeId) ?: return
        val old = node.properties[propId] ?: defaultPropertyValue(node, propId) ?: return
        if (old == newValue) return
        undoManager.execute(SetContainerPropertyCommand(this, nodeId, propId, old, newValue))
        markSimChanged()
    }

    private fun defaultPropertyValue(node: EdNodeLike, propId: String): String? {
        val type = registry.find(node.typeId) ?: return null
        return type.properties().firstOrNull { it.id() == propId }?.defaultValue()?.let { propertyValueString(it) }
    }

    private fun propertyValueString(value: org.academy.api.client.render.graph.type.Value): String =
        when (value.type()) {
            org.academy.api.client.render.graph.type.ValueType.FLOAT -> value.asFloat().toString()
            org.academy.api.client.render.graph.type.ValueType.COLOR -> {
                val c = value.asColor()
                "${c.x},${c.y},${c.z},${c.w}"
            }

            else -> ""
        }

    fun moveContext(contextId: String, newX: Float, newY: Float) {
        val ctx = contexts[contextId] ?: return
        if (ctx.x == newX && ctx.y == newY) return
        undoManager.execute(MoveContextCommand(this, contextId, ctx.x, ctx.y, newX, newY))
    }

    fun moveOperator(nodeId: String, newX: Float, newY: Float) {
        val op = operators[nodeId] ?: return
        if (op.x == newX && op.y == newY) return
        undoManager.execute(MoveOperatorCommand(this, nodeId, op.x, op.y, newX, newY))
    }

    fun setOutput(nodeId: String) {
        val block = findBlock(nodeId) ?: return
        if (!block.typeId.startsWith("vfx.block.output_")) return
        undoManager.execute(SetOutputCommand(this, nodeId))
        markSimChanged()
    }

    fun addParameter(param: GraphParameter) {
        undoManager.execute(object : VfxContainerCommand(this) {
            override fun execute() {
                parameters.add(param)
            }

            override fun undo() {
                parameters.remove(param)
            }

            override fun label() = "Add parameter"
        })
        markSimChanged()
    }

    fun removeParameter(index: Int) {
        if (index !in parameters.indices) return
        val param = parameters[index]
        undoManager.execute(object : VfxContainerCommand(this) {
            override fun execute() {
                parameters.removeAt(index.coerceAtMost(parameters.size - 1))
            }

            override fun undo() {
                parameters.add(index.coerceAtMost(parameters.size), param)
            }

            override fun label() = "Remove parameter"
        })
        markSimChanged()
    }

    fun reset() {
        contexts.clear()
        operators.clear()
        flowEdges.clear()
        blockFlows.clear()
        dataEdges.clear()
        parameters.clear()
        outputs.clear()
        nextContextId = 0
        nextNodeId = 0
        clearHistory()
        markSimChanged()
    }

    // ---- 桥接核心模型 ----

    fun toSystem(): VfxSystem {
        val ctxList = contexts.values.map { ctx ->
            val blocks = ctx.blocks.values.map { block ->
                VfxBlock(block.id, block.typeId, block.properties.toMap(), portsFor(block.id))
            }
            VfxContext(ctx.id, ctx.type, ctx.name, blocks, ctx.x, ctx.y)
        }
        val opList = operators.values.map { op ->
            VfxOperatorNode(op.id, op.typeId, op.properties.toMap(), portsFor(op.id), op.x, op.y)
        }
        val flowList = flowEdges.map { VfxFlowEdge(it.fromContext, it.toContext) }
        val blockFlowList = blockFlows.map { VfxBlockFlowEdge(it.fromBlock, it.toBlock) }
        val dataList = dataEdges.map {
            VfxDataEdge(
                org.academy.api.client.render.graph.model.Edge.PortRef(it.fromNode, it.fromPort),
                org.academy.api.client.render.graph.model.Edge.PortRef(it.toNode, it.toPort),
            )
        }
        return VfxSystem(
            "editor",
            ctxList,
            opList,
            flowList,
            blockFlowList,
            dataList,
            parameters.toList(),
            outputs.toList()
        )
    }

    fun load(system: VfxSystem) {
        contexts.clear()
        operators.clear()
        flowEdges.clear()
        dataEdges.clear()
        parameters.clear()
        outputs.clear()
        for (ctx in system.contexts()) {
            val ed = createContext(ctx.id(), ctx.type(), ctx.name(), ctx.x(), ctx.y())
            for (block in ctx.blocks()) {
                ed.blocks[block.id()] = EdBlock(block.id(), block.type(), block.properties().toMutableMap())
            }
            nextContextId = maxOf(nextContextId, ctx.id().removePrefix("ctx").toIntOrNull()?.plus(1) ?: 0)
        }
        for (op in system.operators()) {
            operators[op.id()] = EdOperator(op.id(), op.type(), op.properties().toMutableMap(), op.x(), op.y())
        }
        for (edge in system.flowEdges()) {
            flowEdges.add(EdFlowEdge(edge.fromContextId(), edge.toContextId()))
        }
        for (edge in system.blockFlows()) {
            blockFlows.add(EdBlockFlowEdge(edge.fromBlockId(), edge.toBlockId()))
        }
        for (edge in system.dataEdges()) {
            dataEdges.add(
                EdDataEdge(
                    edge.from().nodeId(),
                    edge.from().portId(),
                    edge.to().nodeId(),
                    edge.to().portId()
                )
            )
        }
        parameters.addAll(system.parameters())
        outputs.addAll(system.outputs())
        nextNodeId = (system.nodes().mapNotNull { it.id().removePrefix("n").toIntOrNull() }.maxOrNull() ?: -1) + 1
        clearHistory()
        markSimChanged()
    }
}
