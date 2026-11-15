package org.academy.api.client.render.vfxgraph.render;

import org.academy.api.client.render.vfxgraph.curve.TubeCurve;

public interface CurveLighting {
    record Light(float intensity, float alpha) {
        public static final Light DEFAULT = new Light(1f, 1f);
    }

    Light light(TubeCurve curve);
}
