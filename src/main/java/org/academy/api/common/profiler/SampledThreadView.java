package org.academy.api.common.profiler;

public class SampledThreadView {
    private final long id;
    private final String name;
    private final long samples;
    private final SampledNode root;

    public SampledThreadView(long id, String name, long samples, SampledNode root) {
        this.id = id;
        this.name = name;
        this.samples = samples;
        this.root = root;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getSamples() {
        return samples;
    }

    public SampledNode getRoot() {
        return root;
    }
}
