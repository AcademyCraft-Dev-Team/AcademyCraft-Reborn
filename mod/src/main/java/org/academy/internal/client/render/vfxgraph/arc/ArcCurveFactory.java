package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.curve.CurveFactory;
import org.academy.api.client.render.vfxgraph.curve.TubeCurve;

public final class ArcCurveFactory implements CurveFactory {
    @Override
    public TubeCurve create() {
        return new ArcCurve();
    }

    @Override
    public boolean supports(TubeCurve curve) {
        return curve instanceof ArcCurve;
    }
}
