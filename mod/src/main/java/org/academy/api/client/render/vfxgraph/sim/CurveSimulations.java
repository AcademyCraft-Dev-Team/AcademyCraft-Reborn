package org.academy.api.client.render.vfxgraph.sim;

import java.util.Objects;

public final class CurveSimulations {
    private static volatile CurveSimulation simulation = (context, buffer, config) -> {
    };

    private CurveSimulations() {
    }

    public static void register(CurveSimulation simulation) {
        CurveSimulations.simulation = Objects.requireNonNull(simulation);
    }

    public static CurveSimulation get() {
        return simulation;
    }
}
