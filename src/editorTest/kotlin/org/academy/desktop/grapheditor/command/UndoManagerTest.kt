package org.academy.desktop.grapheditor.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class AccumCommand(
    private val target: MutableList<Int>,
    private val old: Int,
    private val new: Int,
) : Command {
    override fun execute() {
        val index = target.indexOf(old)
        if (index >= 0) target[index] = new else target.add(new)
    }

    override fun undo() {
        val index = target.lastIndexOf(new)
        if (index >= 0) target[index] = old else target.remove(new)
    }

    override fun mergeKey(): String = "accum"

    override fun mergeWith(next: Command): Command? {
        if (next !is AccumCommand) return null
        return AccumCommand(target, old, next.new)
    }

    override fun label(): String = "accum $old -> $new"
}

/** 不可合并的普通命令，用于深度裁剪等非合并场景。 */
private class PlainCommand(
    private val target: MutableList<Int>,
    private val value: Int,
) : Command {
    override fun execute() {
        target.add(value)
    }

    override fun undo() {
        target.remove(value)
    }

    override fun label(): String = "plain $value"
}

class UndoManagerTest {

    @Test
    fun executeRunsAndTracksState() {
        val target = mutableListOf(0)
        val manager = UndoManager()
        manager.execute(AccumCommand(target, 0, 5))
        assertEquals(listOf(5), target)
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
    }

    @Test
    fun undoRedoRoundTrip() {
        val target = mutableListOf(0)
        val manager = UndoManager()
        manager.execute(AccumCommand(target, 0, 5))
        assertTrue(manager.undo())
        assertEquals(listOf(0), target)
        assertTrue(manager.canRedo)
        assertTrue(manager.redo())
        assertEquals(listOf(5), target)
    }

    @Test
    fun undoRedoOnEmptyStackReturnsFalse() {
        val manager = UndoManager()
        assertFalse(manager.undo())
        assertFalse(manager.redo())
    }

    @Test
    fun newExecuteClearsRedoStack() {
        val target = mutableListOf(0)
        val manager = UndoManager()
        manager.execute(AccumCommand(target, 0, 5))
        manager.undo()
        assertTrue(manager.canRedo)
        manager.execute(AccumCommand(target, 0, 9))
        assertFalse(manager.canRedo)
        assertEquals(listOf(9), target)
    }

    @Test
    fun maxDepthTrimsOldestCommands() {
        val target = mutableListOf<Int>()
        val manager = UndoManager()
        manager.maxDepth = 3
        for (i in 1..6) manager.execute(PlainCommand(target, i))
        // 深度 3：只能撤销最近 3 条
        assertTrue(manager.undo())
        assertTrue(manager.undo())
        assertTrue(manager.undo())
        assertFalse(manager.undo())
    }

    @Test
    fun mergeCoalescesAdjacentSameKeyCommands() {
        val target = mutableListOf(0)
        val manager = UndoManager()
        manager.execute(AccumCommand(target, 0, 5))
        manager.execute(AccumCommand(target, 5, 8))
        // 合并后单条命令：0 -> 8
        assertTrue(manager.undo())
        assertEquals(listOf(0), target)
        assertFalse(manager.canUndo)
        assertTrue(manager.redo())
        assertEquals(listOf(8), target)
    }

    @Test
    fun onMutateFiredOnEveryTransition() {
        var fired = 0
        val manager = UndoManager(onMutate = { fired++ })
        manager.execute(AccumCommand(mutableListOf(0), 0, 1))
        manager.undo()
        manager.redo()
        manager.clear()
        assertEquals(3, fired)
    }

    @Test
    fun clearDropsBothStacks() {
        val manager = UndoManager()
        manager.execute(AccumCommand(mutableListOf(0), 0, 1))
        manager.clear()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
    }

    @Test
    fun compositeUndoesInReverseOrder() {
        val target = mutableListOf(0)
        val manager = UndoManager()
        manager.execute(
            CompositeCommand(
                listOf(AccumCommand(target, 0, 1), AccumCommand(target, 1, 2)),
                "two steps"
            )
        )
        assertEquals(listOf(2), target)
        manager.undo()
        assertEquals(listOf(0), target)
    }
}
