package org.academy.api.client.render.vfxgraph.shape;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmitterShapeMeshTest {
    private final Random random = new Random(11L);

    @Test
    void unitCubeSamplesStayOnSurface() {
        var shape = MeshShape.unitCube(0f, 0f, 0f, 1f);
        var out = new float[3];
        for (int i = 0; i < 2000; i++) {
            shape.sample(random, out);
            assertTrue(out[0] >= -1e-4f && out[0] <= 1f + 1e-4f, "x in cube");
            assertTrue(out[1] >= -1e-4f && out[1] <= 1f + 1e-4f, "y in cube");
            assertTrue(out[2] >= -1e-4f && out[2] <= 1f + 1e-4f, "z in cube");
            // 表面性：至少一维贴近立方体边界
            float eps = 1e-3f;
            boolean onSurface = out[0] < eps || out[0] > 1f - eps
                    || out[1] < eps || out[1] > 1f - eps
                    || out[2] < eps || out[2] > 1f - eps;
            assertTrue(onSurface, "point must be on cube surface: " + Arrays.toString(out));
        }
    }

    @Test
    void areaWeightedSamplingPrefersLargeTriangles() {
        // 两个三角形：大（0..10 底 × 高）与小（0..1），面积 50 vs 0.5
        float[] triangles = {
                0f, 0f, 0f, 10f, 0f, 0f, 0f, 10f, 0f,   // 大：面积 50
                0f, 0f, 1f, 1f, 0f, 1f, 0f, 1f, 1f     // 小：面积 0.5
        };
        var shape = new MeshShape(0f, 0f, 0f, 1f, triangles);
        var out = new float[3];
        int largeHits = 0;
        int samples = 5000;
        for (int i = 0; i < samples; i++) {
            shape.sample(random, out);
            // 大三角形 z≈0，小三角形 z≈1
            if (out[2] < 0.5f) {
                largeHits++;
            }
        }
        float ratio = largeHits / (float) samples;
        // 面积比 50:0.5 → 期望 ~0.99，宽松断言 > 0.9
        assertTrue(ratio > 0.9f, "large triangle hit ratio: " + ratio);
    }

    @Test
    void scaleAndOriginApplied() {
        var shape = MeshShape.unitCube(5f, 0f, -3f, 2f);
        var out = new float[3];
        for (int i = 0; i < 500; i++) {
            shape.sample(random, out);
            assertTrue(out[0] >= 5f - 1e-4f && out[0] <= 5f + 2f + 1e-4f, "x scaled+offset");
            assertTrue(out[1] >= -1e-4f && out[1] <= 2f + 1e-4f, "y scaled");
            assertTrue(out[2] >= -3f - 1e-4f && out[2] <= -3f + 2f + 1e-4f, "z scaled+offset");
        }
    }

    @Test
    void unitCubeSampledByMeshAssets() {
        MeshAssets.clear();
        MeshAssets.register("demo", ObjMeshParser.parse("""
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                f 1 2 3 4
                """));
        var triangles = MeshAssets.triangles("demo");
        assertEquals(18, triangles.length); // 一个四边形 → 2 三角形 → 18 float
        var shape = new MeshShape(0f, 0f, 0f, 1f, triangles);
        var out = new float[3];
        for (int i = 0; i < 500; i++) {
            shape.sample(random, out);
            assertTrue(out[2] > -1e-4f && out[2] < 1e-4f, "sampled on quad plane");
            assertTrue(out[0] >= -1e-4f && out[0] <= 1f + 1e-4f);
            assertTrue(out[1] >= -1e-4f && out[1] <= 1f + 1e-4f);
        }
    }
}
