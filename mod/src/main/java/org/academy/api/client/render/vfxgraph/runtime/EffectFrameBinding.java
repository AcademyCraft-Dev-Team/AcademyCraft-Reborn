package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.vfxgraph.render.GraphCamera;

@FunctionalInterface
public interface EffectFrameBinding {
    boolean update(ActiveEffect effect, GraphCamera camera, float partialTick);
}
