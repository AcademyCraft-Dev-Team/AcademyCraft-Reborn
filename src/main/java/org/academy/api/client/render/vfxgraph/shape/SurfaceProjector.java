package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

/** Projects an emitter-local point onto a named surface. NaN marks an unsupported/empty surface. */
@FunctionalInterface
public interface SurfaceProjector {
    SurfaceProjector IDENTITY = (x, y, z, out) -> out.set(x, y, z);

    Vector3f project(float x, float y, float z, Vector3f destination);
}
