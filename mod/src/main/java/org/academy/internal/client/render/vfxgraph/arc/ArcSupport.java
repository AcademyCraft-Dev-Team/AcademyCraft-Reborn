package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.curve.CurveBuffer;


import java.util.concurrent.atomic.AtomicLong;

public final class ArcSupport {
    private static final AtomicLong NEXT_TRANSIENT_ARC_GROUP = new AtomicLong(1L);

    private ArcSupport() {
    }

    public static ArcCurve add(CurveBuffer buffer) {
        return (ArcCurve) buffer.add();
    }

    public static ArcCurve add(CurveBuffer buffer, long group) {
        return (ArcCurve) buffer.add(group);
    }

    public static long newTransientGroup() {
        return NEXT_TRANSIENT_ARC_GROUP.getAndIncrement();
    }

    public static void configure(ArcCurve arc, float[] color, float emission, float lifetime, long seed) {
        arc.setColor(color[0] * emission, color[1] * emission, color[2] * emission, color[3] * emission);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
    }
}
