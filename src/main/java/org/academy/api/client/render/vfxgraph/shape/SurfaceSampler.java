package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Stable surface samples in effect-local coordinates. A missing surface returns false. */
@FunctionalInterface
public interface SurfaceSampler {
    boolean sample(int index, float u, float v, Vector3f position, Vector3f normal);

    /** Samples one of the six faces of a box, optionally attached to a posed model part. */
    static void box(int face, float u, float v, Vector3f min, Vector3f max,
                    Matrix4fc transform, Vector3f position, Vector3f normal) {
        float x = min.x + (max.x - min.x) * u;
        float y = min.y + (max.y - min.y) * v;
        float z = min.z + (max.z - min.z) * u;
        switch (Math.floorMod(face, 6)) {
            case 0 -> { position.set(min.x, y, z); normal.set(-1, 0, 0); }
            case 1 -> { position.set(max.x, y, z); normal.set(1, 0, 0); }
            case 2 -> { position.set(x, min.y, min.z + (max.z - min.z) * v); normal.set(0, -1, 0); }
            case 3 -> { position.set(x, max.y, min.z + (max.z - min.z) * v); normal.set(0, 1, 0); }
            case 4 -> { position.set(x, y, min.z); normal.set(0, 0, -1); }
            default -> { position.set(x, y, max.z); normal.set(0, 0, 1); }
        }
        transform.transformPosition(position);
        transform.transformDirection(normal).normalize();
    }
}
