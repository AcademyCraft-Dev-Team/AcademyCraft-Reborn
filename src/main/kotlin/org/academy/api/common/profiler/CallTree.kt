package org.academy.api.common.profiler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * 采样调用树的一个可变节点（由采样线程写入）。
 */
class SampledCallNode(val label: String) {
    val samples: LongAdder = LongAdder()
    val selfSamples: LongAdder = LongAdder()
    val children: ConcurrentHashMap<String, SampledCallNode> = ConcurrentHashMap()

    fun child(name: String): SampledCallNode =
        children.computeIfAbsent(name) { SampledCallNode(it) }
}

/**
 * 单个线程的采样调用树，仅由采样线程写入。
 */
class SampledCallTree(val threadId: Long, val threadName: String) {
    val root: SampledCallNode = SampledCallNode("<root>")
    private val sampleCount: LongAdder = LongAdder()

    fun insert(frames: Array<StackTraceElement>) {
        sampleCount.increment()
        var node = root
        node.samples.increment()
        for (i in frames.indices.reversed()) {
            val frame = frames[i]
            val label = frame.className + '.' + frame.methodName
            node = node.child(label)
            node.samples.increment()
            if (i == 0) node.selfSamples.increment()
        }
    }

    fun totalSamples(): Long = sampleCount.sum()

    fun reset() {
        root.children.clear()
        root.samples.reset()
        root.selfSamples.reset()
        sampleCount.reset()
    }
}
