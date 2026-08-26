package org.academy.api.client.render.vfxgraph.arc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 表面布点器（M22-Rev2）：在三角形网格表面按面积加权均匀撒点。
 *
 * <p>复用 {@code MeshShape} 的面积加权采样逻辑（二分选三角形 + 重心坐标取点），
 * 增加法线计算、概率过滤、时间频率控制（复刻 Blender 随机点云阵列子组）。</p>
 */
public final class SurfaceDistributor {
    private static final int FLOATS_PER_TRI = 9;

    private final float[] triangles;
    private final float[] cumulativeArea;
    private final float totalArea;
    private final int triCount;

    /**
     * 临时存储每个三角形的法线（构造时预计算）。
     */
    private final float[] normals;

    public SurfaceDistributor(float[] triangles) {
        if (triangles.length == 0 || triangles.length % FLOATS_PER_TRI != 0) {
            throw new IllegalArgumentException("triangles must be xyz*3 per triangle, got " + triangles.length);
        }
        this.triangles = triangles.clone();
        this.triCount = triangles.length / FLOATS_PER_TRI;
        this.cumulativeArea = new float[triCount];
        this.normals = new float[triCount * 3];

        float total = 0f;
        for (int i = 0; i < triCount; i++) {
            total += computeAreaAndNormal(i);
            cumulativeArea[i] = total;
        }
        this.totalArea = total;
    }

    /**
     * 在表面撒点。
     *
     * <p>注意：本方法只做**单帧纯布点**（面积加权 + 概率过滤），不再做时间频率门控——
     * 帧周期断续时序（复刻 Blender {@code Compare(Frame MOD N) EQUAL 0}）由调用方
     * （{@code VfxBlocks.arcSurface/arcContact}）在块级按帧周期控制，避免每帧全密度 spawn
     * 导致弧数爆炸（M29b-01 修复）。{@code time}/{@code frequency} 保留为兼容签名。</p>
     *
     * @param density     每单位面积的期望点数
     * @param probability 每个点的保留概率（0~1）
     * @param time        当前时间（保留，未使用；兼容签名）
     * @param frequency   散布频率（保留，未使用；兼容签名）
     * @param seed        随机种子
     * @return 采样结果列表（position + normal）
     */
    public List<Sample> distribute(float density, float probability, float time, float frequency, long seed) {
        var random = new Random(seed);
        var result = new ArrayList<Sample>();

        if (totalArea < 1e-8f || density < 1e-6f) return result;

        // 期望点数 = 面积 × 密度
        int expectedCount = Math.max(1, (int) (totalArea * density));

        for (int i = 0; i < expectedCount; i++) {
            // 概率过滤（复刻 Blender 的 Random Value + Delete Geometry）
            if (random.nextFloat() > probability) continue;

            // 面积加权采样一个三角形
            float r = random.nextFloat() * totalArea;
            int tri = pickTriangle(r);

            // 重心坐标取点（均匀分布，u+v<=1 翻转保证在三角形内）
            float u = (float) Math.sqrt(random.nextFloat());
            float v = random.nextFloat();
            if (u + v > 1f) {
                u = 1f - u;
                v = 1f - v;
            }
            float w = 1f - u - v;

            int t9 = tri * 9;
            float px = triangles[t9] * w + triangles[t9 + 3] * u + triangles[t9 + 6] * v;
            float py = triangles[t9 + 1] * w + triangles[t9 + 4] * u + triangles[t9 + 7] * v;
            float pz = triangles[t9 + 2] * w + triangles[t9 + 5] * u + triangles[t9 + 8] * v;

            // 法线
            int n3 = tri * 3;
            float nx = normals[n3];
            float ny = normals[n3 + 1];
            float nz = normals[n3 + 2];

            result.add(new Sample(px, py, pz, nx, ny, nz));
        }
        return result;
    }

    /**
     * 获取三角形数量。
     */
    public int triCount() {
        return triCount;
    }

    /**
     * 获取原始三角形数据。
     */
    public float[] triangles() {
        return triangles;
    }

    /**
     * 获取预计算的法线数组。
     */
    public float[] normals() {
        return normals;
    }

    /**
     * 获取累计面积数组。
     */
    public float[] cumulativeArea() {
        return cumulativeArea;
    }

    /**
     * 获取总面积。
     */
    public float totalArea() {
        return totalArea;
    }

    /**
     * 按法线方向在切平面内采样一个方向向量。
     *
     * @param nx,ny,nz 表面法线
     * @param angle    扰动角度（弧度）
     * @param random   随机源
     * @return 归一化的方向向量 [x,y,z]
     */
    public static float[] tangentDirection(float nx, float ny, float nz, float angle, Random random) {
        // 构建切平面基
        float[] t1 = tangentBase(nx, ny, nz);
        float[] t2 = cross(nx, ny, nz, t1[0], t1[1], t1[2]);

        // 随机角度
        float a = random.nextFloat() * (float) (Math.PI * 2);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);

        // 扰动角度
        float da = (random.nextFloat() - 0.5f) * 2f * angle;
        float dc = (float) Math.cos(da);
        float ds = (float) Math.sin(da);

        // 组合：先绕法线旋转 a，再倾斜 da
        float dx = t1[0] * c + t2[0] * s;
        float dy = t1[1] * c + t2[1] * s;
        float dz = t1[2] * c + t2[2] * s;

        // 倾斜
        float rx = dx * dc + nx * ds;
        float ry = dy * dc + ny * ds;
        float rz = dz * dc + nz * ds;

        float len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1e-6f) return new float[]{nx, ny, nz};
        return new float[]{rx / len, ry / len, rz / len};
    }

    // --- 内部方法 ---

    private int pickTriangle(float target) {
        int lo = 0, hi = triCount - 1;
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

    /**
     * 计算三角形面积并预计算法线。返回面积。
     */
    private float computeAreaAndNormal(int tri) {
        int t9 = tri * 9;
        float ax = triangles[t9], ay = triangles[t9 + 1], az = triangles[t9 + 2];
        float bx = triangles[t9 + 3], by = triangles[t9 + 4], bz = triangles[t9 + 5];
        float cx = triangles[t9 + 6], cy = triangles[t9 + 7], cz = triangles[t9 + 8];

        // AB × AC
        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        int n3 = tri * 3;
        if (len < 1e-8f) {
            normals[n3] = 0;
            normals[n3 + 1] = 1;
            normals[n3 + 2] = 0;
        } else {
            normals[n3] = nx / len;
            normals[n3 + 1] = ny / len;
            normals[n3 + 2] = nz / len;
        }

        return len * 0.5f;
    }

    private static float[] tangentBase(float nx, float ny, float nz) {
        // 与法线不平行的参考向量
        float[] ref = Math.abs(ny) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] t = cross(nx, ny, nz, ref[0], ref[1], ref[2]);
        float len = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        return new float[]{t[0] / len, t[1] / len, t[2] / len};
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        return new float[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    /**
     * 表面采样结果。
     */
    public record Sample(float x, float y, float z, float nx, float ny, float nz) {
    }
}
