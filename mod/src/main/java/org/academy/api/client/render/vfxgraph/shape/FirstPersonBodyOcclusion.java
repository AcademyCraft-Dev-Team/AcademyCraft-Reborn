package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Clips emitter-space geometry against the camera's view of an otherwise hidden body volume.
 */
public final class FirstPersonBodyOcclusion {
    public final Vector3f eye = new Vector3f();
    public final Vector3f min = new Vector3f();
    public final Vector3f max = new Vector3f();

    public boolean occludes(Vector3fc point, float xOffset) {
        var near = 0.0f;
        var far = 1.0f;
        for (var axis = 0; axis < 3; axis++) {
            var origin = eye.get(axis);
            var delta = point.get(axis) + (axis == 0 ? xOffset : 0.0f) - origin;
            var lower = min.get(axis);
            var upper = max.get(axis);
            if (Math.abs(delta) < 1.0e-6f) {
                if (origin < lower || origin > upper) return false;
                continue;
            }
            var first = (lower - origin) / delta;
            var second = (upper - origin) / delta;
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
            if (near > far) return false;
        }
        return near > 1.0e-4f && near < 0.9999f && far > 0.0f;
    }
}
