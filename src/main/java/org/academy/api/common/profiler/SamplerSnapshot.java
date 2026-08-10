package org.academy.api.common.profiler;

import java.util.Map;

public class SamplerSnapshot {
    private final Map<Long, SampledThreadView> threads;
    private final long totalSamples;
    private final double durationSeconds;

    public SamplerSnapshot(Map<Long, SampledThreadView> threads, long totalSamples, double durationSeconds) {
        this.threads = threads;
        this.totalSamples = totalSamples;
        this.durationSeconds = durationSeconds;
    }

    public Map<Long, SampledThreadView> getThreads() {
        return threads;
    }

    public long getTotalSamples() {
        return totalSamples;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }
}
