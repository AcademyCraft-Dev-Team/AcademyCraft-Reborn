package org.academy.internal.client.render.vfx;

import org.joml.Matrix4fc;

/** Keep the repair hand proxy at the normal 70-degree hand projection size in the world pass. */
final class FirstPersonRepairSurface {
    private static final float HAND_PROJECTION_Y = (float) (1 / Math.tan(Math.toRadians(35)));

    private FirstPersonRepairSurface() { }

    static float projectionScale(Matrix4fc worldProjection) {
        float projectionY = Math.abs(worldProjection.m11());
        if (worldProjection.m33() != 0 || !Float.isFinite(projectionY) || projectionY <= .0001f) return 1;
        return HAND_PROJECTION_Y / projectionY;
    }
}
