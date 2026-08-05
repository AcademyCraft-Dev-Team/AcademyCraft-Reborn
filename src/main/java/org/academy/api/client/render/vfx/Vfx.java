package org.academy.api.client.render.vfx;

public interface Vfx {
    default void update(float dt, VfxFrameContext ctx) {
    }

    void sample(VfxFrameContext ctx, VfxSink sink);

    boolean isAlive();
}
