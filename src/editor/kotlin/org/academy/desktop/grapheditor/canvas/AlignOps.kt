package org.academy.desktop.grapheditor.canvas

import org.academy.desktop.grapheditor.command.CompositeCommand
import org.academy.desktop.grapheditor.command.MoveNodeCommand

/**
 * 多选对齐/均匀分布。纯函数：输入 id→(x,y)，输出 id→目标 (x,y)，便于单测。
 */
object AlignOps {
    enum class Align { LEFT, CENTER_H, RIGHT, TOP, MIDDLE_V, BOTTOM }

    enum class Distribute { HORIZONTAL, VERTICAL }

    /** 少于 2 个节点无法对齐，返回空。 */
    fun align(nodes: Map<String, Pair<Float, Float>>, align: Align): Map<String, Pair<Float, Float>> {
        if (nodes.size < 2) return emptyMap()
        val minX = nodes.values.minOf { it.first }
        val maxX = nodes.values.maxOf { it.first }
        val minY = nodes.values.minOf { it.second }
        val maxY = nodes.values.maxOf { it.second }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        return nodes.mapValues { (_, pos) ->
            val (x, y) = pos
            when (align) {
                Align.LEFT -> Pair(minX, y)
                Align.CENTER_H -> Pair(centerX, y)
                Align.RIGHT -> Pair(maxX, y)
                Align.TOP -> Pair(x, minY)
                Align.MIDDLE_V -> Pair(x, centerY)
                Align.BOTTOM -> Pair(x, maxY)
            }
        }
    }

    /** 少于 3 个节点无法均匀分布，返回空。 */
    fun distribute(nodes: Map<String, Pair<Float, Float>>, axis: Distribute): Map<String, Pair<Float, Float>> {
        if (nodes.size < 3) return emptyMap()
        val sorted = when (axis) {
            Distribute.HORIZONTAL -> nodes.entries.sortedBy { it.value.first }
            Distribute.VERTICAL -> nodes.entries.sortedBy { it.value.second }
        }
        val first = sorted.first()
        val last = sorted.last()
        val extent = when (axis) {
            Distribute.HORIZONTAL -> last.value.first - first.value.first
            Distribute.VERTICAL -> last.value.second - first.value.second
        }
        val span = (sorted.size - 1).toFloat()
        val result = LinkedHashMap<String, Pair<Float, Float>>()
        for ((index, entry) in sorted.withIndex()) {
            val t = if (span == 0f) 0f else index / span
            val (x, y) = entry.value
            result[entry.key] = when (axis) {
                Distribute.HORIZONTAL -> Pair(first.value.first + extent * t, y)
                Distribute.VERTICAL -> Pair(x, first.value.second + extent * t)
            }
        }
        return result
    }

    /** 把目标位置应用到模型（合并为一条 MoveNodeCommand 组合命令，可整体撤销）。 */
    fun applyPositions(model: GraphEditorModel, positions: Map<String, Pair<Float, Float>>) {
        if (positions.isEmpty()) return
        val commands = positions.mapNotNull { (id, pos) ->
            val node = model.nodes[id] ?: return@mapNotNull null
            if (node.x == pos.first && node.y == pos.second) return@mapNotNull null
            MoveNodeCommand(model, id, node.x, node.y, pos.first, pos.second)
        }
        if (commands.isEmpty()) return
        model.submit(CompositeCommand(commands, "Align/Distribute"))
    }
}
