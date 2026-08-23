package org.academy.desktop.grapheditor.container.command

import org.academy.api.client.render.vfxgraph.model.VfxContextType
import org.academy.desktop.grapheditor.command.Command
import org.academy.desktop.grapheditor.container.VfxContainerModel

/** 以容器模型为宿主的命令基类。 */
abstract class VfxContainerCommand(protected val model: VfxContainerModel) : Command

class AddContextCommand(
    model: VfxContainerModel,
    private val contextId: String,
    private val type: VfxContextType,
    private val x: Float,
    private val y: Float,
) : VfxContainerCommand(model) {
    override fun execute() {
        if (!model.contexts.containsKey(contextId)) {
            model.createContext(contextId, type, "", x, y)
        }
    }

    override fun undo() {
        model.removeContextInternal(contextId)
    }

    override fun label(): String = "Add ${type.name} context"
}

class RemoveContextCommand(
    model: VfxContainerModel,
    private val contextId: String,
) : VfxContainerCommand(model) {
    private var snapshot: VfxContainerModel.EdContext? = null

    override fun execute() {
        val ctx = model.contexts[contextId] ?: return
        snapshot = ctx
        model.removeContextInternal(contextId)
    }

    override fun undo() {
        val ctx = snapshot ?: return
        model.contexts[contextId] = ctx
        model.markDirty()
    }

    override fun label(): String = "Delete context"
}

class AddBlockCommand(
    model: VfxContainerModel,
    private val contextId: String,
    private val blockId: String,
    private val typeId: String,
) : VfxContainerCommand(model) {
    override fun execute() {
        val ctx = model.contexts[contextId] ?: return
        if (!ctx.blocks.containsKey(blockId)) {
            ctx.blocks[blockId] = VfxContainerModel.EdBlock(blockId, typeId, mutableMapOf())
        }
    }

    override fun undo() {
        model.contexts[contextId]?.blocks?.remove(blockId)
    }

    override fun label(): String = "Add block"
}

class RemoveBlockCommand(
    model: VfxContainerModel,
    private val contextId: String,
    private val blockId: String,
) : VfxContainerCommand(model) {
    private var snapshot: VfxContainerModel.EdBlock? = null

    override fun execute() {
        val ctx = model.contexts[contextId] ?: return
        snapshot = ctx.blocks[blockId]
        ctx.blocks.remove(blockId)
        model.dataEdges.removeIf { it.fromNode == blockId || it.toNode == blockId }
        model.outputs.remove(blockId)
    }

    override fun undo() {
        val ctx = model.contexts[contextId] ?: return
        val block = snapshot ?: return
        ctx.blocks[blockId] = block
        model.markDirty()
    }

    override fun label(): String = "Delete block"
}

class AddOperatorCommand(
    model: VfxContainerModel,
    private val operatorId: String,
    private val typeId: String,
    private val x: Float,
    private val y: Float,
) : VfxContainerCommand(model) {
    override fun execute() {
        if (!model.operators.containsKey(operatorId)) {
            model.createOperator(operatorId, typeId, emptyMap(), x, y)
        }
    }

    override fun undo() {
        model.operators.remove(operatorId)
    }

    override fun label(): String = "Add operator"
}

class RemoveOperatorCommand(
    model: VfxContainerModel,
    private val operatorId: String,
) : VfxContainerCommand(model) {
    private var snapshot: VfxContainerModel.EdOperator? = null

    override fun execute() {
        snapshot = model.operators[operatorId]
        model.removeNodeInternal(operatorId)
    }

    override fun undo() {
        val op = snapshot ?: return
        model.operators[operatorId] = op
        model.markDirty()
    }

    override fun label(): String = "Delete operator"
}

class ConnectFlowCommand(
    model: VfxContainerModel,
    private val from: String,
    private val to: String,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.flowEdges.add(VfxContainerModel.EdFlowEdge(from, to))
    }

    override fun undo() {
        model.flowEdges.removeIf { it.fromContext == from && it.toContext == to }
    }

    override fun label(): String = "Connect flow"
}

class ConnectBlockFlowCommand(
    model: VfxContainerModel,
    private val from: String,
    private val to: String,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.blockFlows.add(VfxContainerModel.EdBlockFlowEdge(from, to))
    }

    override fun undo() {
        model.blockFlows.removeIf { it.fromBlock == from && it.toBlock == to }
    }

    override fun label(): String = "Connect block flow"
}

