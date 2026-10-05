package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.arc.ArcCurve;

import java.util.concurrent.atomic.AtomicLong;

final class ArcSupport {
    private static final AtomicLong NEXT_TRANSIENT_ARC_GROUP = new AtomicLong(1L);

    private ArcSupport() {
    }

    static long newTransientGroup() {
        return NEXT_TRANSIENT_ARC_GROUP.getAndIncrement();
    }

    static void configure(ArcCurve arc, float[] color, float emission, float lifetime, long seed) {
        arc.setColor(color[0] * emission, color[1] * emission, color[2] * emission, color[3] * emission);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
    }
}
