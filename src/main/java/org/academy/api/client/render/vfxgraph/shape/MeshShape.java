package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/**
 * 网格表面发射形状（A3，ADR-023）：在三角形网格表面按面积加权均匀采样。
 *
 * <p>构造函数预计算每三角形面积与累计权重；{@link #sample} 用均匀随机 + 二分选三角形、
 * 重心坐标取表面点，乘 {@code scale} 并平移到 {@code (ox,oy,oz)}。无网格数据时用
 * {@link #unitCube} 兜底（与 {@code vfx.output_mesh} 的单位立方体渲染一致）。</p>
 */
public final class MeshShape implements EmitterShape {
    private static final int FLOATS_PER_TRIANGLE = 9;

    private final float ox;
    private final float oy;
    private final float oz;
    private final float scale;
    private final float[] triangles;
    private final float[] cumulativeArea;
    private final float totalArea;

    public MeshShape(float ox, float oy, float oz, float scale, float[] triangles) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.scale = scale;
        if (triangles.length == 0 || triangles.length % FLOATS_PER_TRIANGLE != 0) {
            throw new IllegalArgumentException("triangles must be xyz*3 per triangle");
        }
        this.triangles = triangles.clone();
        int triCount = triangles.length / FLOATS_PER_TRIANGLE;
        this.cumulativeArea = new float[triCount];
        float total = 0f;
        for (int i = 0; i < triCount; i++) {
            total += triangleArea(i);
            cumulativeArea[i] = total;
        }
        this.totalArea = total;
    }

    /**
     * 单位立方体（0..1 范围，6 面 × 2 三角形），与 {@code vfx.output_mesh} 渲染一致。
     */
    public static MeshShape unitCube(float ox, float oy, float oz, float scale) {
        float[] cube = new float[6 * 2 * FLOATS_PER_TRIANGLE];
        int t = 0;
        for (int face = 0; face < 6; face++) {
            var quad = CUBE_FACES[face];
            // quad 顶点顺序：逆时针，拆成两个三角形 (0,1,2)/(0,2,3)
            t = pushTriangle(cube, t, quad[0], quad[1], quad[2]);
            t = pushTriangle(cube, t, quad[0], quad[2], quad[3]);
        }
        return new MeshShape(ox, oy, oz, scale, cube);
    }

    private static int pushTriangle(float[] out, int t, float[] a, float[] b, float[] c) {
        out[t++] = a[0];
        out[t++] = a[1];
        out[t++] = a[2];
        out[t++] = b[0];
        out[t++] = b[1];
        out[t++] = b[2];
        out[t++] = c[0];
        out[t++] = c[1];
        out[t++] = c[2];
        return t;
    }

    @Override
    public void sample(Random random, float[] out) {
        if (totalArea <= 0f) {
            out[0] = ox;
            out[1] = oy;
            out[2] = oz;
            return;
        }
        int tri = pickTriangle(random.nextFloat() * totalArea);
        int base = tri * FLOATS_PER_TRIANGLE;
        float ax = triangles[base], ay = triangles[base + 1], az = triangles[base + 2];
        float bx = triangles[base + 3], by = triangles[base + 4], bz = triangles[base + 5];
        float cx = triangles[base + 6], cy = triangles[base + 7], cz = triangles[base + 8];
        float u = (float) Math.sqrt(random.nextFloat());
        float v = random.nextFloat();
        float px = (1f - u) * ax + u * ((1f - v) * bx + v * cx);
        float py = (1f - u) * ay + u * ((1f - v) * by + v * cy);
        float pz = (1f - u) * az + u * ((1f - v) * bz + v * cz);
        out[0] = ox + px * scale;
        out[1] = oy + py * scale;
        out[2] = oz + pz * scale;
    }

    private int pickTriangle(float target) {
        int lo = 0;
        int hi = cumulativeArea.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulativeArea[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private float triangleArea(int index) {
        int base = index * FLOATS_PER_TRIANGLE;
        float ax = triangles[base], ay = triangles[base + 1], az = triangles[base + 2];
        float abx = triangles[base + 3] - ax, aby = triangles[base + 4] - ay, abz = triangles[base + 5] - az;
        float acx = triangles[base + 6] - ax, acy = triangles[base + 7] - ay, acz = triangles[base + 8] - az;
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        return 0.5f * (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    // 单位立方体 6 面（每面 4 顶点，逆时针）
    private static final float[][][] CUBE_FACES = {
            {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}, // -Z
            {{0, 0, 1}, {0, 1, 1}, {1, 1, 1}, {1, 0, 1}}, // +Z
            {{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}}, // -X
            {{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}}, // +X
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, // -Y
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // +Y
    };
}
