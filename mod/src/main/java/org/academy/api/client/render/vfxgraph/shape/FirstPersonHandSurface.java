package org.academy.api.client.render.vfxgraph.shape;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class FirstPersonHandSurface {
    private static final List<Capture> CAPTURES = new ArrayList<>(4);

    private FirstPersonHandSurface() {
    }

    public static void beginFrame() {
        CAPTURES.clear();
    }

    public static void addArm(HumanoidArm arm, Matrix4f transform, boolean slim) {
        var min = new Vector3f();
        var max = new Vector3f();
        HumanoidSurfaceSampler.armBounds(arm == HumanoidArm.RIGHT, slim, min, max);
        CAPTURES.add(new Capture(arm, boxTriangles(min, max, transform)));
    }

    public static void addItem(HumanoidArm arm, Matrix4f transform, List<BakedQuad> quads) {
        var triangles = fromQuads(transform, quads);
        if (triangles.length > 0) CAPTURES.add(new Capture(arm, triangles));
    }

    public static void addBox(HumanoidArm arm, Matrix4f transform,
                              float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ) {
        CAPTURES.add(new Capture(arm, boxTriangles(
                new Vector3f(minX, minY, minZ), new Vector3f(maxX, maxY, maxZ), transform)));
    }

    static float[] fromQuads(Matrix4f transform, List<BakedQuad> quads) {
        if (quads.isEmpty()) return new float[0];
        var out = new ArrayList<Float>(quads.size() * 18);
        for (var quad : quads) {
            append(out, transform, quad.position0(), quad.position1(), quad.position2());
            append(out, transform, quad.position0(), quad.position2(), quad.position3());
        }
        var triangles = toArray(out);
        var center = centroid(triangles);
        orientOutward(triangles, center.x, center.y, center.z);
        return triangles;
    }

    public static List<Capture> captures() {
        return CAPTURES;
    }

    static float[] boxTriangles(Vector3f min, Vector3f max, Matrix4f transform) {
        var out = new float[6 * 18];
        var corners = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        var normal = new Vector3f();
        var offset = 0;
        for (var face = 0; face < 6; face++) {
            SurfaceSampler.box(face, 0f, 0f, min, max, transform, corners[0], normal);
            SurfaceSampler.box(face, 1f, 0f, min, max, transform, corners[1], normal);
            SurfaceSampler.box(face, 1f, 1f, min, max, transform, corners[2], normal);
            SurfaceSampler.box(face, 0f, 1f, min, max, transform, corners[3], normal);
            offset = append(out, offset, corners[0], corners[1], corners[2]);
            offset = append(out, offset, corners[0], corners[2], corners[3]);
        }
        var center = transform.transformPosition(new Vector3f(
                (min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f, (min.z + max.z) * 0.5f));
        orientOutward(out, center.x, center.y, center.z);
        return out;
    }

    private static void append(ArrayList<Float> out, Matrix4f transform, Vector3fc a, Vector3fc b, Vector3fc c) {
        append(out, transform.transformPosition(new Vector3f(a)));
        append(out, transform.transformPosition(new Vector3f(b)));
        append(out, transform.transformPosition(new Vector3f(c)));
    }

    private static void append(ArrayList<Float> out, Vector3f point) {
        out.add(point.x);
        out.add(point.y);
        out.add(point.z);
    }

    private static int append(float[] out, int offset, Vector3f a, Vector3f b, Vector3f c) {
        for (var point : new Vector3f[]{a, b, c}) {
            out[offset++] = point.x;
            out[offset++] = point.y;
            out[offset++] = point.z;
        }
        return offset;
    }

    private static float[] toArray(ArrayList<Float> values) {
        var out = new float[values.size()];
        for (var i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static Vector3f centroid(float[] triangles) {
        var center = new Vector3f();
        var count = triangles.length / 3;
        if (count == 0) return center;
        for (var i = 0; i < triangles.length; i += 3) {
            center.x += triangles[i];
            center.y += triangles[i + 1];
            center.z += triangles[i + 2];
        }
        return center.div(count);
    }

    private static void orientOutward(float[] triangles, float cx, float cy, float cz) {
        for (var i = 0; i + 8 < triangles.length; i += 9) {
            var ax = triangles[i];
            var ay = triangles[i + 1];
            var az = triangles[i + 2];
            var bx = triangles[i + 3];
            var by = triangles[i + 4];
            var bz = triangles[i + 5];
            var gx = triangles[i + 6];
            var gy = triangles[i + 7];
            var gz = triangles[i + 8];
            var nx = (by - ay) * (gz - az) - (bz - az) * (gy - ay);
            var ny = (bz - az) * (gx - ax) - (bx - ax) * (gz - az);
            var nz = (bx - ax) * (gy - ay) - (by - ay) * (gx - ax);
            var tx = (ax + bx + gx) / 3f - cx;
            var ty = (ay + by + gy) / 3f - cy;
            var tz = (az + bz + gz) / 3f - cz;
            if (nx * tx + ny * ty + nz * tz >= 0f) continue;
            triangles[i + 3] = gx;
            triangles[i + 4] = gy;
            triangles[i + 5] = gz;
            triangles[i + 6] = bx;
            triangles[i + 7] = by;
            triangles[i + 8] = bz;
        }
    }

    public record Capture(HumanoidArm arm, float[] triangles) {
    }
}
