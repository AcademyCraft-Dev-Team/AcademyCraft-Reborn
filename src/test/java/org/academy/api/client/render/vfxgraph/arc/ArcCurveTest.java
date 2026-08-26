package org.academy.api.client.render.vfxgraph.arc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArcCurveTest {

    @Test
    void addAndReadPoints() {
        var curve = new ArcCurve();
        curve.addPoint(1, 2, 3, 0.5f, 0);
        curve.addPoint(4, 5, 6, 0.3f, 1);

        assertEquals(2, curve.size());
        assertEquals(1f, curve.x(0));
        assertEquals(5f, curve.y(1));
        assertEquals(6f, curve.z(1));
        assertEquals(0.5f, curve.width(0));
        assertEquals(0f, curve.generation(0));
        assertEquals(1f, curve.generation(1));
    }

    @Test
    void clearPointsResetsSize() {
        var curve = new ArcCurve();
        curve.addPoint(1, 2, 3, 0.5f, 0);
        curve.addPoint(4, 5, 6, 0.3f, 0);
        curve.clearPoints();
        assertEquals(0, curve.size());
    }

    @Test
    void expansionDoublesCapacity() {
        var curve = new ArcCurve();
        for (int i = 0; i < 20; i++) {
            curve.addPoint(i, i, i, 0.1f, 0);
        }
        assertEquals(20, curve.size());
        assertEquals(19f, curve.x(19));
    }

    @Test
    void colorAndLifecycle() {
        var curve = new ArcCurve();
        curve.setColor(0.8f, 0.2f, 0.4f, 1f);
        assertEquals(0.8f, curve.r());
        assertEquals(0.2f, curve.g());
        assertEquals(0.4f, curve.b());
        assertEquals(1f, curve.a());

        curve.setLifetime(2.0f);
        curve.setAge(0.5f);
        assertTrue(curve.isAlive());
        curve.setAge(2.5f);
        assertFalse(curve.isAlive());
    }

    @Test
    void seedIsPreserved() {
        var curve = new ArcCurve();
        curve.setSeed(12345L);
        assertEquals(12345L, curve.seed());
    }
}
