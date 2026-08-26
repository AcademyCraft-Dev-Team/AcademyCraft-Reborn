package org.academy.api.client.render.vfxgraph.shape;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmitterShapeTest {
    private final Random random = new Random(7L);

    @Test
    void pointShapeAlwaysReturnsOrigin() {
        var shape = new PointShape(1f, 2f, 3f);
        var out = new float[3];
        for (int i = 0; i < 10; i++) {
            shape.sample(random, out);
            assertEquals(1f, out[0]);
            assertEquals(2f, out[1]);
            assertEquals(3f, out[2]);
        }
    }

    @Test
    void sphereShapeStaysWithinRadius() {
        var shape = new SphereShape(0f, 0f, 0f, 5f);
        var out = new float[3];
        for (int i = 0; i < 1000; i++) {
            shape.sample(random, out);
            assertTrue(Math.abs(out[0]) <= 5f);
            assertTrue(Math.abs(out[1]) <= 5f);
            assertTrue(Math.abs(out[2]) <= 5f);
        }
    }

    @Test
    void boxShapeStaysWithinHalfExtents() {
        var shape = new BoxShape(0f, 0f, 0f, 1f, 2f, 3f);
        var out = new float[3];
        for (int i = 0; i < 1000; i++) {
            shape.sample(random, out);
            assertTrue(Math.abs(out[0]) <= 1f);
            assertTrue(Math.abs(out[1]) <= 2f);
            assertTrue(Math.abs(out[2]) <= 3f);
        }
    }

    @Test
    void coneShapeStaysWithinBounds() {
        var shape = new ConeShape(0f, 0f, 0f, 2f, 4f);
        var out = new float[3];
        for (int i = 0; i < 1000; i++) {
            shape.sample(random, out);
            assertTrue(out[1] >= 0f && out[1] <= 4f);
            double radial = Math.sqrt(out[0] * out[0] + out[2] * out[2]);
            assertTrue(radial <= 2f + 1e-4);
        }
    }
}
