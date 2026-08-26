package org.academy.desktop.grapheditor.container

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.vfxgraph.model.VfxContextType
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry
import org.academy.api.client.render.vfxgraph.operator.VfxOperators
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VfxContainerModelTest {

    private fun containerRegistry(): SimpleNodeRegistry {
        val r = SimpleNodeRegistry()
        VfxBlocks.registerAll(r, VfxBlockRegistry())
        VfxOperators.registerAll(r, VfxOperatorRegistry())
        return r
    }

    @Test
    fun addContextsAndBlocksProducesSystem() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val block = model.addBlock(spawn.id, "vfx.block.spawn_rate")
        model.setProperty(block.id, "rate", "20")

        val system = model.toSystem()
        assertEquals(1, system.contexts().size)
        assertEquals(VfxContextType.SPAWN, system.contexts()[0].type())
        assertEquals(1, system.contexts()[0].blocks().size)
        assertEquals("20", system.contexts()[0].blocks()[0].properties()["rate"])
    }

    @Test
    fun undoRedoContextRemoval() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        model.removeContext(spawn.id)
        assertTrue(model.contexts.isEmpty())
        model.undo()
        assertEquals(1, model.contexts.size)
        model.redo()
        assertTrue(model.contexts.isEmpty())
    }

    /** 回归：连续多次操作可逐步撤销（撤销不应只生效一次）。 */
    @Test
    fun undoStepsThroughMultipleOperations() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val b1 = model.addBlock(spawn.id, "vfx.block.spawn_rate")
        val b2 = model.addBlock(spawn.id, "vfx.block.spawn_burst")

        assertEquals(2, model.contexts[spawn.id]!!.blocks.size)
        model.undo()
        assertEquals(1, model.contexts[spawn.id]!!.blocks.size)
        model.undo()
        assertEquals(0, model.contexts[spawn.id]!!.blocks.size)
        model.undo()
        assertTrue(model.contexts.isEmpty())

        model.redo()
        assertEquals(1, model.contexts.size)
        model.redo()
        assertEquals(1, model.contexts[spawn.id]!!.blocks.size)
        model.redo()
        assertEquals(2, model.contexts[spawn.id]!!.blocks.size)
        assertEquals(listOf(b1.id, b2.id), model.contexts[spawn.id]!!.blocks.keys.toList())
    }

    @Test
    fun undoRedoBlockRemoval() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val block = model.addBlock(spawn.id, "vfx.block.spawn_rate")
        model.setProperty(block.id, "rate", "30")
        model.removeBlock(spawn.id, block.id)
        assertTrue(model.contexts[spawn.id]!!.blocks.isEmpty())
        model.undo()
        assertEquals("30", model.contexts[spawn.id]!!.blocks[block.id]!!.properties["rate"])
    }

    @Test
    fun connectFlowAndData() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val init = model.addContext(VfxContextType.INITIALIZE, 200f, 0f)
        assertTrue(model.connectFlow(spawn.id, init.id))
        // 重复连接拒绝
        assertFalse(model.connectFlow(spawn.id, init.id))
        // 自环拒绝
        assertFalse(model.connectFlow(spawn.id, spawn.id))

        val op = model.addOperator("vfx.op.attr_seed", 400f, 0f)
        val block = model.addBlock(init.id, "vfx.block.init_velocity")
        val outPort = model.firstOutputPort(op.id)!!
        val inPort = model.firstInputPort(block.id)!!
        assertTrue(model.connectData(op.id, outPort, block.id, inPort))
        // 同目标端口重复连接会先断再连
        assertTrue(model.connectData(op.id, outPort, block.id, inPort))
        assertEquals(1, model.dataEdges.size)
    }

    @Test
    fun dataEdgeToMissingPortRejected() {
        val model = VfxContainerModel(containerRegistry())
        val init = model.addContext(VfxContextType.INITIALIZE, 0f, 0f)
        val op = model.addOperator("vfx.op.constant", 400f, 0f)
        val block = model.addBlock(init.id, "vfx.block.init_velocity")
        // 目标端口不存在 → 拒绝
        assertFalse(model.connectData(op.id, "out", block.id, "nonexistent"))
        // 源端口不存在 → 拒绝
        assertFalse(model.connectData(op.id, "nope", block.id, "vx"))
    }

    @Test
    fun moveOperatorMergeAndUndo() {
        val model = VfxContainerModel(containerRegistry())
        val op = model.addOperator("vfx.op.constant", 0f, 0f)
        model.moveOperator(op.id, 10f, 10f)
        model.moveOperator(op.id, 20f, 20f)
        assertEquals(20f, model.operators[op.id]!!.x)
        model.undo()
        assertEquals(0f, model.operators[op.id]!!.x)
    }

    @Test
    fun loadRoundTripsSystem() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        model.addBlock(spawn.id, "vfx.block.spawn_rate")
        val out = model.addContext(VfxContextType.OUTPUT, 400f, 0f)
        val outBlock = model.addBlock(out.id, "vfx.block.output_quad")
        model.setOutput(outBlock.id)
        model.connectFlow(spawn.id, out.id)

        val system = model.toSystem()
        val loaded = VfxContainerModel(containerRegistry())
        loaded.load(system)
        val system2 = loaded.toSystem()
        assertEquals(system.contexts().size, system2.contexts().size)
        assertEquals(system.flowEdges(), system2.flowEdges())
        assertEquals(system.outputs(), system2.outputs())
    }

    @Test
    fun setOutputOnlyAcceptsOutputBlocks() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val block = model.addBlock(spawn.id, "vfx.block.spawn_rate")
        model.setOutput(block.id)
        assertTrue(model.outputs.isEmpty())
    }

    /** M21n 多输出：setOutput 为切换语义，可多个输出块共存，撤销恢复。 */
    @Test
    fun setOutputTogglesMultipleOutputs() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val out = model.addContext(VfxContextType.OUTPUT, 400f, 0f)
        val a = model.addBlock(out.id, "vfx.block.output_quad_glow")
        val b = model.addBlock(out.id, "vfx.block.output_quad")

        model.setOutput(a.id)
        model.setOutput(b.id)
        assertEquals(listOf(a.id, b.id), model.outputs.toList())

        // 切换掉 a
        model.setOutput(a.id)
        assertEquals(listOf(b.id), model.outputs.toList())

        // 撤销两次：先恢复 a（cmd3 undo），再撤销 setOutput(b)（cmd2 undo）→ 剩 [a]
        model.undo()
        model.undo()
        assertEquals(listOf(a.id), model.outputs.toList())

        // 第三次撤销 → 回到空（setOutput(a) 也撤销）
        model.undo()
        assertTrue(model.outputs.isEmpty())
    }

    /** M28b 块级批次 flow：spawn→init 配对 + 类型约束 + 撤销。 */
    @Test
    fun connectBlockFlowPairsSpawnToInit() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val init = model.addContext(VfxContextType.INITIALIZE, 300f, 0f)
        val s = model.addBlock(spawn.id, "vfx.block.spawn_rate")
        val i = model.addBlock(init.id, "vfx.block.init_velocity")

        assertTrue(model.connectBlockFlow(s.id, i.id))
        assertEquals(1, model.blockFlows.size)
        // 类型约束：spawn→spawn 拒绝；init→init 拒绝
        val s2 = model.addBlock(spawn.id, "vfx.block.spawn_burst")
        assertFalse(model.connectBlockFlow(s.id, s2.id))
        val i2 = model.addBlock(init.id, "vfx.block.init_color")
        assertFalse(model.connectBlockFlow(i.id, i2.id))
        // 撤销 flow 连线（栈顶可能还有 addBlock，需撤到 flow 为止）
        while (model.blockFlows.size > 0) {
            model.undo()
        }
        assertEquals(0, model.blockFlows.size)
        model.redo()
        assertEquals(1, model.blockFlows.size)
    }

    /** M28b 断线：disconnectBlockFlow / disconnectData 移除对应边（右键断线路径）。 */
    @Test
    fun disconnectEdgesRemovesConnections() {
        val model = VfxContainerModel(containerRegistry())
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        val init = model.addContext(VfxContextType.INITIALIZE, 300f, 0f)
        val s = model.addBlock(spawn.id, "vfx.block.spawn_rate")
        val i = model.addBlock(init.id, "vfx.block.init_velocity")
        model.connectBlockFlow(s.id, i.id)
        assertEquals(1, model.blockFlows.size)

        model.disconnectBlockFlow(s.id, i.id)
        assertEquals(0, model.blockFlows.size)
        // 撤销恢复
        model.undo()
        assertEquals(1, model.blockFlows.size)

        // data edge 断线
        val op = model.addOperator("vfx.op.constant", 600f, 0f)
        val outPort = model.firstOutputPort(op.id)!!
        val inPort = model.firstInputPort(i.id)!!
        model.connectData(op.id, outPort, i.id, inPort)
        assertEquals(1, model.dataEdges.size)
        model.disconnectData(i.id, inPort)
        assertEquals(0, model.dataEdges.size)
    }
}
