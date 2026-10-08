package org.academy.api.client.render.vfxgraph.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceClipTransformTest {
    @Test void localHalfSpaceKeepsTheSamePointsAfterTranslationYawScaleAndCameraOffset() {
        var camera = new Vector3f(3, 2, 10);
        var transform = new WorldTransform(new Vector3f(3, 2, 1),
                new Quaternionf().rotateY((float) Math.PI / 2), 2);
        float[] plane = new float[4];
        float[] point = new float[3];
        transform.applyClipPlane(-1, 0, 0, 2, camera, plane);
        for (float localX : new float[]{1, 2, 3}) {
            transform.apply(localX, 1, -0.2f, point);
            float distance = plane[0] * (point[0] - camera.x) + plane[1] * (point[1] - camera.y)
                    + plane[2] * (point[2] - camera.z) + plane[3];
            assertEquals((2 - localX) * 2, distance, 0.00001);
        }
    }

    @Test void mirroredUniformScaleAndTheUnrestrictedDefaultRemainSafe() {
        var transform = new WorldTransform(new Vector3f(4, 1, 2), new Quaternionf(), -2);
        float[] plane = new float[4];
        transform.applyClipPlane(-2, 0, 0, 4, new Vector3f(), plane);
        assertEquals(1, plane[0]);
        assertEquals(0, plane[3], 0.00001);
        transform.applyClipPlane(0, 0, 0, 1, new Vector3f(), plane);
        assertArrayEquals(new float[]{0, 0, 0, 1}, plane);
    }
}
