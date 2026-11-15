package org.academy.api.client.render.vfxgraph.render;

import java.util.Objects;

public final class CurveLightings {
    private static volatile CurveLighting lighting = curve -> CurveLighting.Light.DEFAULT;

    private CurveLightings() {
    }

    public static void register(CurveLighting lighting) {
        CurveLightings.lighting = Objects.requireNonNull(lighting);
    }

    public static CurveLighting get() {
        return lighting;
    }
}
