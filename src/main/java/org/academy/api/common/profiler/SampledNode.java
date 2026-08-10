package org.academy.api.common.profiler;

import java.util.List;

public class SampledNode {
    private final String label;
    private final long samples;
    private final long selfSamples;
    private final List<SampledNode> children;
    private final long totalSamples;

    public SampledNode(String label, long samples, long selfSamples, List<SampledNode> children, long totalSamples) {
        this.label = label;
        this.samples = samples;
        this.selfSamples = selfSamples;
        this.children = children;
        this.totalSamples = totalSamples;
    }

    public String getLabel() {
        return label;
    }

    public long getSamples() {
        return samples;
    }

    public long getSelfSamples() {
        return selfSamples;
    }

    public List<SampledNode> getChildren() {
        return children;
    }

    public long getTotalSamples() {
        return totalSamples;
    }

    public double getSamplesPercent() {
        return totalSamples > 0 ? samples * 100.0 / totalSamples : 0.0;
    }

    public double getSelfPercent() {
        return totalSamples > 0 ? selfSamples * 100.0 / totalSamples : 0.0;
    }
}
