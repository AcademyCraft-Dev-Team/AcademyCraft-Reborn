package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirstPersonRepairSurfaceTest {
    @Test void narrowWorldFovKeepsBothHandPatchesAtTheNormalHandScreenPositionAndDepth() {
        var reference = projection(70);
        for (float fov : new float[]{48, 70}) {
            var world = projection(fov);
            float scale = FirstPersonRepairSurface.projectionScale(world);
            for (float side : new float[]{-1, 1}) {
                var transform = new Matrix4f().scale(scale, scale, 1).translate(side * .30f, -.23f, -.53f);
                var unscaled = new Matrix4f().translate(side * .30f, -.23f, -.53f);
                for (float u : new float[]{.18f, .5f, .82f}) {
                    for (float v : new float[]{.18f, .5f, .82f}) {
                        var point = sample(u, v, transform);
                        var expected = sample(u, v, unscaled);
                        assertEquals(expected.z, point.z, 1e-6f);
                        world.transformProject(point);
                        reference.transformProject(expected);
                        assertEquals(expected.x, point.x, 1e-6f);
                        assertEquals(expected.y, point.y, 1e-6f);
                        assertTrue(Math.abs(point.x) < .9f && Math.abs(point.y) < .9f,
                                "The complete patch needs margin from the screen edge");
                    }
                }
            }
        }
    }

    @Test void orthographicOrInvalidProjectionPreservesTheExistingProxy() {
        assertEquals(1, FirstPersonRepairSurface.projectionScale(new Matrix4f()));
        assertEquals(1, FirstPersonRepairSurface.projectionScale(projection(70).m11(Float.NaN)));
        assertEquals(1, FirstPersonRepairSurface.projectionScale(projection(70).m11(0)));
    }

    private static Matrix4f projection(float fov) {
        return new Matrix4f().setPerspective((float) Math.toRadians(fov), 16f / 9, .05f, 100);
    }

    private static Vector3f sample(float u, float v, Matrix4f transform) {
        var point = new Vector3f();
        var normal = new Vector3f();
        SurfaceSampler.box(5, u, v, new Vector3f(-.05f, -.05f, -.02f), new Vector3f(.05f, .05f, .02f),
                transform, point, normal);
        return point.fma(.007f, normal);
    }
}
