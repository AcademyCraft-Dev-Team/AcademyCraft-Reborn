package org.academy.api.common.profiler;

public record FrameStatsSnapshot(int size, double lastFrameMs, double avgMs, double minMs, double maxMs, double p99Ms,
                                 double fps, double[] frameTimesMs, double[] heapUsedMb) {
}
