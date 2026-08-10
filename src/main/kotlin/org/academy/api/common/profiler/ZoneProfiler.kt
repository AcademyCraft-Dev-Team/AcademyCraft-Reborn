package org.academy.api.common.profiler

import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * 自研轻量 push/pop 插桩剖析器。
 *
 * 每个线程独立维护一个计时栈，用 [System.nanoTime] 记录耗时，聚合到
 * path → [ZoneNode] 的树中。不依赖 MC 的 Profiler / Tracy / ActiveProfiler，
 * 完全自包含。关闭时 push/pop 为近零开销。
 */
object ZoneProfiler {
    private val LOGGER = LoggerFactory.getLogger("AcademyProfiler")

    const val ROOT: String = "root"
    const val PATH_SEPARATOR: Char = '\u001e'

    @Volatile
    var enabled: Boolean = false
        private set

    private val sessions = ConcurrentHashMap<Long, ZoneSession>()

    private val threadLocalSession: ThreadLocal<ZoneSession> = ThreadLocal.withInitial {
        val thread = Thread.currentThread()
        sessions.computeIfAbsent(thread.id) { ZoneSession(thread.id, thread.name) }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) reset()
    }

    fun push(name: String) {
        if (!enabled) return
        threadLocalSession.get().push(name)
    }

    fun pop() {
        if (!enabled) return
        threadLocalSession.get().pop()
    }

    fun popPush(name: String) {
        if (!enabled) return
        val session = threadLocalSession.get()
        session.pop()
        session.push(name)
    }

    fun incrementCounter(name: String, amount: Int) {
        if (!enabled) return
        threadLocalSession.get().incrementCounter(name, amount)
    }

    fun reset() {
        sessions.values.forEach { it.reset() }
    }

    fun threadNames(): List<String> =
        sessions.values.map { it.name }.distinct().sorted()

    fun snapshot(): Map<String, ZoneSnapshot> {
        val result = LinkedHashMap<String, ZoneSnapshot>()
        for (session in sessions.values) {
            if (!result.containsKey(session.name)) {
                result[session.name] = session.snapshot()
            }
        }
        return result
    }

    internal fun logUnbalancedPop(threadName: String) {
        LOGGER.warn("ZoneProfiler: unbalanced pop() on thread '{}' (missing push)", threadName)
    }
}

/**
 * 单个线程的一次采集会话。字段只由所属线程写入；读取快照时克隆。
 */
class ZoneSession internal constructor(private val threadId: Long, val name: String) {
    private val pathStack = ArrayDeque<ZoneNode>()
    private val startTimes = ArrayDeque<Long>()
    private val nodes = ConcurrentHashMap<String, ZoneNode>()
    private var mismatchPops = 0

    init {
        nodes.computeIfAbsent(ZoneProfiler.ROOT) { ZoneNode(ZoneProfiler.ROOT, ZoneProfiler.ROOT) }
    }

    fun push(zoneName: String) {
        val current = currentNode()
        val path = childPath(current.path, zoneName)
        val node = nodes.computeIfAbsent(path) { ZoneNode(path, zoneName) }
        pathStack.addLast(node)
        startTimes.addLast(System.nanoTime())
    }

    fun pop() {
        if (startTimes.isEmpty()) {
            if (mismatchPops++ < 5) {
                ZoneProfiler.logUnbalancedPop(name)
            }
            return
        }
        val start = startTimes.removeLast()
        val node = pathStack.removeLast()
        val elapsed = System.nanoTime() - start
        node.totalNs.add(elapsed)
        node.count.increment()
        node.maxNs.accumulateAndGet(elapsed) { a, b -> maxOf(a, b) }
    }

    fun incrementCounter(counterName: String, amount: Int) {
        if (amount == 0) return
        currentNode().counters.computeIfAbsent(counterName) { LongAdder() }.add(amount.toLong())
    }

    private fun currentNode(): ZoneNode = pathStack.lastOrNull() ?: nodes[ZoneProfiler.ROOT]!!

    private fun childPath(parentPath: String, childName: String): String =
        if (parentPath == ZoneProfiler.ROOT) ZoneProfiler.ROOT + ZoneProfiler.PATH_SEPARATOR + childName
        else parentPath + ZoneProfiler.PATH_SEPARATOR + childName

