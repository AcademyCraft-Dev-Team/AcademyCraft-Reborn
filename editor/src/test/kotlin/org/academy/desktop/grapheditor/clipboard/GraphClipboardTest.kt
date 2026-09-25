package org.academy.desktop.grapheditor.clipboard

import org.academy.desktop.grapheditor.EditorTestFixtures
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphClipboardTest {

    @Test
    fun copyEmptySelectionReturnsNull() {
        val registry = EditorTestFixtures.registry()
        val model = GraphEditorModel(registry)
        val clipboard = GraphClipboard(registry)
        assertNull(clipboard.copy(model, emptySet()))
    }

    @Test
    fun copyPasteRoundTripPreservesNodesAndInternalEdges() {
        val registry = EditorTestFixtures.registry()
        val model = GraphEditorModel(registry)
        val clipboard = GraphClipboard(registry)
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 10f, 10f)
        model.connect(a.id, "out", add.id, "a")

        val snippet = clipboard.copy(model, setOf(a.id, add.id))
        assertNotNull(snippet)

        val model2 = GraphEditorModel(registry)
        val newIds = clipboard.pasteAt(model2, snippet, 0f, 0f)
        assertEquals(2, newIds.size)
        assertEquals(2, model2.nodes.size)
        assertEquals(1, model2.edges.size)
        val edge = model2.edges.values.first()
        assertTrue(edge.fromNode in newIds)
        assertTrue(edge.toNode in newIds)
    }

    @Test
    fun copySkipsEdgesCrossingSelectionBoundary() {
        val registry = EditorTestFixtures.registry()
        val model = GraphEditorModel(registry)
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 10f, 10f)
        model.connect(a.id, "out", add.id, "a")

        // 只复制 a，其边连到未选中的 add，应被丢弃
        val snippet = clipboardFrom(registry).copy(model, setOf(a.id))
        val graph = clipboardFrom(registry).decode(snippet)
        assertNotNull(graph)
        assertTrue(graph!!.edges().isEmpty())
    }

    @Test
    fun pasteIsSingleUndo() {
        val registry = EditorTestFixtures.registry()
        val model = GraphEditorModel(registry)
        val clipboard = GraphClipboard(registry)
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 10f, 10f)
        model.connect(a.id, "out", add.id, "a")
        val snippet = clipboard.copy(model, setOf(a.id, add.id))

        clipboard.pasteAt(model, snippet, 0f, 0f)
        assertEquals(4, model.nodes.size)

        model.undo()
        assertEquals(2, model.nodes.size)
        assertEquals(1, model.edges.size)

        model.redo()
        assertEquals(4, model.nodes.size)
    }

    @Test
    fun pasteAtCenterAlignsBoundingBox() {
        val registry = EditorTestFixtures.registry()
        val model = GraphEditorModel(registry)
        val clipboard = GraphClipboard(registry)
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 100f, 0f)
        val snippet = clipboard.copy(model, setOf(a.id, add.id))

        clipboard.pasteAt(model, snippet, 0f, 0f)
        // 新节点 bbox 中心应对齐 (0,0)
        val pasted = model.nodes.values.filter { it.id != a.id && it.id != add.id }
        val cx = (pasted.minOf { it.x } + pasted.maxOf { it.x }) / 2f
        val cy = (pasted.minOf { it.y } + pasted.maxOf { it.y }) / 2f
        assertEquals(0f, cx, 0.001f)
        assertEquals(0f, cy, 0.001f)
    }

    @Test
    fun duplicateOffsetsAndPreservesEdges() {
        val registry = EditorTestFixtures.registry()
        val model = GraphEditorModel(registry)
        val clipboard = GraphClipboard(registry)
        val a = model.addNode("input.constant", 0f, 0f)
        val add = model.addNode("math.add", 10f, 10f)
        model.connect(a.id, "out", add.id, "a")

        val newIds = clipboard.duplicate(model, setOf(a.id, add.id))
        assertEquals(2, newIds.size)
        assertEquals(4, model.nodes.size)
        assertEquals(2, model.edges.size)

        model.undo()
        assertEquals(2, model.nodes.size)
        assertEquals(1, model.edges.size)
    }

    @Test
    fun decodeGarbageReturnsNull() {
        val registry = EditorTestFixtures.registry()
        assertNull(clipboardFrom(registry).decode("not json at all {{{"))
        assertNull(clipboardFrom(registry).decode(""))
    }

    private fun clipboardFrom(registry: org.academy.api.client.render.graph.registry.NodeRegistry) =
        GraphClipboard(registry)
}
