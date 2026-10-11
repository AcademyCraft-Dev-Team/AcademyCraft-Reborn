package org.academy.internal.client.render.vfx;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Vanilla 26.2 cow torso proxy, in model space before the living renderer's root transform. */
final class CowReconstructionSurface {
    private static final float SURFACE_CLEARANCE = .006f;

    private CowReconstructionSurface() { }

    static void prepare(boolean baby, boolean cold, float scale, float bodyYaw, float deathTime,
                        Vector3f min, Vector3f max, Matrix4f transform) {
        if (baby) {
            // BabyCowModel body: cube (-7,-7,-1)+(8,6,12), pivot (3,19,-5).
            min.set(-4, 12, -6).div(16);
            max.set(4, 18, 6).div(16);
        } else {
            // CowModel body: cube (-6,-10,-7)+(12,18,10), pivot (0,5,2), X rotation pi/2.
            min.set(-6, 2, -8).div(16);
            max.set(6, 12, 10).div(16);
        }
        float padding = SURFACE_CLEARANCE + (!baby && cold ? .5f / 16 : 0);
        min.sub(padding, padding, padding);
        max.add(padding, padding, padding);
        transform.identity().scale(scale).rotateY((float) Math.toRadians(180 - bodyYaw));
        if (deathTime > 0) {
            float fall = (float) Math.sqrt(Math.clamp((deathTime - 1) / 20 * 1.6f, 0, 1));
            transform.rotateZ(fall * (float) (Math.PI / 2));
        }
        transform.scale(-1, -1, 1).translate(0, -1.501f, 0);
    }
}
