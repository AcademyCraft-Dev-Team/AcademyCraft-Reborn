package org.academy.api.common.profiler

/**
 * 统一的剖析快照，供 UI / 命令读取。
 */
class ProfilerSnapshot(
    val zones: Map<String, ZoneSnapshot>,
    val sampler: SamplerSnapshot?,
    val frame: FrameStatsSnapshot,
    val zonesEnabled: Boolean,
    val sampling: Boolean,
    val samplingPaused: Boolean,
)
