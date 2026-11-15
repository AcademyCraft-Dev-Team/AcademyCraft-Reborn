package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.curve.CurveBuffer;
import org.academy.api.client.render.vfxgraph.curve.CurveFactories;

public final class ArcTestSupport {
    static {
        CurveFactories.register(new ArcCurveFactory());
    }

    private ArcTestSupport() {
    }

    public static ArcCurve add(CurveBuffer buffer) {
        return (ArcCurve) buffer.add();
    }

    public static ArcCurve add(CurveBuffer buffer, long group) {
        return (ArcCurve) buffer.add(group);
    }

    public static ArcCurve at(CurveBuffer buffer, int index) {
        return (ArcCurve) buffer.arc(index);
    }
}
