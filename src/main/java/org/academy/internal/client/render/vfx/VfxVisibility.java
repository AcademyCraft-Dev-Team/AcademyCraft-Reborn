package org.academy.internal.client.render.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.VfxCamera;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/** Render-thread scratch; computes a camera-relative frustum once per sampled frame. */
public final class VfxVisibility {
    private static VfxCamera previous;
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private VfxVisibility() {}
    private static void prepare(VfxCamera camera) {
        if (previous != camera) {
            MATRIX.set(camera.projectionMatrix()).mul(camera.viewRotationMatrix());
            FRUSTUM.set(MATRIX);
            previous = camera;
        }
    }
    public static boolean sphere(VfxCamera camera, Vec3 position, float radius) {
        prepare(camera);
        var c = camera.pos();
        return FRUSTUM.testSphere((float) (position.x - c.x), (float) (position.y - c.y),
                (float) (position.z - c.z), radius);
    }
    public static boolean segment(VfxCamera camera, Vec3 a, Vec3 b, float radius) {
        prepare(camera);
        var c = camera.pos();
        return FRUSTUM.testAab((float) (Math.min(a.x, b.x) - c.x) - radius,
                (float) (Math.min(a.y, b.y) - c.y) - radius,
                (float) (Math.min(a.z, b.z) - c.z) - radius,
                (float) (Math.max(a.x, b.x) - c.x) + radius,
                (float) (Math.max(a.y, b.y) - c.y) + radius,
                (float) (Math.max(a.z, b.z) - c.z) + radius);
    }
}
