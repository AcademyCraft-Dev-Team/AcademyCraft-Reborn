package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceOcclusionSampleTest {
    @Test
    void fanQueriesRetainTheOriginalFunctionalSamplerContract() {
        var root = new Vector3f(0, 1, 0);
        var center = new Vector3f(4, 1.5f, 0);
        var upper = new Vector3f(2, 2, 0);
        var lower = new Vector3f(2, .12f, 0);
        var normal = new Vector3f(0, 0, 1);
        var plane = new Vector4f();
        int[] calls = {0};
        SurfaceOcclusionSampler legacy = (group, start, tip, surfaceNormal, width, output) -> {
            calls[0]++;
            assertEquals(3, group);
            assertSame(root, start);
            assertSame(center, tip);
            assertSame(normal, surfaceNormal);
            assertEquals(1.4f, width);
            assertSame(plane, output);
            output.set(-1, 0, 0, 2.2f);
            return true;
        };

        assertTrue(legacy.sampleFan(3, root, center, upper, lower, normal, 1.4f, plane));
        assertEquals(1, calls[0]);
        assertEquals(new Vector4f(-1, 0, 0, 2.2f), plane);
    }
}
