package org.academy.api.common.profiler

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 将剖析数据导出为文本报告（命令 / 文件导出用）。
 */
object ProfileDump {
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    @JvmStatic
    fun timestamp(): String = LocalDateTime.now().format(TIME_FORMAT)

    @JvmStatic
    fun dumpZones(snapshot: ZoneSnapshot, maxDepth: Int = 8): String {
        val sb = StringBuilder()
        sb.append("== AcademyCraft Zone Profile [thread=")
            .append(snapshot.threadName)
            .append("] ==\n")
        val rootTotal = snapshot.rootTotalNs
        sb.append("Total: ")
            .append("%.2f".format(rootTotal / 1e6))
            .append(" ms\n")
        appendZoneNode(sb, snapshot, ZoneProfiler.ROOT, 0, maxDepth)
        return sb.toString()
    }

    private fun appendZoneNode(
        sb: StringBuilder,
        snapshot: ZoneSnapshot,
        path: String,
        depth: Int,
        maxDepth: Int,
    ) {
        if (depth > maxDepth) return
        for (child in snapshot.childrenOf(path)) {
            val pct = snapshot.parentPercent(child)
            val rootTotal = snapshot.rootTotalNs
            val gpct = if (rootTotal > 0) child.totalNs * 100.0 / rootTotal else 0.0
            sb.append("  ".repeat(depth))
                .append(child.name)
                .append(" - ")
                .append("%.2f%% / %.2f%%".format(pct, gpct))
                .append(" - ")
                .append("%.2f ms".format(child.totalMs))
                .append(" (self ")
                .append("%.2f ms".format(child.selfMs))
                .append(") - ")
                .append(child.count)
                .append(" calls - max ")
                .append("%.2f ms".format(child.maxMs))
                .append('\n')
            if (depth < maxDepth) {
                appendZoneNode(sb, snapshot, child.path, depth + 1, maxDepth)
            }
        }
    }

    @JvmStatic
    fun dumpSampler(snapshot: SamplerSnapshot, limit: Int = 30): String {
        val sb = StringBuilder()
        sb.append("== AcademyCraft Sampling Profile ==\n")
            .append("Total samples: ")
            .append(snapshot.totalSamples)
            .append("  Duration: ")
            .append("%.1f".format(snapshot.durationSeconds))
            .append(" s\n")
        for (view in snapshot.threads.values) {
            sb.append("-- Thread: ").append(view.name).append(" (").append(view.samples).append(" samples)\n")
            val self = HashMap<String, Long>()
            collectSelf(view.root, self)
            val top = self.entries.sortedByDescending { it.value }.take(limit)
            for ((label, count) in top) {
                val total = view.root.samples
                val pct = if (total > 0) count * 100.0 / total else 0.0
                sb.append("  ")
                    .append(label)
                    .append(" - ")
                    .append("%.2f%%".format(pct))
                    .append(" (")
                    .append(count)
                    .append(" self samples)\n")
            }
        }
        return sb.toString()
    }

    private fun collectSelf(node: SampledNode, map: MutableMap<String, Long>) {
        if (node.label != "<root>") {
            map.merge(node.label, node.selfSamples, Long::plus)
        }
        for (child in node.children) {
            collectSelf(child, map)
        }
    }
}
