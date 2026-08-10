package org.academy.api.common.profiler

/**
 * AcademyCraft 性能分析总入口（门面）。
 *
 * 客户端 / 服务端 / 命令共用。数据采集完全自包含，不依赖 MC Profiler / Tracy / ImGui。
 */
object AcademyProfiler {
    // ---------- Zones（push/pop 插桩） ----------

    @JvmStatic
    fun push(name: String) = ZoneProfiler.push(name)

    @JvmStatic
    fun pop() = ZoneProfiler.pop()

    @JvmStatic
    fun popPush(name: String) = ZoneProfiler.popPush(name)

    @JvmStatic
    fun <T> zone(name: String, block: () -> T): T {
        ZoneProfiler.push(name)
        try {
            return block()
        } finally {
            ZoneProfiler.pop()
        }
    }

    /** Java 友好的 zone 便捷方法。 */
    @JvmStatic
    fun runZone(name: String, block: Runnable) {
        ZoneProfiler.push(name)
        try {
            block.run()
        } finally {
            ZoneProfiler.pop()
        }
    }

    @JvmStatic
    fun incrementCounter(name: String, amount: Int) = ZoneProfiler.incrementCounter(name, amount)

    @JvmStatic
    fun incrementCounter(name: String) = ZoneProfiler.incrementCounter(name, 1)

    @JvmStatic
    fun isCapturingZones(): Boolean = ZoneProfiler.enabled

    @JvmStatic
    fun startZoneCapture() {
        ZoneProfiler.setEnabled(true)
        invalidateSnapshot()
    }

    @JvmStatic
    fun stopZoneCapture() {
        ZoneProfiler.setEnabled(false)
        invalidateSnapshot()
    }

    @JvmStatic
    fun resetZones() {
        ZoneProfiler.reset()
        invalidateSnapshot()
    }

    // ---------- Sampler（采样式全量剖析） ----------

    @JvmStatic
    fun isSampling(): Boolean = ProfilerSampler.isRunning

    @JvmStatic
    fun isSamplingPaused(): Boolean = ProfilerSampler.isPaused

    @JvmStatic
    fun startSampling() {
        ProfilerSampler.start(1000L)
        invalidateSnapshot()
    }

    @JvmStatic
    fun startSampling(intervalMicros: Long) {
        ProfilerSampler.start(intervalMicros)
        invalidateSnapshot()
    }

    @JvmStatic
    fun stopSampling() {
        ProfilerSampler.stop()
        invalidateSnapshot()
    }

    @JvmStatic
    fun pauseSampling() {
        ProfilerSampler.pause()
        invalidateSnapshot()
    }

    @JvmStatic
    fun resumeSampling() {
        ProfilerSampler.resume()
        invalidateSnapshot()
    }

    @JvmStatic
    fun resetSampling() {
        ProfilerSampler.reset()
        invalidateSnapshot()
    }

    @JvmStatic
    fun registerThread(thread: Thread) = ProfilerSampler.registerThread(thread)

    @JvmStatic
    fun unregisterThread(thread: Thread) = ProfilerSampler.unregisterThread(thread.id)

    @JvmStatic
    fun setThreadEnabled(threadId: Long, enabled: Boolean) = ProfilerSampler.setThreadEnabled(threadId, enabled)

    @JvmStatic
    fun samplerThreads(): List<ProfilerSampler.ThreadRef> = ProfilerSampler.threadRefs()

    // ---------- 快照 ----------

    @Volatile
    private var cachedSnapshotAt = 0L

    @Volatile
    private var cachedSnapshot: ProfilerSnapshot? = null

    private const val SNAPSHOT_TTL_NANOS = 250_000_000L

    private fun invalidateSnapshot() {
        cachedSnapshot = null
        cachedSnapshotAt = 0L
    }

    @JvmStatic
    fun snapshot(): ProfilerSnapshot {
        val now = System.nanoTime()
        cachedSnapshot?.let {
            if (now - cachedSnapshotAt < SNAPSHOT_TTL_NANOS) return it
        }
        val fresh = buildSnapshot()
        cachedSnapshot = fresh
        cachedSnapshotAt = System.nanoTime()
        return fresh
    }

    private fun buildSnapshot(): ProfilerSnapshot {
        return ProfilerSnapshot(
            zones = ZoneProfiler.snapshot(),
            sampler = if (ProfilerSampler.hasData) ProfilerSampler.snapshot() else null,
            frame = FrameStats.snapshot(),
            zonesEnabled = ZoneProfiler.enabled,
            sampling = ProfilerSampler.isRunning,
            samplingPaused = ProfilerSampler.isPaused,
        )
    }
}
