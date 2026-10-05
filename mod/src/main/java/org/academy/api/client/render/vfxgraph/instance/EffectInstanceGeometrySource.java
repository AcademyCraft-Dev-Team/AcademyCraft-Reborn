package org.academy.api.client.render.vfxgraph.instance;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface EffectInstanceGeometrySource {
    @Nullable InstanceGeometryFrame sample(float timeSeconds);
}
