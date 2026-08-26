package org.academy.api.client.render.vfxgraph.arc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ArcSimulatorTest {

    private static float[] unitCube() {
        float[] tris = new float[12 * 9];
        int t = 0;
        t = tri(tris, t, 1, -1, -1, 1, -1, 1, 1, 1, 1);
        t = tri(tris, t, 1, 1, 1, 1, 1, -1, 1, -1, -1);
        t = tri(tris, t, -1, -1, 1, -1, -1, -1, -1, 1, -1);
        t = tri(tris, t, -1, 1, -1, -1, 1, 1, -1, -1, 1);
        t = tri(tris, t, -1, 1, -1, 1, 1, -1, 1, 1, 1);
        t = tri(tris, t, -1, 1, 1, -1, 1, -1, 1, 1, 1);
        t = tri(tris, t, -1, -1, -1, -1, -1, 1, 1, -1, 1);
        t = tri(tris, t, 1, -1, 1, 1, -1, -1, -1, -1, -1);
        t = tri(tris, t, -1, -1, 1, 1, -1, 1, 1, 1, 1);
        t = tri(tris, t, -1, -1, 1, 1, 1, 1, -1, 1, 1);
        t = tri(tris, t, -1, 1, -1, 1, 1, -1, -1, -1, -1);
        t = tri(tris, t, -1, 1, -1, -1, -1, -1, 1, 1, -1);
        return tris;
    }

    private static int tri(float[] out, int t, float ax, float ay, float az,
                           float bx, float by, float bz, float cx, float cy, float cz) {
        out[t++] = ax;
        out[t++] = ay;
        out[t++] = az;
        out[t++] = bx;
        out[t++] = by;
        out[t++] = bz;
        out[t++] = cx;
        out[t++] = cy;
        out[t++] = cz;
        return t;
    }

    @Test
    void tickAgesArcs() {
        var buf = new ArcBuffer();
        var arc = buf.add();
        arc.setLifetime(2f);
        arc.setAge(0f);
        arc.setColor(1, 1, 1, 1);
        // Add some points
        for (int i = 0; i < 12; i++) {
            arc.addPoint(i * 0.1f, 0, 0, 0.01f, 0);
        }

        var dist = new SurfaceDistributor(unitCube());
        var sim = new ArcSimulator(buf, dist);

        sim.tick(0.5f, 0.5f, 0.27f, 2.0f, 42L);
        assertEquals(1, buf.count(), "Arc should still be alive");
        assertEquals(0.5f, buf.arc(0).age(), 0.01f);

        sim.tick(0.5f, 0.5f, 0.27f, 2.0f, 42L);
        sim.tick(0.5f, 0.5f, 0.27f, 2.0f, 42L); // age = 1.5
        assertEquals(1, buf.count(), "Arc should still be alive at 1.5s");

        sim.tick(1.0f, 0.5f, 0.27f, 2.0f, 42L); // age = 2.5 > lifetime
        assertEquals(0, buf.count(), "Arc should be expired");
    }

    @Test
    void tickAppliesNoise() {
        var buf = new ArcBuffer();
        var arc = buf.add();
        arc.setLifetime(10f);
        arc.setAge(0f);
        arc.setColor(1, 1, 1, 1);
        for (int i = 0; i < 12; i++) {
            arc.addPoint(0, i * 0.1f, 0, 0.01f, 0);
        }

        float origX3 = arc.x(3);

        var dist = new SurfaceDistributor(unitCube());
        var sim = new ArcSimulator(buf, dist);
        sim.tick(0.1f, 0.5f, 0.27f, 2.0f, 42L);

        // Point should have moved due to noise
        assertNotEquals(origX3, arc.x(3), 1e-4f, "Point should move from noise");
    }

    @Test
    void timeAdvances() {
        var buf = new ArcBuffer();
        var dist = new SurfaceDistributor(unitCube());
        var sim = new ArcSimulator(buf, dist);

        assertEquals(0f, sim.time(), 1e-6f);
        sim.tick(0.1f, 0.5f, 0.27f, 2.0f, 42L);
        assertEquals(0.1f, sim.time(), 1e-6f);
        sim.tick(0.2f, 0.5f, 0.27f, 2.0f, 42L);
        assertEquals(0.3f, sim.time(), 1e-6f);
    }
}
