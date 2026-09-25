package org.academy.api.common.profiler;

import java.util.Map;

public record SamplerSnapshot(Map<Long, SampledThreadView> threads, long totalSamples, double durationSeconds) {
}
