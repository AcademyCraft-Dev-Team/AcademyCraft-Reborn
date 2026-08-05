package org.academy.api.common.profiler

import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 ThreadMXBean 的采样式性能剖析器（Spark 风格）。
 *
 * 在独立守护线程上周期性地抓取已注册线程的栈，聚合进 [SampledCallTree]。
 * 完全自包含，不依赖 MC 的 Profiler / Tracy / 外部环境。
 */
object ProfilerSampler {
    private val LOGGER = LoggerFactory.getLogger("AcademyProfiler")
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val threads = ConcurrentHashMap<Long, ThreadRef>()
    private val trees = ConcurrentHashMap<Long, SampledCallTree>()

    private const val MAX_DEPTH = 128

    @Volatile
    var intervalMicros: Long = 1000L
        private set

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var captureStartNanos = System.nanoTime()

    @Volatile
    private var everStarted = false

    private var worker: Thread? = null
    private val lock = Any()

    class ThreadRef(val id: Long, val name: String) {
        @Volatile
        var enabled: Boolean = true
    }

    val isRunning: Boolean get() = running
    val isPaused: Boolean get() = paused

    /** 是否启动过（保留停止后的数据，供 UI / 导出查看）。 */
    val hasData: Boolean get() = everStarted

    fun threadRefs(): List<ThreadRef> = threads.values.sortedBy { it.name }

    fun registerThread(thread: Thread): ThreadRef {
        val ref = threads.computeIfAbsent(thread.id) { ThreadRef(thread.id, thread.name) }
        trees.computeIfAbsent(thread.id) { SampledCallTree(thread.id, thread.name) }
        return ref
    }

    fun unregisterThread(threadId: Long) {
        threads.remove(threadId)
        trees.remove(threadId)
    }

    fun setThreadEnabled(threadId: Long, enabled: Boolean) {
        threads[threadId]?.enabled = enabled
    }

    fun start(intervalMicros: Long) {
        synchronized(lock) {
            if (running) return
            running = true
            paused = false
            everStarted = true
            this.intervalMicros = intervalMicros.coerceIn(100L, 1_000_000L)
            captureStartNanos = System.nanoTime()
            resetInternal()
            worker = Thread({ loop() }, "Academy Profiler Sampler").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!running) return
            running = false
            worker?.interrupt()
            worker = null
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun reset() {
        synchronized(lock) { resetInternal() }
    }

    private fun resetInternal() {
        captureStartNanos = System.nanoTime()
        trees.values.forEach { it.reset() }
    }

    fun elapsedSeconds(): Double = (System.nanoTime() - captureStartNanos) / 1e9

    private fun loop() {
        while (running) {
            if (!paused) {
                try {
                    sampleOnce()
                } catch (t: Throwable) {
                    LOGGER.warn("Sampler iteration failed", t)
                }
            }
            val us = intervalMicros
            val sleepMs = us / 1000
            val sleepNs = (us % 1000) * 1000
            try {
                Thread.sleep(sleepMs, sleepNs.toInt())
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun sampleOnce() {
        val enabledIds = threads.values.filter { it.enabled }.map { it.id }.toLongArray()
        if (enabledIds.isEmpty()) return
        val infos = try {
            threadBean.getThreadInfo(enabledIds, MAX_DEPTH)
        } catch (t: Throwable) {
            null
        } ?: return
        for (i in enabledIds.indices) {
            val info = infos[i] ?: continue
            val stack = info.stackTrace
            if (stack.isEmpty()) continue
            trees[enabledIds[i]]?.insert(stack)
        }
    }

    fun snapshot(): SamplerSnapshot {
        val perThread = trees.mapValues { (id, tree) ->
            val total = tree.totalSamples()
            SampledThreadView(
                id = id,
                name = tree.threadName,
                samples = total,
                root = cloneNode(tree.root, total),
            )
        }
        return SamplerSnapshot(
            threads = perThread,
            totalSamples = perThread.values.sumOf { it.samples },
            durationSeconds = elapsedSeconds(),
        )
    }

    private fun cloneNode(node: SampledCallNode, totalSamples: Long): SampledNode {
        val children = node.children.values
            .sortedByDescending { it.samples.sum() }
            .map { cloneNode(it, totalSamples) }
        return SampledNode(
            label = node.label,
            samples = node.samples.sum(),
            selfSamples = node.selfSamples.sum(),
            children = children,
            totalSamples = totalSamples,
        )
    }
}

class SampledNode(
    val label: String,
    val samples: Long,
    val selfSamples: Long,
    val children: List<SampledNode>,
    val totalSamples: Long,
) {
    val samplesPercent: Double get() = if (totalSamples > 0) samples * 100.0 / totalSamples else 0.0
    val selfPercent: Double get() = if (totalSamples > 0) selfSamples * 100.0 / totalSamples else 0.0
}

class SampledThreadView(
    val id: Long,
    val name: String,
    val samples: Long,
    val root: SampledNode,
)

class SamplerSnapshot(
    val threads: Map<Long, SampledThreadView>,
    val totalSamples: Long,
    val durationSeconds: Double,
)
