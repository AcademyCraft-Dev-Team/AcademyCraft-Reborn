package org.academy.internal.client.render.vfxgraph;

@FunctionalInterface
public interface FrameSampler {
    void sample(FrameContext ctx);
}