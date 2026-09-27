package org.academy.api.client.render.vfx;

/**
 * @deprecated implement effects with {@link org.academy.api.client.render.vfxgraph.GraphEffect}
 */
@Deprecated(since = "0.0.4")
public interface Vfx {
    default void update(float dt, VfxFrameContext ctx) {
    }

    void sample(VfxFrameContext ctx, VfxSink sink);

    boolean isAlive();
}
