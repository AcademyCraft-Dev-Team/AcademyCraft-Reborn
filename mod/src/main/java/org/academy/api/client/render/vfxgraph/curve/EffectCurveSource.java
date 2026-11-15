package org.academy.api.client.render.vfxgraph.curve;

@FunctionalInterface
public interface EffectCurveSource {
    void emit(CurveEmission out, float timeSeconds);
}
