package org.academy.desktop.grapheditor.command

import java.util.*

/**
 * 撤销/重做管理器（命令模式）。execute 执行并压入撤销栈；undo/redo 回滚/重放；
 * 栈深受限（[maxDepth]），支持相邻同 [Command.mergeKey] 命令合并。
 *
 * [onMutate] 在每次 execute/undo/redo 后回调，供宿主刷新脏标记/预览。
 */
class UndoManager(private val onMutate: () -> Unit = {}) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    var maxDepth: Int = DEFAULT_MAX_DEPTH
        set(value) {
            field = value.coerceAtLeast(1)
            trimUndo()
        }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(command: Command) {
        command.execute()
        val key = command.mergeKey()
        if (key != null && undoStack.isNotEmpty()) {
            val top = undoStack.peekLast()
            if (top.mergeKey() == key) {
                val merged = top.mergeWith(command)
                if (merged != null) {
                    undoStack.removeLast()
                    undoStack.addLast(merged)
                    redoStack.clear()
                    onMutate()
                    return
                }
            }
        }
        undoStack.addLast(command)
        trimUndo()
        redoStack.clear()
        onMutate()
    }

    fun undo(): Boolean {
        val command = undoStack.pollLast() ?: return false
        command.undo()
        redoStack.addLast(command)
        onMutate()
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.pollLast() ?: return false
        command.execute()
        undoStack.addLast(command)
        onMutate()
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun trimUndo() {
        while (undoStack.size > maxDepth) undoStack.removeFirst()
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 100
    }
}
