package org.academy.desktop.grapheditor.canvas

import org.academy.api.client.render.graph.model.GraphParameter
import org.academy.api.client.render.graph.type.Value
import org.academy.api.client.render.graph.type.ValueType
import org.academy.desktop.grapheditor.EditorTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

/**
 * 模型命令化端到端测试：所有公开 mutation 均可撤销/重做，且 undo 后 toGraph 与操作前一致。
 */
class GraphEditorModelUndoTest {

    @Test
    fun addNodeUndoRedo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        model.addNode("input.constant", 10f, 20f)
        assertEquals(1, model.nodes.size)

        model.undo()
        assertTrue(model.nodes.isEmpty())

        model.redo()
        assertEquals(1, model.nodes.size)
        val node = model.nodes.values.first()
        assertEquals("input.constant", node.typeId)
        assertEquals(10f, node.x)
        assertEquals(20f, node.y)
    }

    @Test
    fun addNodeUndoRestoresToGraph() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val before = model.toGraph()
        model.addNode("input.constant", 10f, 20f)
        model.undo()
        assertEquals(before, model.toGraph())
    }

    @Test
    fun connectUndoRestoresOverwrittenEdge() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 0f, 0f)

        assertTrue(model.connect(a.id, "out", add.id, "a"))
        assertTrue(model.connect(b.id, "out", add.id, "a"))
        assertEquals(b.id, model.edges["${add.id}:a"]!!.fromNode)

        model.undo()
        assertEquals(a.id, model.edges["${add.id}:a"]!!.fromNode)
        model.undo()
        assertNull(model.edges["${add.id}:a"])
        model.redo()
        assertTrue(model.edges.containsKey("${add.id}:a"))
        model.redo()
        assertEquals(b.id, model.edges["${add.id}:a"]!!.fromNode)
    }

    @Test
    fun disconnectRestoresEdge() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 0f, 0f)
        model.connect(a.id, "out", add.id, "a")
        assertTrue(model.edges.containsKey("${add.id}:a"))

        model.disconnect(add.id, "a")
        assertTrue(model.edges.isEmpty())

        model.undo()
        assertEquals(a.id, model.edges["${add.id}:a"]!!.fromNode)
    }

    @Test
    fun reconnectSwapsEdge() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 0f, 0f)
        model.connect(a.id, "out", add.id, "a")

        assertTrue(model.reconnect(b.id, "out", add.id, "a"))
        assertEquals(b.id, model.edges["${add.id}:a"]!!.fromNode)

        model.undo()
        assertEquals(a.id, model.edges["${add.id}:a"]!!.fromNode)
    }

    @Test
    fun removeNodeRestoresEdgesAndOutput() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 0f, 0f)
        model.connect(a.id, "out", add.id, "a")
        model.setOutput(a.id)

        model.removeNode(a.id)
        assertEquals(1, model.nodes.size)
        assertTrue(model.edges.isEmpty())
        assertTrue(model.outputNodeIds.isEmpty())

        model.undo()
        assertEquals(2, model.nodes.size)
        assertEquals(a.id, model.outputNodeIds.single())
        assertEquals(a.id, model.edges["${add.id}:a"]!!.fromNode)
    }

    @Test
    fun setOutputRestoresPrevious() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        model.setOutput(a.id)
        model.setOutput(b.id)
        assertEquals(listOf(b.id), model.outputNodeIds)

        model.undo()
        assertEquals(listOf(a.id), model.outputNodeIds)
        model.undo()
        assertTrue(model.outputNodeIds.isEmpty())
    }

    @Test
    fun moveNodeCommandsMergeIntoSingleUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 0f, 0f)
        model.moveNode(node.id, 5f, 5f)
        model.moveNode(node.id, 10f, 10f)
        model.moveNode(node.id, 20f, 30f)
        assertEquals(20f, model.nodes[node.id]!!.x)
        assertEquals(30f, model.nodes[node.id]!!.y)

        model.undo()
        assertEquals(0f, model.nodes[node.id]!!.x)
        assertEquals(0f, model.nodes[node.id]!!.y)

        model.redo()
        assertEquals(20f, model.nodes[node.id]!!.x)
    }

    @Test
    fun moveUndoRestoresToGraph() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 3f, 4f)
        val before = model.toGraph()
        model.moveNode(node.id, 10f, 10f)
        model.moveNode(node.id, 20f, 20f)
        model.undo()
        assertEquals(before, model.toGraph())
    }

    @Test
    fun propertyEditsMergeIntoSingleUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 0f, 0f)
        model.setProperty(node.id, "value", "1.5")
        model.setProperty(node.id, "value", "2.5")
        model.setProperty(node.id, "value", "3.5")
        assertEquals("3.5", model.nodes[node.id]!!.properties["value"])

        model.undo()
        assertEquals("0.0", model.nodes[node.id]!!.properties["value"])
        model.redo()
        assertEquals("3.5", model.nodes[node.id]!!.properties["value"])
    }

    @Test
    fun removeNodesIsSingleUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        model.removeNodes(listOf(a.id, b.id))
        assertTrue(model.nodes.isEmpty())

        model.undo()
        assertEquals(2, model.nodes.size)
        assertTrue(model.edges.isEmpty())

        model.redo()
        assertTrue(model.nodes.isEmpty())
    }

    @Test
    fun parameterAddRemoveUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val param = GraphParameter("p1", "P1", ValueType.FLOAT, Value.of(1f), Optional.empty())

        model.addParameter(param)
        assertEquals(1, model.parameters.size)
        model.undo()
        assertTrue(model.parameters.isEmpty())
        model.redo()
        assertEquals("p1", model.parameters[0].id())

        model.removeParameter(0)
        assertTrue(model.parameters.isEmpty())
        model.undo()
        assertEquals(1, model.parameters.size)
    }

    @Test
    fun replaceParameterUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        model.addParameter(GraphParameter("p1", "P1", ValueType.FLOAT, Value.of(1f), Optional.empty()))

        model.replaceParameter(0, GraphParameter("p1", "P1", ValueType.FLOAT, Value.of(5f), Optional.empty()))
        assertEquals(5f, model.parameters[0].defaultValue().asFloat())

        model.undo()
        assertEquals(1f, model.parameters[0].defaultValue().asFloat())
    }

    @Test
    fun versionBumpsOnUndoRedo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val v0 = model.version
        val node = model.addNode("input.constant", 0f, 0f)
        val v1 = model.version
        assertTrue(v1 > v0)
        model.undo()
        assertTrue(model.version > v1)
        model.redo()
        assertTrue(model.version > model.version - 1)
        assertEquals(1, model.nodes.size)
        assertEquals(node.id, model.nodes.values.first().id)
    }

    @Test
    fun resetClearsDocumentAndHistory() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        model.addNode("input.constant", 0f, 0f)
        model.addNode("math.add", 0f, 0f)
        assertTrue(model.canUndo)

        model.reset()
        assertTrue(model.nodes.isEmpty())
        assertTrue(model.edges.isEmpty())
        assertFalse(model.canUndo)
        assertFalse(model.canRedo)
    }

    @Test
    fun loadClearsHistory() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        model.addNode("input.constant", 0f, 0f)
        assertTrue(model.canUndo)

        model.load(model.toGraph())
        assertFalse(model.canUndo)
        assertFalse(model.canRedo)
        assertEquals(1, model.nodes.size)
    }

    @Test
    fun invalidConnectRejectedWithoutHistory() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        val version = model.version
        // constant.out 是 OUTPUT，不能连到 OUTPUT
        assertFalse(model.connect(a.id, "out", b.id, "out"))
        assertTrue(model.edges.isEmpty())
        assertEquals(version, model.version)
    }

    @Test
    fun reorderNodeChangesExecutionOrderAndUndoes() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        val c = model.addNode("input.constant", 0f, 0f)
        assertEquals(listOf(a.id, b.id, c.id), model.nodes.keys.toList())

        model.moveNodeExecutionOrder(b.id, 1) // b 后移一位 → a, c, b
        assertEquals(listOf(a.id, c.id, b.id), model.nodes.keys.toList())

        model.undo()
        assertEquals(listOf(a.id, b.id, c.id), model.nodes.keys.toList())

        model.redo()
        assertEquals(listOf(a.id, c.id, b.id), model.nodes.keys.toList())
    }

    @Test
    fun reorderNodeClampsToBoundsAndIgnoresUnknown() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        model.moveNodeExecutionOrder(a.id, -100) // 顶格不动
        assertEquals(listOf(a.id, b.id), model.nodes.keys.toList())
        val version = model.version
        model.moveNodeExecutionOrder("nope", 1) // 未知节点无操作
        assertEquals(version, model.version)
        model.reorderNode(b.id, 100) // 越界钳到末尾
        assertEquals(listOf(a.id, b.id), model.nodes.keys.toList())
    }
}
