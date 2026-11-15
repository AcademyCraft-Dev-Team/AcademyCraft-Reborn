package org.academy.api.client.render.vfxgraph.curve;

public interface CurveFactory {
    TubeCurve create();

    boolean supports(TubeCurve curve);
}
