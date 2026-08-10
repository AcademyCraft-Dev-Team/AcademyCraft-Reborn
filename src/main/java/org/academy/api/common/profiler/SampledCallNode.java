package org.academy.api.common.profiler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class SampledCallNode {
    private final String label;
    public final LongAdder samples = new LongAdder();
    public final LongAdder selfSamples = new LongAdder();
    public final ConcurrentHashMap<String, SampledCallNode> children = new ConcurrentHashMap<>();

    public SampledCallNode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public LongAdder getSamples() {
        return samples;
    }

    public LongAdder getSelfSamples() {
        return selfSamples;
    }

    public ConcurrentHashMap<String, SampledCallNode> getChildren() {
        return children;
    }

    public SampledCallNode child(String name) {
        return children.computeIfAbsent(name, SampledCallNode::new);
    }
}
