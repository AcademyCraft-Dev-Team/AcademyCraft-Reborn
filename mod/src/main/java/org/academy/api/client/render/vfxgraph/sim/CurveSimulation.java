package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.vfxgraph.curve.CurveBuffer;

public interface CurveSimulation {
    record Config(float driftSpeed, float noiseStrength) {
        public static final Config DEFAULT = new Config(1.5f, 0.27f);
    }

    void simulate(SimContext context, CurveBuffer buffer, Config config);
}