class DisconnectBlockFlowCommand(
    model: VfxContainerModel,
    private val edge: VfxContainerModel.EdBlockFlowEdge,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.blockFlows.remove(edge)
    }

    override fun undo() {
        model.blockFlows.add(edge)
    }

    override fun label(): String = "Disconnect block flow"
}

class DisconnectFlowCommand(
    model: VfxContainerModel,
    private val edge: VfxContainerModel.EdFlowEdge,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.flowEdges.remove(edge)
    }

    override fun undo() {
        model.flowEdges.add(edge)
    }

    override fun label(): String = "Disconnect flow"
}

class ConnectDataCommand(
    model: VfxContainerModel,
    private val from: String,
    private val fromPort: String,
    private val to: String,
    private val toPort: String,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.dataEdges.add(VfxContainerModel.EdDataEdge(from, fromPort, to, toPort))
    }

    override fun undo() {
        model.dataEdges.removeIf { it.toNode == to && it.toPort == toPort }
    }

    override fun label(): String = "Connect data"
}

class DisconnectDataCommand(
    model: VfxContainerModel,
    private val edge: VfxContainerModel.EdDataEdge,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.dataEdges.remove(edge)
    }

    override fun undo() {
        model.dataEdges.add(edge)
    }

    override fun label(): String = "Disconnect data"
}

class SetContainerPropertyCommand(
    model: VfxContainerModel,
    private val nodeId: String,
    private val propId: String,
    private val oldValue: String,
    private val newValue: String,
) : VfxContainerCommand(model) {
    override fun execute() {
        model.findNode(nodeId)?.properties?.set(propId, newValue)
    }

    override fun undo() {
        model.findNode(nodeId)?.properties?.set(propId, oldValue)
    }

    override fun label(): String = "Set property"

    override fun mergeKey(): String? = "prop:$nodeId:$propId"

    override fun mergeWith(next: Command): Command? {
        if (next !is SetContainerPropertyCommand) return null
        if (next.nodeId != nodeId || next.propId != propId) return null
        return SetContainerPropertyCommand(model, nodeId, propId, oldValue, next.newValue)
    }
}

class MoveContextCommand(
    model: VfxContainerModel,
    private val contextId: String,
    private val oldX: Float,
    private val oldY: Float,
    private val newX: Float,
    private val newY: Float,
) : VfxContainerCommand(model) {
    override fun execute() {
        val ctx = model.contexts[contextId] ?: return
        ctx.x = newX
        ctx.y = newY
    }

    override fun undo() {
        val ctx = model.contexts[contextId] ?: return
        ctx.x = oldX
        ctx.y = oldY
    }

    override fun label(): String = "Move context"

    override fun mergeKey(): String? = "moveCtx:$contextId"

    override fun mergeWith(next: Command): Command? {
        if (next !is MoveContextCommand) return null
        if (next.contextId != contextId) return null
        return MoveContextCommand(model, contextId, oldX, oldY, next.newX, next.newY)
    }
}

class MoveOperatorCommand(
    model: VfxContainerModel,
    private val nodeId: String,
    private val oldX: Float,
    private val oldY: Float,
    private val newX: Float,
    private val newY: Float,
) : VfxContainerCommand(model) {
    override fun execute() {
        val op = model.operators[nodeId] ?: return
        op.x = newX
        op.y = newY
    }

    override fun undo() {
        val op = model.operators[nodeId] ?: return
        op.x = oldX
        op.y = oldY
    }

    override fun label(): String = "Move operator"

    override fun mergeKey(): String? = "moveOp:$nodeId"

    override fun mergeWith(next: Command): Command? {
        if (next !is MoveOperatorCommand) return null
        if (next.nodeId != nodeId) return null
        return MoveOperatorCommand(model, nodeId, oldX, oldY, next.newX, next.newY)
    }
}

class SetOutputCommand(
    model: VfxContainerModel,
    private val nodeId: String,
) : VfxContainerCommand(model) {
    private var wasPresent = false

    override fun execute() {
        // 切换语义（M21n 多输出）：已在输出集则移除，否则加入（可多个输出块共存）
        wasPresent = model.outputs.remove(nodeId)
        if (!wasPresent) {
            model.outputs.add(nodeId)
        }
    }

    override fun undo() {
        if (wasPresent) {
            model.outputs.add(nodeId)
        } else {
            model.outputs.remove(nodeId)
        }
    }

    override fun label(): String = if (wasPresent) "Remove from outputs" else "Set as output"
}
