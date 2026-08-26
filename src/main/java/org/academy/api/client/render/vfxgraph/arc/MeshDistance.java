package org.academy.api.client.render.vfxgraph.arc;

/**
 * 点到网格最近距离查询（M29）：复刻 Blender「闪电附着」的
 * {@code Sample Nearest Surface.002 → Vector Math(DISTANCE) → Compare(GREATER_THAN 接触范围)}。
 *
 * <p>给定三角形网格（每三角形 9 个 float：xyz*3），求任意点到网格表面的最近距离。
 * 纯函数、headless 可测。接触弧块用它剔除距接触对象超过 {@code contact_range} 的表面点。</p>
 */
public final class MeshDistance {
    private MeshDistance() {
    }

    /**
     * 求点到三角形网格表面的最近距离（点到最近三角形的平面投影 + 三角形内判定）。
     *
     * @param triangles 三角形数组（xyz*3/三角形）
     * @param px,py,pz  查询点
     * @return 最近距离（≥0；空网格返回 +inf）
     */
    public static float nearestDistance(float[] triangles, float px, float py, float pz) {
        var best = Float.MAX_VALUE;
        for (var t = 0; t + 8 < triangles.length; t += 9) {
            float ax = triangles[t], ay = triangles[t + 1], az = triangles[t + 2];
            float bx = triangles[t + 3], by = triangles[t + 4], bz = triangles[t + 5];
            float cx = triangles[t + 6], cy = triangles[t + 7], cz = triangles[t + 8];

            // 法线
            float ex1 = bx - ax, ey1 = by - ay, ez1 = bz - az;
            float ex2 = cx - ax, ey2 = cy - ay, ez2 = cz - az;
            var nx = ey1 * ez2 - ez1 * ey2;
            var ny = ez1 * ex2 - ex1 * ez2;
            var nz = ex1 * ey2 - ey1 * ex2;
            var nlen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nlen < 1e-12f) continue;
            nx /= nlen;
            ny /= nlen;
            nz /= nlen;

            // 点到平面有符号距离 + 投影点
            float dx = px - ax, dy = py - ay, dz = pz - az;
            var dist = dx * nx + dy * ny + dz * nz;
            var projX = px - nx * dist;
            var projY = py - ny * dist;
            var projZ = pz - nz * dist;

            // 重心坐标判定投影点是否在三角形内
            var d00 = ex1 * ex1 + ey1 * ey1 + ez1 * ez1;
            var d01 = ex1 * ex2 + ey1 * ey2 + ez1 * ez2;
            var d11 = ex2 * ex2 + ey2 * ey2 + ez2 * ez2;
            float e2x = projX - ax, e2y = projY - ay, e2z = projZ - az;
            var d20 = e2x * ex1 + e2y * ey1 + e2z * ez1;
            var d21 = e2x * ex2 + e2y * ey2 + e2z * ez2;
            var denom = d00 * d11 - d01 * d01;
            float u, v;
            if (Math.abs(denom) < 1e-12f) {
                u = 0;
                v = 0;
            } else {
                u = (d11 * d20 - d01 * d21) / denom;
                v = (d00 * d21 - d01 * d20) / denom;
            }

            float candidate;
            if (u >= -1e-4f && v >= -1e-4f && u + v <= 1f + 1e-4f) {
                candidate = Math.abs(dist); // 投影点在三角形内 → 平面距离
            } else {
                candidate = (float) Math.sqrt(nearestToTriangleSq(px, py, pz, ax, ay, az, bx, by, bz, cx, cy, cz));
            }
            if (candidate < best) best = candidate;
        }
        return best;
    }

    /**
     * 点到三角形最近距离的平方（Closest Point on Triangle，Ericson 实现）。
     */
    private static float nearestToTriangleSq(float px, float py, float pz,
                                             float ax, float ay, float az,
                                             float bx, float by, float bz,
                                             float cx, float cy, float cz) {
        // 最近点（退化到点到线段/点）
        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;
        float apx = px - ax, apy = py - ay, apz = pz - az;

        var d1 = apx * abx + apy * aby + apz * abz;
        var d2 = apx * acx + apy * acy + apz * acz;
        if (d1 <= 0 && d2 <= 0) return distSq(px, py, pz, ax, ay, az);

        float bpx = px - bx, bpy = py - by, bpz = pz - bz;
        var d3 = bpx * abx + bpy * aby + bpz * abz;
        var d4 = bpx * acx + bpy * acy + bpz * acz;
        if (d3 >= 0 && d4 <= d3) return distSq(px, py, pz, bx, by, bz);

        var vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) {
            var t = d1 / (d1 - d3);
            float qx = ax + t * abx, qy = ay + t * aby, qz = az + t * abz;
            return distSq(px, py, pz, qx, qy, qz);
        }

        float cpx = px - cx, cpy = py - cy, cpz = pz - cz;
        var d5 = cpx * abx + cpy * aby + cpz * abz;
        var d6 = cpx * acx + cpy * acy + cpz * acz;
        if (d6 >= 0 && d5 <= d6) return distSq(px, py, pz, cx, cy, cz);

        var vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) {
            var t = d2 / (d2 - d6);
            float qx = ax + t * acx, qy = ay + t * acy, qz = az + t * acz;
            return distSq(px, py, pz, qx, qy, qz);
        }

        var va = d3 * d6 - d5 * d4;
        if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
            var t = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            float qx = bx + t * (cx - bx), qy = by + t * (cy - by), qz = bz + t * (cz - bz);
            return distSq(px, py, pz, qx, qy, qz);
        }

        var denom = 1f / (va + vb + vc);
        var v = vb * denom;
        var w = vc * denom;
        var u = 1f - v - w;
        var qx = ax + u * abx + v * acx;
        var qy = ay + u * aby + v * acy;
        var qz = az + u * abz + v * acz;
        return distSq(px, py, pz, qx, qy, qz);
    }

    private static float distSq(float px, float py, float pz, float qx, float qy, float qz) {
        float dx = px - qx, dy = py - qy, dz = pz - qz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 调试辅助（包内）：返回第 t 个三角形上最近点。
     */
    static float[] closestPointOnTriangleAt(float[] triangles, int t, float px, float py, float pz) {
        var base = t * 9;
        float ax = triangles[base], ay = triangles[base + 1], az = triangles[base + 2];
        float e1x = triangles[base + 3] - ax, e1y = triangles[base + 4] - ay, e1z = triangles[base + 5] - az;
        float e2x = triangles[base + 6] - ax, e2y = triangles[base + 7] - ay, e2z = triangles[base + 8] - az;
        return closestPointOnTriangle(px, py, pz, ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z);
    }

    /**
     * 求点到网格表面的最近点（复刻 Blender {@code Sample Nearest Surface}）。
     *
     * @param triangles 三角形数组（xyz*3/三角形）
     * @param px,py,pz  查询点
     * @return 最近表面点 [x,y,z]；空网格返回原查询点
     */
    public static float[] nearestPoint(float[] triangles, float px, float py, float pz) {
        var best = Float.MAX_VALUE;
        float bx = px, by = py, bz = pz;
        for (var t = 0; t + 8 < triangles.length; t += 9) {
            float ax = triangles[t], ay = triangles[t + 1], az = triangles[t + 2];
            float e1x = triangles[t + 3] - ax, e1y = triangles[t + 4] - ay, e1z = triangles[t + 5] - az;
            float e2x = triangles[t + 6] - ax, e2y = triangles[t + 7] - ay, e2z = triangles[t + 8] - az;
            var q = closestPointOnTriangle(px, py, pz, ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z);
            var d = distSq(px, py, pz, q[0], q[1], q[2]);
            if (d < best) {
                best = d;
                bx = q[0];
                by = q[1];
                bz = q[2];
            }
        }
        return new float[]{bx, by, bz};
    }

    /**
     * 点到三角形的最近点（Closest Point on Triangle，Ericson 算法）。
     * 三角形以 (a, e1, e2) 表示（a 原点，e1=b-a，e2=c-a），无需显式 b/c 顶点。
     *
     * @return 最近点 [x,y,z]
     */
    private static float[] closestPointOnTriangle(float px, float py, float pz,
                                                  float ax, float ay, float az,
                                                  float e1x, float e1y, float e1z,
                                                  float e2x, float e2y, float e2z) {
        float apx = px - ax, apy = py - ay, apz = pz - az;
        var d1 = apx * e1x + apy * e1y + apz * e1z;
        var d2 = apx * e2x + apy * e2y + apz * e2z;
        if (d1 <= 0 && d2 <= 0) return new float[]{ax, ay, az};

        float bx = ax + e1x, by = ay + e1y, bz = az + e1z;
        float bpx = px - bx, bpy = py - by, bpz = pz - bz;
        var d3 = bpx * e1x + bpy * e1y + bpz * e1z;
        var d4 = bpx * e2x + bpy * e2y + bpz * e2z;
        if (d3 >= 0 && d4 <= d3) return new float[]{bx, by, bz};

        float cx = ax + e2x, cy = ay + e2y, cz = az + e2z;
        float cpx = px - cx, cpy = py - cy, cpz = pz - cz;
        var d5 = cpx * e1x + cpy * e1y + cpz * e1z;
        var d6 = cpx * e2x + cpy * e2y + cpz * e2z;
        if (d6 >= 0 && d5 <= d6) return new float[]{cx, cy, cz};

        var vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) {
            var t = d1 / (d1 - d3);
            return new float[]{ax + t * e1x, ay + t * e1y, az + t * e1z};
        }

        var vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) {
            var t = d2 / (d2 - d6);
            return new float[]{ax + t * e2x, ay + t * e2y, az + t * e2z};
        }

        var va = d3 * d6 - d5 * d4;
        if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
            var t = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            return new float[]{bx + t * (cx - bx), by + t * (cy - by), bz + t * (cz - bz)};
        }

        var denom = 1f / (va + vb + vc);
        var v = vb * denom;
        var w = vc * denom;
        return new float[]{ax + v * e1x + w * e2x, ay + v * e1y + w * e2y, az + v * e1z + w * e2z};
    }
}
