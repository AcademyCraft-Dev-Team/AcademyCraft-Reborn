package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.vfxgraph.render.GraphCamera;

/** Per-frame attachment and parameters, evaluated before culling and shared by both render passes. */
@FunctionalInterface
public interface EffectFrameBinding {
    /** Return false to stop the effect when its attachment or owning action ends. */
    boolean update(ActiveEffect effect, GraphCamera camera, float partialTick);
}