    fun reset() {
        nodes.clear()
        nodes.computeIfAbsent(ZoneProfiler.ROOT) { ZoneNode(ZoneProfiler.ROOT, ZoneProfiler.ROOT) }
        pathStack.clear()
        startTimes.clear()
        mismatchPops = 0
    }

    fun snapshot(): ZoneSnapshot {
        val rootTotal = nodes[ZoneProfiler.ROOT]?.totalNs?.sum() ?: 0L
        val slices = LinkedHashMap<String, ZoneSlice>()
        for ((path, node) in nodes) {
            val self = selfNs(node)
            slices[path] = ZoneSlice(
                name = node.name,
                path = path,
                totalNs = node.totalNs.sum(),
                selfNs = self,
                count = node.count.sum(),
                maxNs = node.maxNs.get(),
                rootTotalNs = rootTotal,
                color = colorOf(node.name),
                counters = node.counters.entries.associate { it.key to it.value.sum() },
            )
        }
        return ZoneSnapshot(name, slices, rootTotal)
    }

    private fun selfNs(node: ZoneNode): Long {
        var childSum = 0L
        val prefix = node.path + ZoneProfiler.PATH_SEPARATOR
        for ((path, child) in nodes) {
            if (path.length > node.path.length
                && path.startsWith(prefix)
                && path.indexOf(ZoneProfiler.PATH_SEPARATOR, node.path.length + 1) < 0
            ) {
                childSum += child.totalNs.sum()
            }
        }
        return (node.totalNs.sum() - childSum).coerceAtLeast(0)
    }
}

class ZoneNode internal constructor(
    val path: String,
    val name: String,
) {
    val totalNs: LongAdder = LongAdder()
    val count: LongAdder = LongAdder()
    val maxNs: AtomicLong = AtomicLong(0)
    val counters: ConcurrentHashMap<String, LongAdder> = ConcurrentHashMap()
}

/**
 * 不可变快照切片，供 UI / 导出读取。
 */
class ZoneSlice(
    val name: String,
    val path: String,
    val totalNs: Long,
    val selfNs: Long,
    val count: Long,
    val maxNs: Long,
    val rootTotalNs: Long,
    val color: Int,
    val counters: Map<String, Long>,
) {
    val totalMs: Double get() = totalNs / 1e6
    val selfMs: Double get() = selfNs / 1e6
    val maxMs: Double get() = maxNs / 1e6
    val globalPercent: Double get() = if (rootTotalNs > 0) totalNs * 100.0 / rootTotalNs else 0.0
}

/**
 * 单个线程的 zone 快照。
 */
class ZoneSnapshot(
    val threadName: String,
    private val slices: Map<String, ZoneSlice>,
    val rootTotalNs: Long,
) {
    val root: ZoneSlice? get() = slices[ZoneProfiler.ROOT]

    fun sliceAt(path: String): ZoneSlice? = slices[path]

    fun childrenOf(path: String): List<ZoneSlice> =
        slices.values
            .filter { isDirectChild(path, it.path) }
            .sortedByDescending { it.totalNs }

    fun topSlices(limit: Int, excludeRoot: Boolean = true): List<ZoneSlice> =
        slices.values
            .filter { !excludeRoot || it.path != ZoneProfiler.ROOT }
            .sortedByDescending { it.totalNs }
            .take(limit)

    fun parentPercent(slice: ZoneSlice): Double {
        val parentPath = parentPathOf(slice.path)
        val parent = slices[parentPath]
        val parentTotal = parent?.totalNs ?: rootTotalNs
        return if (parentTotal > 0) slice.totalNs * 100.0 / parentTotal else 0.0
    }

    private fun parentPathOf(path: String): String {
        if (path == ZoneProfiler.ROOT) return ZoneProfiler.ROOT
        val idx = path.lastIndexOf(ZoneProfiler.PATH_SEPARATOR)
        return if (idx < 0) ZoneProfiler.ROOT else path.substring(0, idx)
    }

    private fun isDirectChild(parentPath: String, path: String): Boolean {
        if (path == parentPath) return false
        val prefix = parentPath + ZoneProfiler.PATH_SEPARATOR
        if (!path.startsWith(prefix)) return false
        val rest = path.substring(prefix.length)
        return rest.indexOf(ZoneProfiler.PATH_SEPARATOR) < 0
    }
}

/** MC ResultField 同款颜色：按名字哈希。 */
internal fun colorOf(name: String): Int = (name.hashCode() and 11184810) + -12303292
