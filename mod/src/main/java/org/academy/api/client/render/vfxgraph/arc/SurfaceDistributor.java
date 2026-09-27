package org.academy.api.client.render.vfxgraph.arc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SurfaceDistributor {
    private static final int FLOATS_PER_TRI = 9;

    private final float[] triangles;
    private final float[] cumulativeArea;
    private final float totalArea;
    private final int triCount;

    private final float[] normals;

    public SurfaceDistributor(float[] triangles) {
        if (triangles.length == 0 || triangles.length % FLOATS_PER_TRI != 0) {
            throw new IllegalArgumentException("triangles must be xyz*3 per triangle, got " + triangles.length);
        }
        this.triangles = triangles.clone();
        this.triCount = triangles.length / FLOATS_PER_TRI;
        this.cumulativeArea = new float[triCount];
        this.normals = new float[triCount * 3];

        var total = 0f;
        for (var i = 0; i < triCount; i++) {
            total += computeAreaAndNormal(i);
            cumulativeArea[i] = total;
        }
        this.totalArea = total;
    }

    public List<Sample> distribute(float density, float probability, float time, float frequency, long seed) {
        var random = new Random(seed);
        var result = new ArrayList<Sample>();

        if (totalArea < 1e-8f || density < 1e-6f) return result;

        var expectedCount = Math.max(1, (int) (totalArea * density));

        for (var i = 0; i < expectedCount; i++) {
            if (random.nextFloat() > probability) continue;

            var r = random.nextFloat() * totalArea;
            var tri = pickTriangle(r);

            var u = (float) Math.sqrt(random.nextFloat());
            var v = random.nextFloat();
            if (u + v > 1f) {
                u = 1f - u;
                v = 1f - v;
            }
            var w = 1f - u - v;

            var t9 = tri * 9;
            var px = triangles[t9] * w + triangles[t9 + 3] * u + triangles[t9 + 6] * v;
            var py = triangles[t9 + 1] * w + triangles[t9 + 4] * u + triangles[t9 + 7] * v;
            var pz = triangles[t9 + 2] * w + triangles[t9 + 5] * u + triangles[t9 + 8] * v;

            var n3 = tri * 3;
            var nx = normals[n3];
            var ny = normals[n3 + 1];
            var nz = normals[n3 + 2];

            result.add(new Sample(px, py, pz, nx, ny, nz));
        }
        return result;
    }

    public int triCount() {
        return triCount;
    }

    public float[] triangles() {
        return triangles;
    }

    public float[] normals() {
        return normals;
    }

    public float[] cumulativeArea() {
        return cumulativeArea;
    }

    public float totalArea() {
        return totalArea;
    }

    public static float[] perpendicular(float ax, float ay, float az) {
        var ref = Math.abs(ay) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        var t = cross(ax, ay, az, ref[0], ref[1], ref[2]);
        var len = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        if (len < 1e-6f) return new float[]{1, 0, 0};
        return new float[]{t[0] / len, t[1] / len, t[2] / len};
    }

    public static float[] fanDirection(float fx, float fy, float fz,
                                       float rx, float ry, float rz,
                                       float angle, Random random) {
        var da = random.nextFloat() * angle;
        var dc = (float) Math.cos(da);
        var ds = (float) Math.sin(da);
        return new float[]{fx * dc + rx * ds, fy * dc + ry * ds, fz * dc + rz * ds};
    }

    public static float[] coneDirection(float ax, float ay, float az, float angle, Random random) {
        var t1 = tangentBase(ax, ay, az);
        var t2 = cross(ax, ay, az, t1[0], t1[1], t1[2]);
        var a = random.nextFloat() * (float) (Math.PI * 2);
        var da = random.nextFloat() * angle;
        var dc = (float) Math.cos(da);
        var ds = (float) Math.sin(da);
        var cc = (float) Math.cos(a);
        var cs = (float) Math.sin(a);
        var ex = ax * dc + (t1[0] * cc + t2[0] * cs) * ds;
        var ey = ay * dc + (t1[1] * cc + t2[1] * cs) * ds;
        var ez = az * dc + (t1[2] * cc + t2[2] * cs) * ds;
        return new float[]{ex, ey, ez};
    }

    public static float[] tangentDirection(float nx, float ny, float nz, float angle, Random random) {
        var t1 = tangentBase(nx, ny, nz);
        var t2 = cross(nx, ny, nz, t1[0], t1[1], t1[2]);

        var a = random.nextFloat() * (float) (Math.PI * 2);
        var c = (float) Math.cos(a);
        var s = (float) Math.sin(a);

        var da = (random.nextFloat() - 0.5f) * 2f * angle;
        var dc = (float) Math.cos(da);
        var ds = (float) Math.sin(da);

        var dx = t1[0] * c + t2[0] * s;
        var dy = t1[1] * c + t2[1] * s;
        var dz = t1[2] * c + t2[2] * s;

        var rx = dx * dc + nx * ds;
        var ry = dy * dc + ny * ds;
        var rz = dz * dc + nz * ds;

        var len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1e-6f) return new float[]{nx, ny, nz};
        return new float[]{rx / len, ry / len, rz / len};
    }

    private int pickTriangle(float target) {
        int lo = 0, hi = triCount - 1;
        while (lo < hi) {
            var mid = (lo + hi) >>> 1;
            if (cumulativeArea[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private float computeAreaAndNormal(int tri) {
        var t9 = tri * 9;
        float ax = triangles[t9], ay = triangles[t9 + 1], az = triangles[t9 + 2];
        float bx = triangles[t9 + 3], by = triangles[t9 + 4], bz = triangles[t9 + 5];
        float cx = triangles[t9 + 6], cy = triangles[t9 + 7], cz = triangles[t9 + 8];

        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;
        var nx = aby * acz - abz * acy;
        var ny = abz * acx - abx * acz;
        var nz = abx * acy - aby * acx;
        var len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        var n3 = tri * 3;
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
        var ref = Math.abs(ny) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        var t = cross(nx, ny, nz, ref[0], ref[1], ref[2]);
        var len = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        return new float[]{t[0] / len, t[1] / len, t[2] / len};
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        return new float[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    public record Sample(float x, float y, float z, float nx, float ny, float nz) {
    }
}
