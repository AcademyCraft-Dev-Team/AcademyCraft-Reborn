package org.academy.desktop.grapheditor.command

/**
 * 可逆编辑命令。编辑器所有 mutation 均以命令形式执行，供 [UndoManager] 撤销/重做。
 *
 * 合并协议：实现 [mergeKey] 返回非空字符串时，[UndoManager] 会把连续（栈顶）且同 key 的命令
 * 交给 [mergeWith] 尝试合并；返回非空则用合并结果替换栈顶命令（新命令被丢弃）。
 */
interface Command {
    fun execute()

    fun undo()

    fun label(): String

    fun mergeKey(): String? = null

    fun mergeWith(next: Command): Command? = null
}
