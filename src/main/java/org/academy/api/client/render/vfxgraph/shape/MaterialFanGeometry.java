package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

/** Public local continuous surface sampler matching the material-fan output's UV domain. */
public final class MaterialFanGeometry {
    private MaterialFanGeometry() { }

    /** u runs along the arc, v from the outer cutting edge into its feathered material tail. */
    public static void sample(float u, float v, float radius, float arc, float width,
                              float progress, boolean reverse, Vector3f out) {
        u = Math.clamp(u, 0, 1);
        v = Math.clamp(v, 0, 1);
        radius = Math.clamp(radius, 0.1f, 64);
        width = Math.clamp(width, 0.02f, radius);
        arc = Math.clamp(arc, 0.05f, 5.8f);
        float sweep = (Math.clamp(progress, 0, 1) - 0.5f) * arc * 0.3f * (reverse ? -1 : 1);
        float angle = (u - 0.5f) * arc + sweep;
        float radial = radius - v * width;
        out.set((float) Math.sin(angle) * radial,
                (float) Math.sin(u * Math.PI) * v * radius * 0.025f,
                (float) Math.cos(angle) * radial);
    }

    public static float boundsRadius(float radius) { return Math.clamp(radius, 0.1f, 64) * 1.03f; }
}
