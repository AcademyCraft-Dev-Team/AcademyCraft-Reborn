package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3fc;
import org.joml.Vector4f;

/** Entity-neutral cached occlusion query for a complete group of graph-authored surface feathers. */
@FunctionalInterface
public interface SurfaceOcclusionSampler {
    /**
     * Writes an emitter-local allowed half-space: dot(plane.xyz, localPoint) + plane.w >= 0.
     * Return false for an unobstructed group; callers then use (0, 0, 0, 1). Implementations may
     * return a cached result, and must not retain the mutable input vectors. A feather array uses
     * group = pair * 2 + sideIndex, where sideIndex 0 is local -X and 1 is local +X.
     * Heights and normals use the anchor frame; halfWidth covers the full physical vane width.
     */
    boolean sample(int group, Vector3fc localRoot, Vector3fc localTip, Vector3fc surfaceNormal,
                   float halfWidth, Vector4f allowedPlane);

    /**
     * Samples a fan from one actual attachment to three points on its posed surface. Unlike a
     * capsule radius, these endpoints describe the fan without shifting its attachment outside
     * the physical surface. The default preserves existing samplers and functional implementations.
     * Inputs are borrowed for this call and must not be retained.
     */
    default boolean sampleFan(int group, Vector3fc localRoot, Vector3fc centerTip,
                              Vector3fc edgeTipA, Vector3fc edgeTipB, Vector3fc surfaceNormal,
                              float halfWidth, Vector4f allowedPlane) {
        return sample(group, localRoot, centerTip, surfaceNormal, halfWidth, allowedPlane);
    }
}
