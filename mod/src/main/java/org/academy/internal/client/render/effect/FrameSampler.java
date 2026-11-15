package org.academy.internal.client.render.effect;

@FunctionalInterface
public interface FrameSampler {
    void sample(FrameContext ctx);
}