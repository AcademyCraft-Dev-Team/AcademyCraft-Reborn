package org.academy.api.client.render.vfxgraph.arc;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * 曲线→管网格构建器（M22-Rev2）：复刻 Blender Curve to Mesh + Curve Circle 节点。
 *
 * <p>对 ArcCurve 的每个控制点生成 circle profile 顶点，通过 parallel transport 构建正交基，
 * 连接相邻 ring 形成 tube mesh。Blender 对应：Curve to Mesh(Curve Circle, r=0.01)。</p>
 *
 * <p>输出顶点格式：Position(3) + Normal(3) + UV(2) + Color(4) = 12 floats/vertex。</p>
 */
public final class CurveToMeshBuilder {
    public static final int FLOATS_PER_VERTEX = 12; // Position(3) + Normal(3) + UV(2) + Color(4)
    public static final int VERTEX_STRIDE = FLOATS_PER_VERTEX * 4; // bytes

    private CurveToMeshBuilder() {
    }

    /**
     * 构建 ArcCurve 的管网格。
     *
     * @param arc             源弧线数据
     * @param segmentRes      圆周分段数（默认 8，Blender Curve Circle Resolution=8）
     * @param r,g,b,a         基础颜色
     * @param brightnessScale generation 亮度衰减因子
     * @return 管网格数据（顶点 ByteBuffer + 索引 int[]）
     */
    public static MeshData build(ArcCurve arc, int segmentRes, float r, float g, float b, float a,
                                 float brightnessScale) {
        int n = arc.size();
        if (n < 2) return MeshData.EMPTY;

        // 按连续折线段（segment）分组：分支等互不相连的段各自成 run，避免被缝成一根管。
        // 主弧默认 segment 0；CurveGenerator 为每根分支分配独立 id。
        var runs = new ArrayList<int[]>();
        int runStart = 0;
        for (int i = 1; i <= n; i++) {
            if (i == n || arc.segment(i) != arc.segment(i - 1)) {
                runs.add(new int[]{runStart, i});
                runStart = i;
            }
        }

        // 汇总各 run 顶点/索引数
        int vertsPerRing = segmentRes;
        int totalVerts = 0;
        int totalIndices = 0;
        for (var run : runs) {
            int len = run[1] - run[0];
            if (len < 2) continue;
            totalVerts += len * vertsPerRing;
            totalIndices += (len - 1) * segmentRes * 6; // 2 triangles per segment pair
        }
        if (totalVerts == 0) return MeshData.EMPTY;

        var vertBuf = BufferUtils.createByteBuffer(totalVerts * VERTEX_STRIDE);
        var indices = new int[totalIndices];

        // Precompute ring angles
        float[] ringCos = new float[segmentRes];
        float[] ringSin = new float[segmentRes];
        for (int i = 0; i < segmentRes; i++) {
            float angle = (float) (i * Math.PI * 2.0 / segmentRes);
            ringCos[i] = (float) Math.cos(angle);
            ringSin[i] = (float) Math.sin(angle);
        }

        // 逐 run 建管（parallel transport 在每个 run 重新初始化，防 run 间串扰）
        int vertexOffset = 0;
        int idx = 0;
        for (var run : runs) {
            int from = run[0];
            int to = run[1];
            int len = to - from;
            if (len < 2) continue;

            float[] prevRight = null;
            for (int i = from; i < to; i++) {
                int li = i - from; // local index within run
                // Tangent (center difference)
                int prev = Math.max(from, i - 1);
                int next = Math.min(to - 1, i + 1);
                float tx = arc.x(next) - arc.x(prev);
                float ty = arc.y(next) - arc.y(prev);
                float tz = arc.z(next) - arc.z(prev);
                float tlen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tlen < 1e-6f) {
                    tx = 0;
                    ty = 1;
                    tz = 0;
                } else {
                    tx /= tlen;
                    ty /= tlen;
                    tz /= tlen;
                }

                // Right vector (parallel transport); reset per run
                float[] right;
                if (prevRight == null) {
                    right = initialRight(tx, ty, tz);
                } else {
                    right = parallelTransport(prevRight, tx, ty, tz);
                }

                // Up = tangent × right
                float ux = ty * right[2] - tz * right[1];
                float uy = tz * right[0] - tx * right[2];
                float uz = tx * right[1] - ty * right[0];

                float radius = arc.width(i);
                float v = (float) li / (len - 1); // UV.y = progress along run

                // generation-based brightness attenuation
                float gen = arc.generation(i);
                float brightness = (float) Math.pow(brightnessScale, gen);
                float cr = r * brightness;
                float cg = g * brightness;
                float cb = b * brightness;

                // Ring vertices
                for (int j = 0; j < segmentRes; j++) {
                    float nx = right[0] * ringCos[j] + ux * ringSin[j];
                    float ny = right[1] * ringCos[j] + uy * ringSin[j];
                    float nz = right[2] * ringCos[j] + uz * ringSin[j];

                    float px = arc.x(i) + nx * radius;
                    float py = arc.y(i) + ny * radius;
                    float pz = arc.z(i) + nz * radius;

                    float u = (float) j / segmentRes; // UV.x = around tube

                    // Position
                    vertBuf.putFloat(px);
                    vertBuf.putFloat(py);
                    vertBuf.putFloat(pz);
                    // Normal
                    vertBuf.putFloat(nx);
                    vertBuf.putFloat(ny);
                    vertBuf.putFloat(nz);
                    // UV
                    vertBuf.putFloat(u);
                    vertBuf.putFloat(v);
                    // Color
                    vertBuf.putFloat(cr);
                    vertBuf.putFloat(cg);
                    vertBuf.putFloat(cb);
                    vertBuf.putFloat(a);
                }

                prevRight = right;
            }

            // Build index buffer (triangle strip per segment pair)
            for (int i = from; i < to - 1; i++) {
                int ring0 = vertexOffset + (i - from) * segmentRes;
                int ring1 = vertexOffset + (i - from + 1) * segmentRes;
                for (int j = 0; j < segmentRes; j++) {
                    int j1 = (j + 1) % segmentRes;
                    // Triangle 1
                    indices[idx++] = ring0 + j;
                    indices[idx++] = ring1 + j;
                    indices[idx++] = ring0 + j1;
                    // Triangle 2
                    indices[idx++] = ring0 + j1;
                    indices[idx++] = ring1 + j;
                    indices[idx++] = ring1 + j1;
                }
            }
            vertexOffset += len * vertsPerRing;
        }

