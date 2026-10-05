package org.academy.internal.client.render.vfxgraph;

public interface TickingVfx {
    void tick(FrameContext ctx);

    boolean isAlive();

    default void onRemoved() {
    }
}
