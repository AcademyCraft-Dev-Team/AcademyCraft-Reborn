package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.academy.api.client.render.vfxgraph.shape.MeshAssets;
import org.junit.jupiter.api.Test;

/** M29：内置表面生成器单测（复刻 Blender Plane/Sphere）。 */
class MeshAssetsBuiltinTest {

    @Test
    void resolvePlaneReturnsTwoTriangles() {
        var tris = MeshAssets.resolve("builtin:plane");
        assertEquals(2 * 9, tris.length);
        // 平面 y 全部为 0，x/z 在 [-1,1]
        for (int i = 0; i < tris.length; i += 3) {
            assertEquals(0f, tris[i + 1], 1e-6f, "plane y should be 0");
            assertTrue(Math.abs(tris[i]) <= 1f, "plane x in [-1,1]");
            assertTrue(Math.abs(tris[i + 2]) <= 1f, "plane z in [-1,1]");
        }
    }

    @Test
    void resolveSphereNonEmpty() {
        var tris = MeshAssets.resolve("builtin:sphere");
        assertTrue(tris.length > 0);
        assertEquals(0, tris.length % 9);
        // 所有点都在球面附近（半径 1）
        for (int i = 0; i < tris.length; i += 3) {
            float x = tris[i], y = tris[i + 1], z = tris[i + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            assertEquals(1f, len, 0.01f, "sphere vertex should be on unit sphere");
        }
    }

    @Test
    void resolveUnknownReturnsNull() {
        assertEquals(null, MeshAssets.resolve("nonexistent"));
    }

    @Test
    void planeGeneratesUpwardNormals() {
        var dist = new SurfaceDistributor(MeshAssets.plane(2f));
        for (int i = 0; i < dist.triCount(); i++) {
            float ny = dist.normals()[i * 3 + 1];
            assertTrue(ny > 0.99f, "plane normal should point +Y, got ny=" + ny);
        }
    }

    @Test
    void sphereGeneratesOutwardNormals() {
        var tris = MeshAssets.sphere(1f, 12);
        var dist = new SurfaceDistributor(tris);
        int checked = 0;
        for (int t = 0; t < dist.triCount(); t++) {
            float nx = dist.normals()[t * 3];
            float ny = dist.normals()[t * 3 + 1];
            float nz = dist.normals()[t * 3 + 2];
            // 三角形中心
            float cx = (tris[t * 9] + tris[t * 9 + 3] + tris[t * 9 + 6]) / 3f;
            float cy = (tris[t * 9 + 1] + tris[t * 9 + 4] + tris[t * 9 + 7]) / 3f;
            float cz = (tris[t * 9 + 2] + tris[t * 9 + 5] + tris[t * 9 + 8]) / 3f;
            float dot = nx * cx + ny * cy + nz * cz;
            assertTrue(dot > 0f, "sphere normal should point outward, dot=" + dot);
            checked++;
        }
        assertTrue(checked > 10, "sphere should have many triangles, got " + checked);
    }
}