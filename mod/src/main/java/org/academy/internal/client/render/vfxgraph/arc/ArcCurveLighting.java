package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.internal.client.render.vfxgraph.arc.BlenderCurves;
import org.academy.api.client.render.vfxgraph.curve.TubeCurve;
import org.academy.api.client.render.vfxgraph.render.CurveLighting;

public final class ArcCurveLighting implements CurveLighting {
    @Override
    public Light light(TubeCurve curve) {
        if (!(curve instanceof ArcCurve arc)) return Light.DEFAULT;
        var lifetime = Math.max(1e-3f, arc.lifetime());
        var ageFrac = Math.clamp(arc.age() / lifetime, 0f, 1f);
        if (arc.sparkVelocity() != null) {
            return new Light(BlenderCurves.sample(BlenderCurves.PARTICLE_LIFE, ageFrac), 1f);
        }
        if (arc.flatRadius()) {
            return new Light(BlenderCurves.sample(BlenderCurves.CONTACT_RADIUS_AGE, ageFrac), 1f);
        }
        if (arc.hasArchBase()) {
            return new Light(BlenderCurves.sample(BlenderCurves.LIGHT, ageFrac) + 0.33f, 1f);
        }
        return new Light(BlenderCurves.sample(BlenderCurves.FLICKER, ageFrac), 1f);
    }
}
