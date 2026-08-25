package org.academy.desktop.grapheditor.command

/**
 * 组合命令：一组子命令作为一个整体入栈。execute 顺序执行，undo 逆序回滚。
 * 用于多选删除/移动、对齐、粘贴等批量操作，保证一次撤销恢复整组变更。
 */
class CompositeCommand(
    private val commands: List<Command>,
    private val description: String = "Multiple operations",
) : Command {
    override fun execute() {
        for (command in commands) command.execute()
    }

    override fun undo() {
        for (command in commands.asReversed()) command.undo()
    }

    override fun label(): String = description
}
