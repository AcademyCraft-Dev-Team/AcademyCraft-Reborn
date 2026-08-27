package org.academy.api.client.render.vfxgraph.shape;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmitterShapeM13Test {

    @Test
    void cylinderSamplesWithinBounds() {
        var shape = new CylinderShape(0f, 0f, 0f, 2f, 5f);
        var random = new Random(1);
        var out = new float[3];
        for (var k = 0; k < 100; k++) {
            shape.sample(random, out);
            var r = (float) Math.sqrt(out[0] * out[0] + out[2] * out[2]);
            assertTrue(r >= 1.99f && r <= 2.01f, "cylinder surface radius");
            assertTrue(out[1] >= 0f && out[1] <= 5f, "cylinder height");
        }
    }

    @Test
    void torusSamplesWithinBounds() {
        var shape = new TorusShape(0f, 0f, 0f, 3f, 1f);
        var random = new Random(2);
        var out = new float[3];
        for (var k = 0; k < 100; k++) {
            shape.sample(random, out);
            var r = (float) Math.sqrt(out[0] * out[0] + out[2] * out[2]);
            assertTrue(r >= 2f - 1e-4f && r <= 4f + 1e-4f, "torus radius range");
            assertTrue(out[1] >= -1f - 1e-4f && out[1] <= 1f + 1e-4f, "torus height");
        }
    }

    @Test
    void circleEdgeSamplesOnRing() {
        var shape = new CircleEdgeShape(0f, 1f, 0f, 2f);
        var random = new Random(3);
        var out = new float[3];
        for (var k = 0; k < 100; k++) {
            shape.sample(random, out);
            var r = (float) Math.sqrt(out[0] * out[0] + out[2] * out[2]);
            assertTrue(Math.abs(r - 2f) < 1e-4f, "circle edge radius");
            assertEquals(1f, out[1], "circle edge y");
        }
    }
}
