package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;

final class TriangleSurface {
    private TriangleSurface() {
    }

    static int count(float[] triangles) {
        return triangles.length / 9;
    }

    static boolean sample(float[] triangles, int triangle, float u, float v,
                          Vector3f position, Vector3f normal) {
        var base = triangle * 9;
        var ax = triangles[base];
        var ay = triangles[base + 1];
        var az = triangles[base + 2];
        var bx = triangles[base + 3];
        var by = triangles[base + 4];
        var bz = triangles[base + 5];
        var cx = triangles[base + 6];
        var cy = triangles[base + 7];
        var cz = triangles[base + 8];
        var s = Math.clamp(u, 0f, 1f);
        var t = Math.clamp(v, 0f, 1f);
        var sum = s + t;
        if (sum > 1f) {
            s /= sum;
            t /= sum;
        }
        position.set(
                ax + (bx - ax) * s + (cx - ax) * t,
                ay + (by - ay) * s + (cy - ay) * t,
                az + (bz - az) * s + (cz - az) * t
        );
        var nx = (by - ay) * (cz - az) - (bz - az) * (cy - ay);
        var ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az);
        var nz = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        var length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-8f) return false;
        normal.set(nx / length, ny / length, nz / length);
        return true;
    }
}
