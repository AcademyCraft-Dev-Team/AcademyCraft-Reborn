package org.academy.internal.client.render.effect;

public interface TickingVfx {
    void tick(FrameContext ctx);

    boolean isAlive();

    default void onRemoved() {
    }
}
