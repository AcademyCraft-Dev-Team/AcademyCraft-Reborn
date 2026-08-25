package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SurfaceConstraintTest {

    /** 单位立方体三角形。 */
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
        out[t++] = ax; out[t++] = ay; out[t++] = az;
        out[t++] = bx; out[t++] = by; out[t++] = bz;
        out[t++] = cx; out[t++] = cy; out[t++] = cz;
        return t;
    }

    @Test
    void constrainPullsPointsToSurface() {
        var dist = new SurfaceDistributor(unitCube());
        var constraint = new SurfaceConstraint(dist);

        var arc = new ArcCurve();
        // 3 points, same segment (single run): endpoints are 0 and 2
        arc.addPoint(2, 0, 0, 0.01f, 0);
        arc.addPoint(0, 2, 0, 0.01f, 0);
        arc.addPoint(0, 0, 2, 0.01f, 0);
        arc.setColor(1, 1, 1, 1);

        constraint.constrain(arc);

        // 端点（0 和 2）被拉到最近表面（x=1 或 z=1）
        float d0 = Math.max(Math.abs(arc.x(0)), Math.max(Math.abs(arc.y(0)), Math.abs(arc.z(0))));
        float d2 = Math.max(Math.abs(arc.x(2)), Math.max(Math.abs(arc.y(2)), Math.abs(arc.z(2))));
        assertTrue(d0 <= 1.02f, "Endpoint 0 should be on surface: " + arc.x(0) + "," + arc.y(0) + "," + arc.z(0));
        assertTrue(d2 <= 1.02f, "Endpoint 2 should be on surface: " + arc.x(2) + "," + arc.y(2) + "," + arc.z(2));
        // 中间点（1）保留原位置（参考只约束 Endpoint，中间点保持拱起/漂浮形态）
        assertEquals(2f, arc.y(1), 1e-4f);
    }

    @Test
    void interiorPointsNotConstrained() {
        var dist = new SurfaceDistributor(unitCube());
        var constraint = new SurfaceConstraint(dist);

        var arc = new ArcCurve();
        arc.addPoint(5, 0, 0, 0.01f, 0, 0); // endpoint run0
        arc.addPoint(4, 0, 0, 0.01f, 0, 0); // interior
        arc.addPoint(3, 0, 0, 0.01f, 0, 0); // interior
        arc.addPoint(2, 0, 0, 0.01f, 0, 0); // endpoint run0
        arc.setColor(1, 1, 1, 1);

        constraint.constrain(arc);

        // 端点被拉回 x=1 表面
        assertEquals(1f, arc.x(0), 0.02f);
        assertEquals(1f, arc.x(3), 0.02f);
        // 内部点不受约束（保持在 4,3）
        assertEquals(4f, arc.x(1), 0.02f);
        assertEquals(3f, arc.x(2), 0.02f);
    }

    @Test
    void constrainPointsAlreadyOnSurfaceStay() {
        var dist = new SurfaceDistributor(unitCube());
        var constraint = new SurfaceConstraint(dist);

        var arc = new ArcCurve();
        arc.addPoint(1, 0, 0, 0.01f, 0); // on +X face
        arc.addPoint(0, 1, 0, 0.01f, 0); // on +Y face
        arc.addPoint(0, 0, 1, 0.01f, 0); // on +Z face
        arc.setColor(1, 1, 1, 1);

        constraint.constrain(arc);

        assertEquals(1, arc.x(0), 0.02f);
        assertEquals(1, arc.y(1), 0.02f);
        assertEquals(1, arc.z(2), 0.02f);
    }
}
