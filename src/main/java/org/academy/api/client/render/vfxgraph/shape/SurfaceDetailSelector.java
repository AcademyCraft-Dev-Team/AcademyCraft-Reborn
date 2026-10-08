package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Matrix4fc;

/** Per-effect surface quality with projection-aware hysteresis; independent of actors and renderers. */
public final class SurfaceDetailSelector {
    public static final int FULL = 0;
    public static final int MEDIUM = 1;
    public static final int FAR = 2;
    private int detail = FULL;

    /**
     * Dimensions and camera distance are in the same world-space units. The complete radius
     * conservatively protects the nearest part of a large shape, including anchor offsets.
     * Orthographic inspection and invalid input use FULL instead of distance-based detail.
     */
    public int update(float featureLength, float completeRadius, float cameraDistance, Matrix4fc projection) {
        if (!Float.isFinite(featureLength) || !Float.isFinite(completeRadius)
                || !Float.isFinite(cameraDistance) || featureLength <= 0 || completeRadius < 0
                || projection.m33() != 0 || !Float.isFinite(projection.m11())) {
            return reset();
        }
        float distance = cameraDistance - completeRadius;
        if (distance <= 0.0001f) return reset();
        float projectedSize = featureLength * Math.abs(projection.m11()) / distance;
        if (!(projectedSize > 0) || !Float.isFinite(projectedSize)) return reset();
        if (detail == FULL) {
            if (projectedSize < 0.045f) detail = FAR;
            else if (projectedSize < 0.10f) detail = MEDIUM;
        } else if (detail == MEDIUM) {
            if (projectedSize > 0.12f) detail = FULL;
            else if (projectedSize < 0.045f) detail = FAR;
        } else {
            if (projectedSize > 0.12f) detail = FULL;
            else if (projectedSize > 0.055f) detail = MEDIUM;
        }
        return detail;
    }

    public int reset() { return detail = FULL; }
    public int detail() { return detail; }

    /** Unknown explicit levels preserve complete geometry. */
    public static int validDetail(int value) { return value >= FULL && value <= FAR ? value : FULL; }
    public static int gridX(int detail) { return switch (validDetail(detail)) { case MEDIUM -> 12; case FAR -> 8; default -> 16; }; }
    public static int gridY(int detail) { return switch (validDetail(detail)) { case MEDIUM -> 6; case FAR -> 4; default -> 8; }; }
    public static int indexCount(int detail) { return gridX(detail) * gridY(detail) * 6; }
}
