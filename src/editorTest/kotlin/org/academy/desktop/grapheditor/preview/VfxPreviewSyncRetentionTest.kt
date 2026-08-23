package org.academy.desktop.grapheditor.preview

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.vfxgraph.model.VfxBlock
import org.academy.api.client.render.vfxgraph.model.VfxContext
import org.academy.api.client.render.vfxgraph.model.VfxContextType
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge
import org.academy.api.client.render.vfxgraph.model.VfxSystem
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry
import org.academy.api.client.render.vfxgraph.operator.VfxOperators
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.canvas.GraphEditorModelRef
import org.academy.desktop.grapheditor.container.VfxContainerModel
import org.academy.desktop.grapheditor.container.VfxContainerModelRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 回归（2026-08-15）：VfxPreview.sync 版本守卫在清空模拟器之前——
 * 版本未变时必须保留现有模拟器，否则每帧清空但不重建 → 编辑器视口冻结第一帧。
 */
class VfxPreviewSyncRetentionTest {

    private fun containerModel(): VfxContainerModel {
        val registry = SimpleNodeRegistry()
        VfxBlocks.registerAll(registry, VfxBlockRegistry())
        VfxOperators.registerAll(registry, VfxOperatorRegistry())
        val model = VfxContainerModel(registry)
        val spawn = model.addContext(VfxContextType.SPAWN, 0f, 0f)
        model.addBlock(spawn.id, "vfx.block.spawn_burst")
        return model
    }

    @Test
    fun syncRetainsSimulatorWhenVersionUnchanged() {
        val registry = SimpleNodeRegistry()
        val blocks = VfxBlockRegistry()
        val ops = VfxOperatorRegistry()
        VfxBlocks.registerAll(registry, blocks)
        VfxOperators.registerAll(registry, ops)

        val graphModel = GraphEditorModel(registry)
        val graphRef = GraphEditorModelRef(graphModel)
        val container = containerModel()
        val containerRef = VfxContainerModelRef(container)
        val preview = VfxPreview(
            graphRef, VfxNodeRegistry(), containerRef,
            blocks, ops,
        )

        // 第一次 sync：构建容器模拟器，stepOnce 后产生粒子
        preview.sync()
        preview.stepOnce()
        val afterFirst = preview.particleCount
        assertTrue(afterFirst > 0, "container simulator should spawn on stepOnce, got $afterFirst")

        // 版本未变，再次 sync：必须保留模拟器（bug：清空但不重建 → 后续 step 无效）
        preview.sync()
        preview.stepOnce()
        val afterSecond = preview.particleCount
        assertTrue(afterSecond > 0, "simulator must survive unchanged-version sync, got $afterSecond")
    }

    /**
     * 回归（2026-08-15）：移动节点（布局变更）不重建模拟器 → loop 播放不被打断。
     * 移动只增 version，不增 simVersion；增删/连线/属性才增 simVersion。
     */
    @Test
    fun movingNodeDoesNotRebuildSimulator() {
        val registry = SimpleNodeRegistry()
        val blocks = VfxBlockRegistry()
        val ops = VfxOperatorRegistry()
        VfxBlocks.registerAll(registry, blocks)
        VfxOperators.registerAll(registry, ops)

        val graphModel = GraphEditorModel(registry)
        val graphRef = GraphEditorModelRef(graphModel)
        val container = VfxContainerModel(registry)
        val spawn = container.addContext(VfxContextType.SPAWN, 0f, 0f)
        container.addBlock(spawn.id, "vfx.block.spawn_burst")
        val op = container.addOperator("vfx.op.constant", 300f, 0f)
        val containerRef = VfxContainerModelRef(container)
        val preview = VfxPreview(
            graphRef, VfxNodeRegistry(), containerRef,
            blocks, ops,
        )

        preview.sync()
        preview.stepOnce()
        assertTrue(preview.particleCount > 0)

        // 移动节点：simVersion 不变，sync 不重建（粒子保留）
        val simBefore = container.simVersion
        container.moveOperator(op.id, 400f, 100f)
        container.moveContext(spawn.id, 50f, 60f)
        assertEquals(simBefore, container.simVersion, "layout move must not bump simVersion")
        preview.sync()
        preview.stepOnce()
        assertTrue(preview.particleCount > 0, "moving must not reset simulation")

        // 内容变更：simVersion 递增，sync 重建
        val simAfter = container.simVersion
        container.addBlock(spawn.id, "vfx.block.spawn_burst")
        assertTrue(container.simVersion > simAfter, "adding a block must bump simVersion")
    }
}
