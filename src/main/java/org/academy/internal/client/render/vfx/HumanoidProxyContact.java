package org.academy.internal.client.render.vfx;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Resolves an AABB contact against stable anatomy, before attaching it to an animated bone. */
final class HumanoidProxyContact {
    // Standard HumanoidModel pivots: torso, head, right/left arm, right/left leg (model pixels).
    private static final float[] PIVOT_X = {0, 0, -5f / 16, 5f / 16, -1.9f / 16, 1.9f / 16};
    private static final float[] PIVOT_Y = {0, 0, 2f / 16, 2f / 16, 12f / 16, 12f / 16};

    private HumanoidProxyContact() { }

    static int select(Vector3fc effectContact, Matrix4fc modelRoot, Vector3f[] partMin, Vector3f[] partMax) {
        var point = new Matrix4f(modelRoot).invert().transformPosition(new Vector3f(effectContact));
        float nearest = Float.POSITIVE_INFINITY;
        int selected = 0;
        for (int part = 0; part < PIVOT_X.length; part++) {
            float x = point.x - PIVOT_X[part];
            float y = point.y - PIVOT_Y[part];
            var min = partMin[part];
            var max = partMax[part];
            float dx = x - Math.clamp(x, min.x, max.x);
            float dy = y - Math.clamp(y, min.y, max.y);
            float dz = point.z - Math.clamp(point.z, min.z, max.z);
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearest) { nearest = distance; selected = part; }
        }
        return selected;
    }
}
