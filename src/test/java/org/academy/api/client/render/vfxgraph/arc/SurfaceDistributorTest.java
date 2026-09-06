package org.academy.api.client.render.vfxgraph.arc;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceDistributorTest {

    /**
     * 单位立方体三角形数据（6面×2三角形=12三角形，108 floats）。
     */
    private static float[] unitCubeTriangles() {
        var tris = new float[12 * 9];
        var t = 0;
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
        for (var i = 0; i < a.size(); i++) {
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
            var len = (float) Math.sqrt(s.nx() * s.nx() + s.ny() * s.ny() + s.nz() * s.nz());
            assertEquals(1.0f, len, 0.01f, "Normal should be unit length: " + len);
        }
    }

    @Test
    void tangentDirectionReturnsUnitVector() {
        var random = new Random(42);
        for (var i = 0; i < 100; i++) {
            var nx = random.nextFloat() * 2 - 1;
            var ny = random.nextFloat() * 2 - 1;
            var nz = random.nextFloat() * 2 - 1;
            var len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) continue;
            nx /= len;
            ny /= len;
            nz /= len;

            var dir = SurfaceDistributor.tangentDirection(nx, ny, nz, (float) Math.PI / 4, random);
            var dlen = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            assertEquals(1.0f, dlen, 0.01f, "Tangent direction should be unit length");
        }
    }

    /**
     * fanDirection 应始终落在 (forward, right) 平面内、朝 forward 单侧扇形张开
     * （分支前倾成树，而非绕树干径向散开的鸡毛掸子），且为单位向量。
     */
    @Test
    void fanDirectionStaysInForwardPlaneOneSide() {
        var random = new Random(7);
        float fx = 0f, fy = 1f, fz = 0f;
        var right = SurfaceDistributor.perpendicular(fx, fy, fz);
        var angle = 0.5f;
        for (var i = 0; i < 200; i++) {
            var dir = SurfaceDistributor.fanDirection(fx, fy, fz, right[0], right[1], right[2], angle, random);
            var len = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            assertEquals(1.0f, len, 0.01f, "fan direction should be unit length");
            // 前倾：与 forward 的夹角 ≤ angle（单侧扇形，dot ≥ cos(angle)）
            var dot = dir[0] * fx + dir[1] * fy + dir[2] * fz;
            assertTrue(dot >= (float) Math.cos(angle) - 1e-4f,
                    "branch must lean forward within fan, dot=" + dot);
            // 平面内：与平面法线（forward × right）的点积 ≈ 0
            var nrmX = fy * right[2] - fz * right[1];
            var nrmY = fz * right[0] - fx * right[2];
            var nrmZ = fx * right[1] - fy * right[0];
            var outPlane = dir[0] * nrmX + dir[1] * nrmY + dir[2] * nrmZ;
            assertEquals(0.0f, outPlane, 0.01f, "branch must stay in the forward/right plane");
        }
    }

    /**
     * coneDirection 应在以 axis 为轴、半角 angle 的锥体内均匀散开（3D 树状分叉），
     * 且为单位向量。
     */
    @Test
    void coneDirectionStaysWithinCone() {
        var random = new Random(11);
        float ax = 0f, ay = 1f, az = 0f;
        var angle = 0.5f;
        for (var i = 0; i < 400; i++) {
            var dir = SurfaceDistributor.coneDirection(ax, ay, az, angle, random);
            var len = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            assertEquals(1.0f, len, 0.01f, "cone direction should be unit length");
            // 与 axis 的夹角 ≤ angle：dot ≥ cos(angle)
            var dot = dir[0] * ax + dir[1] * ay + dir[2] * az;
            assertTrue(dot >= (float) Math.cos(angle) - 1e-4f,
                    "branch must stay within cone half-angle, dot=" + dot);
        }
    }
}
