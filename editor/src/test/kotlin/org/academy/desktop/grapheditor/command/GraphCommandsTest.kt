package org.academy.desktop.grapheditor.command

import org.academy.desktop.grapheditor.EditorTestFixtures
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 命令级测试：label 文案、合并协议（同 key 合并 / 异类拒绝）。
 */
class GraphCommandsTest {

    @Test
    fun commandsReportLabels() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 0f, 0f)
        assertEquals("Add node", AddNodeCommand(model, "math.add", 0f, 0f).label())
        assertEquals("Delete node", RemoveNodeCommand(model, node.id).label())
        assertEquals("Connect", ConnectCommand(model, node.id, "out", node.id, "a").label())
        assertEquals("Disconnect", DisconnectCommand(model, node.id, "a").label())
        assertEquals("Reconnect", ReconnectCommand(model, node.id, "out", node.id, "a").label())
        assertEquals("Set output", SetOutputCommand(model, node.id).label())
        assertTrue(MoveNodeCommand(model, node.id, 0f, 0f, 1f, 1f).label().startsWith("Move node"))
        assertTrue(SetPropertyCommand(model, node.id, "value", "0", "1").label().startsWith("Set property"))
    }

    @Test
    fun moveMergeRejectsDifferentNode() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 0f, 0f)
        val first = MoveNodeCommand(model, a.id, 0f, 0f, 1f, 1f)
        val different = MoveNodeCommand(model, b.id, 0f, 0f, 2f, 2f)
        assertNull(first.mergeWith(different))
    }

    @Test
    fun moveMergeKeepsOldEndpoint() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 0f, 0f)
        val first = MoveNodeCommand(model, node.id, 0f, 0f, 1f, 1f)
        val next = MoveNodeCommand(model, node.id, 1f, 1f, 2f, 2f)
        val merged = first.mergeWith(next)
        assertNotNull(merged)
        merged!!.execute()
        assertEquals(2f, model.nodes[node.id]!!.x)
        assertEquals(2f, model.nodes[node.id]!!.y)
        merged.undo()
        assertEquals(0f, model.nodes[node.id]!!.x)
    }

    @Test
    fun propertyMergeKeepsOldValue() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 0f, 0f)
        val first = SetPropertyCommand(model, node.id, "value", "0", "1")
        val next = SetPropertyCommand(model, node.id, "value", "1", "2")
        val merged = first.mergeWith(next)
        assertNotNull(merged)
        merged!!.execute()
        assertEquals("2", model.nodes[node.id]!!.properties["value"])
        merged.undo()
        assertEquals("0", model.nodes[node.id]!!.properties["value"])
    }

    @Test
    fun propertyMergeRejectsDifferentProp() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val node = model.addNode("input.constant", 0f, 0f)
        val first = SetPropertyCommand(model, node.id, "value", "0", "1")
        val different = SetPropertyCommand(model, node.id, "other", "0", "1")
        assertNull(first.mergeWith(different))
    }
}
