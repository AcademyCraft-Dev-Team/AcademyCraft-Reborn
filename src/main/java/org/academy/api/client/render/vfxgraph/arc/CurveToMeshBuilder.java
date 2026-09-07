package org.academy.api.client.render.vfxgraph.arc;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/** Tube extrusion with stable disconnected runs and caller-owned streaming buffers. */
public final class CurveToMeshBuilder {
    public static final int FLOATS_PER_VERTEX = 12;
    public static final int VERTEX_STRIDE = FLOATS_PER_VERTEX * 4;
    private record Profile(float[] cos, float[] sin) { }
    private static final ConcurrentHashMap<Integer, Profile> PROFILES = new ConcurrentHashMap<>();
    private CurveToMeshBuilder() { }

    public record Size(int vertices, int indices) { }

    private static int resolution(ArcCurve arc, int requested) {
        if (arc.maxTubeSegments() > 0) requested = Math.min(requested, arc.maxTubeSegments());
        return Math.clamp(requested, 3, 64);
    }

    /** Counts only runs containing at least two points; holes must never be bridged by an index. */
    public static Size measure(ArcCurve arc, int requested) {
        int rings = resolution(arc, requested), vertices = 0, indices = 0, from = 0;
        for (int to = 1; to <= arc.size(); to++) {
            if (to < arc.size() && arc.segment(to) == arc.segment(to - 1)) continue;
            int length = to - from;
            if (length >= 2) { vertices += length * rings; indices += (length - 1) * rings * 6; }
            from = to;
        }
        return new Size(vertices, indices);
    }

    /** Append without allocating per curve/ring. Buffers remain in write mode. Returns appended vertices. */
    public static int append(ArcCurve arc, int requested, float r, float g, float b, float a,
                             float brightnessScale, ByteBuffer vertices, ByteBuffer indices, int baseVertex) {
        int rings = resolution(arc, requested);
        var profile = PROFILES.computeIfAbsent(rings, count -> {
            var cos = new float[count]; var sin = new float[count];
            for (int i = 0; i < count; i++) {
                float angle = (float) (i * Math.PI * 2.0 / count);
                cos[i] = (float) Math.cos(angle); sin[i] = (float) Math.sin(angle);
            }
            return new Profile(cos, sin);
        });
        int from = 0, vertexOffset = baseVertex;
        for (int to = 1; to <= arc.size(); to++) {
            if (to < arc.size() && arc.segment(to) == arc.segment(to - 1)) continue;
            int length = to - from;
            if (length < 2) { from = to; continue; }
            float rx = 0, ry = 0, rz = 0;
            for (int i = from; i < to; i++) {
                int previous = Math.max(from, i - 1), next = Math.min(to - 1, i + 1);
                float tx = arc.x(next) - arc.x(previous), ty = arc.y(next) - arc.y(previous), tz = arc.z(next) - arc.z(previous);
                float magnitude = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (magnitude < 1e-6f) { tx = 0; ty = 1; tz = 0; }
                else { tx /= magnitude; ty /= magnitude; tz /= magnitude; }
                if (i > from) {
                    float dot = rx * tx + ry * ty + rz * tz;
                    rx -= dot * tx; ry -= dot * ty; rz -= dot * tz;
                }
                magnitude = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (i == from || magnitude < 1e-6f) {
                    if (Math.abs(ty) < 0.9f) { rx = -tz; ry = 0; rz = tx; }
                    else { rx = 0; ry = tz; rz = -ty; }
                    magnitude = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                }
                if (magnitude < 1e-6f) { rx = 1; ry = 0; rz = 0; }
                else { rx /= magnitude; ry /= magnitude; rz /= magnitude; }
                float ux = ty * rz - tz * ry, uy = tz * rx - tx * rz, uz = tx * ry - ty * rx;
                float radius = arc.width(i), v = (float) (i - from) / (length - 1);
                float brightness = brightnessScale == 1f ? 1f : (float) Math.pow(brightnessScale, arc.generation(i));
                for (int j = 0; j < rings; j++) {
                    float nx = rx * profile.cos[j] + ux * profile.sin[j];
                    float ny = ry * profile.cos[j] + uy * profile.sin[j];
                    float nz = rz * profile.cos[j] + uz * profile.sin[j];
                    vertices.putFloat(arc.x(i) + nx * radius).putFloat(arc.y(i) + ny * radius).putFloat(arc.z(i) + nz * radius);
                    vertices.putFloat(nx).putFloat(ny).putFloat(nz);
                    vertices.putFloat((float) j / rings).putFloat(v);
                    vertices.putFloat(r * brightness).putFloat(g * brightness).putFloat(b * brightness).putFloat(a);
                }
            }
            for (int i = from; i < to - 1; i++) {
                int ring0 = vertexOffset + (i - from) * rings, ring1 = ring0 + rings;
                for (int j = 0; j < rings; j++) {
                    int j1 = (j + 1) % rings;
                    indices.putInt(ring0 + j).putInt(ring1 + j).putInt(ring0 + j1);
                    indices.putInt(ring0 + j1).putInt(ring1 + j).putInt(ring1 + j1);
                }
            }
            vertexOffset += length * rings;
            from = to;
        }
        return vertexOffset - baseVertex;
    }

    /** Compatibility/export convenience. Runtime rendering uses append with reusable buffers. */
    public static MeshData build(ArcCurve arc, int segmentRes, float r, float g, float b, float a, float brightnessScale) {
        var size = measure(arc, segmentRes);
        if (size.vertices == 0) return MeshData.EMPTY;
        var vertices = BufferUtils.createByteBuffer(size.vertices * VERTEX_STRIDE);
        var indexBytes = ByteBuffer.allocate(size.indices * 4);
        append(arc, segmentRes, r, g, b, a, brightnessScale, vertices, indexBytes, 0);
        var indices = new int[size.indices];
        indexBytes.flip();
        indexBytes.asIntBuffer().get(indices);
        vertices.flip();
        return new MeshData(vertices, indices, size.vertices, size.indices);
    }

    public record MeshData(ByteBuffer vertexBuffer, int[] indices, int vertexCount, int indexCount) {
        public static final MeshData EMPTY = new MeshData(BufferUtils.createByteBuffer(0), new int[0], 0, 0);
        public int vertexBytes() { return vertexCount * VERTEX_STRIDE; }
    }
}
