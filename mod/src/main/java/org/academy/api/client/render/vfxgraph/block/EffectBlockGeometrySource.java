package org.academy.api.client.render.vfxgraph.block;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface EffectBlockGeometrySource {
    @Nullable BlockGeometryFrame sample(float timeSeconds);
}
