package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SurfaceDistributorTest {

    /** 单位立方体三角形数据（6面×2三角形=12三角形，108 floats）。 */
    private static float[] unitCubeTriangles() {
        float[] tris = new float[12 * 9];
        int t = 0;
        // +X face (x=1)
        t = tri(tris, t, 1, -1, -1, 1, -1, 1, 1, 1, 1);
        t = tri(tris, t, 1, 1, 1, 1, 1, -1, 1, -1, -1);
        // -X face (x=-1)
        t = tri(tris, t, -1, -1, 1, -1, -1, -1, -1, 1, -1);
        t = tri(tris, t, -1, 1, -1, -1, 1, 1, -1, -1, 1);
        // +Y face (y=1)
        t = tri(tris, t, -1, 1, -1, 1, 1, -1, 1, 1, 1);
        t = tri(tris, t, -1, 1, 1, -1, 1, -1, 1, 1, 1);
        // -Y face (y=-1)
        t = tri(tris, t, -1, -1, -1, -1, -1, 1, 1, -1, 1);
        t = tri(tris, t, 1, -1, 1, 1, -1, -1, -1, -1, -1);
        // +Z face (z=1)
        t = tri(tris, t, -1, -1, 1, 1, -1, 1, 1, 1, 1);
        t = tri(tris, t, -1, -1, 1, 1, 1, 1, -1, 1, 1);
        // -Z face (z=-1)
        t = tri(tris, t, -1, 1, -1, 1, 1, -1, -1, -1, -1);
        t = tri(tris, t, -1, 1, -1, -1, -1, -1, 1, 1, -1);
        return tris;
    }

    private static int tri(float[] out, int t, float ax, float ay, float az,
                            float bx, float by, float bz, float cx, float cy, float cz) {
        out[t++] = ax; out[t++] = ay; out[t++] = az;
        out[t++] = bx; out[t++] = by; out[t++] = bz;
        out[t++] = cx; out[t++] = cy; out[t++] = cz;
        return t;
    }

    @Test
    void constructorValidatesInput() {
        assertThrows(IllegalArgumentException.class, () -> new SurfaceDistributor(new float[0]));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceDistributor(new float[5])); // not multiple of 9
    }

    @Test
    void distributeReturnsPointsOnSurface() {
        var dist = new SurfaceDistributor(unitCubeTriangles());
        assertEquals(12, dist.triCount());

        // High density, high probability → should get many points
        var samples = dist.distribute(10f, 1.0f, 0f, 1f, 42L);
        assertFalse(samples.isEmpty());

        // All points should be within the unit cube bounds [-1, 1]
        for (var s : samples) {
            assertTrue(s.x() >= -1.01f && s.x() <= 1.01f, "x out of range: " + s.x());
            assertTrue(s.y() >= -1.01f && s.y() <= 1.01f, "y out of range: " + s.y());
            assertTrue(s.z() >= -1.01f && s.z() <= 1.01f, "z out of range: " + s.z());
        }
    }

    @Test
    void distributeWithLowProbabilityReturnsFewer() {
        var dist = new SurfaceDistributor(unitCubeTriangles());
        var many = dist.distribute(10f, 1.0f, 0f, 1f, 42L);
        var few = dist.distribute(10f, 0.01f, 0f, 1f, 42L);
        assertTrue(few.size() < many.size(),
                "Low probability should return fewer points: " + few.size() + " vs " + many.size());
    }

    @Test
    void distributeDeterministic() {
        var dist = new SurfaceDistributor(unitCubeTriangles());
        var a = dist.distribute(5f, 0.5f, 1.0f, 10f, 123L);
        var b = dist.distribute(5f, 0.5f, 1.0f, 10f, 123L);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x(), b.get(i).x(), 1e-6f);
            assertEquals(a.get(i).y(), b.get(i).y(), 1e-6f);
            assertEquals(a.get(i).z(), b.get(i).z(), 1e-6f);
        }
    }

    @Test
    void normalsAreUnitLength() {
        var dist = new SurfaceDistributor(unitCubeTriangles());
        var samples = dist.distribute(10f, 1.0f, 0f, 1f, 42L);
        for (var s : samples) {
            float len = (float) Math.sqrt(s.nx() * s.nx() + s.ny() * s.ny() + s.nz() * s.nz());
            assertEquals(1.0f, len, 0.01f, "Normal should be unit length: " + len);
        }
    }

    @Test
    void tangentDirectionReturnsUnitVector() {
        var random = new java.util.Random(42);
        for (int i = 0; i < 100; i++) {
            float nx = random.nextFloat() * 2 - 1;
            float ny = random.nextFloat() * 2 - 1;
            float nz = random.nextFloat() * 2 - 1;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) continue;
            nx /= len; ny /= len; nz /= len;

            var dir = SurfaceDistributor.tangentDirection(nx, ny, nz, (float) Math.PI / 4, random);
            float dlen = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            assertEquals(1.0f, dlen, 0.01f, "Tangent direction should be unit length");
        }
    }
}
