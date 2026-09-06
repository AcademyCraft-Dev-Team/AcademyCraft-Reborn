package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector4f;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/** Shared cloud lobe layout for visible billows and attached surface arcs. W is the lobe radius. */
public final class StormCloudShape {
    private StormCloudShape() {
    }

    public static Vector4f lobe(int index, int count, float time, long seed,
                                float height, float radius, Vector4f out) {
        float u = (index + 0.5f) / count;
        float angle = index * 2.399963f + time * 0.055f;
        float r = radius * (float) Math.sqrt(u) * 0.82f;
        return out.set((float) Math.cos(angle) * r,
                height + radius * (0.055f + unit(seed, index) * 0.10f),
                (float) Math.sin(angle) * r,
                radius * (0.22f + 0.11f * unit(seed, index + 9)));
    }

    /** Lower envelope of overlapping billows: the exposed cloud surface visible from the ground. */
    public static float underside(float x, float z, Vector4f[] lobes) {
        float y = Float.POSITIVE_INFINITY;
        for (var lobe : lobes) {
            float dx = x - lobe.x;
            float dz = z - lobe.z;
            float inside = lobe.w * lobe.w - dx * dx - dz * dz;
            if (inside >= 0) y = Math.min(y, lobe.y - (float) Math.sqrt(inside) * 0.72f);
        }
        return Float.isFinite(y) ? y : Float.NaN;
    }
}
