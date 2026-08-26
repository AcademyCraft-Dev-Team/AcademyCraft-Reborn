package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.shape.MeshAssets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M29：点到网格最近距离工具单测（复刻 Blender Sample Nearest Surface + DISTANCE）。
 */
class MeshDistanceTest {

    private static final float[] PLANE = MeshAssets.plane(2f);

    @Test
    void pointOnPlaneIsZero() {
        assertEquals(0f, MeshDistance.nearestDistance(PLANE, 0, 0, 0), 1e-4f);
        assertEquals(0f, MeshDistance.nearestDistance(PLANE, 0.5f, 0, -0.3f), 1e-4f);
    }

    @Test
    void pointAbovePlaneIsItsHeight() {
        assertEquals(3f, MeshDistance.nearestDistance(PLANE, 0, 3, 0), 1e-3f);
    }

    @Test
    void pointOutsidePlaneEdgeClampedToEdge() {
        // 平面角点 (±1,0,±1)；查询 (2,0,2) 最近角点 (1,0,1) 距离 = √2
        float d = MeshDistance.nearestDistance(PLANE, 2, 0, 2);
        assertEquals((float) Math.sqrt(2), d, 1e-3f);
    }

    @Test
    void sphereDistanceFromCenterIsRadius() {
        var sphere = MeshAssets.sphere(1f, 12);
        float d = MeshDistance.nearestDistance(sphere, 0, 0, 0);
        // 多面体球面：中心到三角面距离 ≈ 半径 × cos(半角)，12 段 ≈ 0.93~1.0
        assertTrue(d > 0.9f && d <= 1.0f, "distance from sphere center should be ~radius, got " + d);
    }

    @Test
    void emptyMeshReturnsInfinity() {
        assertEquals(Float.MAX_VALUE, MeshDistance.nearestDistance(new float[0], 0, 0, 0), 0f);
    }

    @Test
    void contactRangeCullsFarPoints() {
        var sphere = MeshAssets.resolve("builtin:sphere");
        float near = MeshDistance.nearestDistance(sphere, 0.52f, 0, 0.38f); // 靠近球面
        float far = MeshDistance.nearestDistance(sphere, 5f, 0, 5f);        // 远离球
        assertTrue(near < far, "near should be closer than far: " + near + " vs " + far);
        // 接触范围 4.1：far 应超出，near 应在内
        assertTrue(far > 4.1f);
        assertTrue(near < 4.1f);
    }
}
