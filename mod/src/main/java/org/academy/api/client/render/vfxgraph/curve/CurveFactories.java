package org.academy.api.client.render.vfxgraph.curve;

import java.util.Objects;

public final class CurveFactories {
    private static final CurveFactory DEFAULT = new CurveFactory() {
        @Override
        public TubeCurve create() {
            return new TubeCurve();
        }

        @Override
        public boolean supports(TubeCurve curve) {
            return true;
        }
    };

    private static volatile CurveFactory factory = DEFAULT;

    private CurveFactories() {
    }

    public static void register(CurveFactory factory) {
        CurveFactories.factory = Objects.requireNonNull(factory);
    }

    public static CurveFactory get() {
        return factory;
    }
}
