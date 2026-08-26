package org.academy.api.common.profiler;

import java.util.List;

public record SampledNode(String label, long samples, long selfSamples, List<SampledNode> children, long totalSamples) {

    public double getSamplesPercent() {
        return totalSamples > 0 ? samples * 100.0 / totalSamples : 0.0;
    }

    public double getSelfPercent() {
        return totalSamples > 0 ? selfSamples * 100.0 / totalSamples : 0.0;
    }
}