        vertBuf.flip();
        return new MeshData(vertBuf, indices, totalVerts, totalIndices);
    }

    /**
     * 与切线方向垂直的初始 right 向量。
     */
    private static float[] initialRight(float tx, float ty, float tz) {
        float[] ref = Math.abs(ty) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float rx = ty * ref[2] - tz * ref[1];
        float ry = tz * ref[0] - tx * ref[2];
        float rz = tx * ref[1] - ty * ref[0];
        float len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1e-6f) return new float[]{1, 0, 0};
        return new float[]{rx / len, ry / len, rz / len};
    }

    /**
     * Parallel transport：把 prevRight 投影到新切线的垂直平面。
     */
    private static float[] parallelTransport(float[] prevRight, float tx, float ty, float tz) {
        // dot = prevRight · tangent
        float dot = prevRight[0] * tx + prevRight[1] * ty + prevRight[2] * tz;
        // projected = prevRight - dot * tangent
        float rx = prevRight[0] - dot * tx;
        float ry = prevRight[1] - dot * ty;
        float rz = prevRight[2] - dot * tz;
        float len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1e-6f) return initialRight(tx, ty, tz);
        return new float[]{rx / len, ry / len, rz / len};
    }

    /**
     * 管网格数据。
     */
    public record MeshData(ByteBuffer vertexBuffer, int[] indices, int vertexCount, int indexCount) {
        public static final MeshData EMPTY = new MeshData(BufferUtils.createByteBuffer(0), new int[0], 0, 0);

        public int vertexBytes() {
            return vertexCount * VERTEX_STRIDE;
        }
    }
}
