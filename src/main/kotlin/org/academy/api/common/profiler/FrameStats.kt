package org.academy.api.common.profiler

/**
 * 帧时间 / FPS / 堆内存 的环形历史记录。
 */
object FrameStats {
    private const val CAPACITY = 720
    private val frameTimesNs = LongArray(CAPACITY)
    private val heapBytes = LongArray(CAPACITY)
    private var index = 0
    private var size = 0

    @Synchronized
    fun recordFrame(frameNs: Long, heapUsed: Long) {
        frameTimesNs[index] = frameNs
        heapBytes[index] = heapUsed
        index = (index + 1) % CAPACITY
        if (size < CAPACITY) size++
    }

    @Synchronized
    fun snapshot(): FrameStatsSnapshot {
        if (size == 0) {
            return FrameStatsSnapshot(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, DoubleArray(0), DoubleArray(0))
        }

        var min = Long.MAX_VALUE
        var max = 0L
        var sum = 0L
        for (i in 0 until size) {
            val v = frameTimesNs[i]
            if (v < min) min = v
            if (v > max) max = v
            sum += v
        }

        val recentCount = size.coerceAtMost(120)
        var recentSum = 0L
        for (i in 0 until recentCount) {
            recentSum += frameTimesNs[(index - 1 - i + CAPACITY * 2) % CAPACITY]
        }

        val lastIndex = (index - 1 + CAPACITY) % CAPACITY
        val p99 = percentile(0.99)
        return FrameStatsSnapshot(
            size = size,
            lastFrameMs = frameTimesNs[lastIndex] / 1e6,
            avgMs = sum.toDouble() / size / 1e6,
            minMs = if (min == Long.MAX_VALUE) 0.0 else min / 1e6,
            maxMs = max / 1e6,
            p99Ms = p99 / 1e6,
            fps = if (recentSum > 0) recentCount * 1e9 / recentSum else 0.0,
            frameTimesMs = DoubleArray(size) { i -> frameTimesNs[i] / 1e6 },
            heapUsedMb = DoubleArray(size) { i -> heapBytes[i] / 1048576.0 },
        )
    }

    private fun percentile(q: Double): Long {
        val arr = LongArray(size) { i -> frameTimesNs[i] }
        arr.sort()
        val pos = ((arr.size - 1) * q).toInt()
        return arr[pos]
    }
}

class FrameStatsSnapshot(
    val size: Int,
    val lastFrameMs: Double,
    val avgMs: Double,
    val minMs: Double,
    val maxMs: Double,
    val p99Ms: Double,
    val fps: Double,
    val frameTimesMs: DoubleArray,
    val heapUsedMb: DoubleArray,
)
