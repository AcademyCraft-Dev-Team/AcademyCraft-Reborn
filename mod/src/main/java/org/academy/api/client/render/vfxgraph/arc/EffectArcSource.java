package org.academy.api.client.render.vfxgraph.arc;

@FunctionalInterface
public interface EffectArcSource {
    void emit(ArcEmission out, float timeSeconds);
}
