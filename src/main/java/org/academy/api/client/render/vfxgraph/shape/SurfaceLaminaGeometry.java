package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

/** Continuous white-matter cross section shared by surface effects and preview/picking tools. */
public final class SurfaceLaminaGeometry {
    public enum Motion { DISASSEMBLE, REPAIR, FRAGMENT }

    private SurfaceLaminaGeometry() { }

    /**
     * Samples a closed, thin oval section: x is across the layer, y along it, z outward.
     * The shader uses the same parameterization, so this is also a conservative geometry oracle.
     */
    public static void sample(float u, float v, float length, float widthRatio, float lift,
                              float progress, Motion motion, float phase, Vector3f out) {
        float p = Math.clamp(progress, 0, 1);
        v = Math.clamp(v, 0, 1);
        float growth = motion == Motion.FRAGMENT ? 1 : smooth(p / (motion == Motion.REPAIR ? 0.62f : 0.22f));
        float retreat = motion == Motion.REPAIR ? smooth((1 - p) / 0.28f) : 1;
        float tip = 1 - smooth((v - 0.55f) / 0.45f);
        if (motion == Motion.DISASSEMBLE) tip *= 0.10f + 0.90f * smooth(v / 0.15f);
        if (motion == Motion.REPAIR) {
            float joined = smooth((p - 0.25f) / 0.40f);
            float stem = 0.06f + 0.86f * smooth(v / 0.7f);
            float front = 1 - smooth((v - 0.7f) / 0.3f);
            tip = stem * (front + (0.97f - front) * joined);
        }
        float taper = (0.94f + 0.06f * (float) Math.sin(v * 9 + phase)) * tip;
        float angle = Math.clamp(u, 0, 1) * (float) (2 * Math.PI);
        float across = (float) Math.cos(angle);
        float peel = motion == Motion.DISASSEMBLE ? smooth((p - 0.18f) / 0.44f) : 0;
        float along = v * length * growth * (1 - peel * v * v * 0.28f);
        float halfWidth = widthRatio * length * 0.5f * taper * retreat;
        float rise = motion == Motion.DISASSEMBLE ? lift * smooth((p - 0.18f) / 0.44f) * v * v
                : motion == Motion.REPAIR ? lift * 0.12f * (1 - smooth((p - 0.32f) / 0.38f)) * (float) Math.sin(v * Math.PI)
                : lift * (float) Math.sin(v * Math.PI);
        float thickness = Math.min(0.008f, length * 0.025f) * taper * retreat;
        float drift = (float) Math.sin(v * Math.PI) * (float) Math.sin(phase) * length * widthRatio * 0.16f;
        out.set(across * halfWidth + drift, along, rise - (float) Math.sin(angle) * thickness);
    }

    public static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    /** InstanceRot carries the motion and bend; no per-particle layout expansion is needed. */
    public static float encodeMotion(Motion motion, float lift) {
        lift = Math.clamp(lift, 0.001f, 0.5f);
        return switch (motion) {
            case DISASSEMBLE -> lift;
            case REPAIR -> -lift;
            case FRAGMENT -> 2 + lift;
        };
    }
}
