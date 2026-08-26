package org.academy.desktop.grapheditor.canvas

import org.academy.desktop.grapheditor.EditorTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlignOpsTest {

    private val nodes = mapOf(
        "a" to Pair(0f, 0f),
        "b" to Pair(100f, 0f),
        "c" to Pair(50f, 100f),
    )

    @Test
    fun alignLeft() {
        val result = AlignOps.align(nodes, AlignOps.Align.LEFT)
        assertEquals(0f, result["a"]!!.first)
        assertEquals(0f, result["b"]!!.first)
        assertEquals(0f, result["c"]!!.first)
        assertEquals(100f, result["c"]!!.second)
    }

    @Test
    fun alignCenterH() {
        val result = AlignOps.align(nodes, AlignOps.Align.CENTER_H)
        assertEquals(50f, result["a"]!!.first)
        assertEquals(50f, result["b"]!!.first)
        assertEquals(50f, result["c"]!!.first)
    }

    @Test
    fun alignRight() {
        val result = AlignOps.align(nodes, AlignOps.Align.RIGHT)
        assertEquals(100f, result["a"]!!.first)
        assertEquals(100f, result["b"]!!.first)
        assertEquals(100f, result["c"]!!.first)
    }

    @Test
    fun alignTop() {
        val result = AlignOps.align(nodes, AlignOps.Align.TOP)
        assertEquals(0f, result["a"]!!.second)
        assertEquals(0f, result["b"]!!.second)
        assertEquals(0f, result["c"]!!.second)
    }

    @Test
    fun alignMiddleV() {
        val result = AlignOps.align(nodes, AlignOps.Align.MIDDLE_V)
        assertEquals(50f, result["a"]!!.second)
        assertEquals(50f, result["b"]!!.second)
        assertEquals(50f, result["c"]!!.second)
    }

    @Test
    fun alignBottom() {
        val result = AlignOps.align(nodes, AlignOps.Align.BOTTOM)
        assertEquals(100f, result["a"]!!.second)
        assertEquals(100f, result["b"]!!.second)
        assertEquals(100f, result["c"]!!.second)
    }

    @Test
    fun alignTooFewNodesReturnsEmpty() {
        assertTrue(AlignOps.align(mapOf("a" to Pair(0f, 0f)), AlignOps.Align.LEFT).isEmpty())
    }

    @Test
    fun distributeHorizontal() {
        val result = AlignOps.distribute(nodes, AlignOps.Distribute.HORIZONTAL)
        // 按 x 排序：a(0) c(50) b(100) → t=0/0.5/1
        assertEquals(0f, result["a"]!!.first)
        assertEquals(50f, result["c"]!!.first)
        assertEquals(100f, result["b"]!!.first)
    }

    @Test
    fun distributeVertical() {
        val result = AlignOps.distribute(nodes, AlignOps.Distribute.VERTICAL)
        // 按 y 排序：a(0) b(0) c(100) → t=0/0.5/1
        assertEquals(0f, result["a"]!!.second)
        assertEquals(50f, result["b"]!!.second)
        assertEquals(100f, result["c"]!!.second)
    }

    @Test
    fun distributeTooFewNodesReturnsEmpty() {
        assertTrue(
            AlignOps.distribute(mapOf("a" to Pair(0f, 0f), "b" to Pair(1f, 1f)), AlignOps.Distribute.HORIZONTAL)
                .isEmpty()
        )
    }

    @Test
    fun applyPositionsIsSingleUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val a = model.addNode("input.constant", 0f, 0f)
        val b = model.addNode("input.constant", 100f, 0f)
        model.addNode("input.constant", 50f, 100f)

        AlignOps.applyPositions(model, mapOf(a.id to Pair(0f, 0f), b.id to Pair(0f, 50f)))
        assertEquals(0f, model.nodes[b.id]!!.x)
        assertEquals(50f, model.nodes[b.id]!!.y)

        model.undo()
        assertEquals(100f, model.nodes[b.id]!!.x)
        assertEquals(0f, model.nodes[b.id]!!.y)
    }
}
