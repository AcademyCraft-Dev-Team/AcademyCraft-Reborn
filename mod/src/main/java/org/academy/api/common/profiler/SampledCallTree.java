package org.academy.api.common.profiler;

import java.util.concurrent.atomic.LongAdder;

public class SampledCallTree {
    private final long threadId;
    private final String threadName;
    private final SampledCallNode root = new SampledCallNode("<root>");
    private final LongAdder sampleCount = new LongAdder();

    public SampledCallTree(long threadId, String threadName) {
        this.threadId = threadId;
        this.threadName = threadName;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public SampledCallNode getRoot() {
        return root;
    }

    public void insert(StackTraceElement[] frames) {
        sampleCount.increment();
        var node = root;
        node.samples.increment();
        for (var i = frames.length - 1; i >= 0; i--) {
            var frame = frames[i];
            var label = frame.getClassName() + '.' + frame.getMethodName();
            node = node.child(label);
            node.samples.increment();
            if (i == 0) {
                node.selfSamples.increment();
            }
        }
    }

    public long totalSamples() {
        return sampleCount.sum();
    }

    public void reset() {
        root.children.clear();
        root.samples.reset();
        root.selfSamples.reset();
        sampleCount.reset();
    }
}
