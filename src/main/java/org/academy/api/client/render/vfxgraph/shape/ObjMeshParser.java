package org.academy.api.client.render.vfxgraph.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简 OBJ 解析器（A3）：读取 {@code v}/{@code f} 记录，产出三角化顶点数组。
 *
 * <p>仅支持纯几何子集：{@code v x y z}（顶点）与 {@code f a[ b c ...]}（面，多边形按扇形
 * 三角化）。索引 1 基，支持 {@code f 1/1 2/2 3/3} 形态（取斜线前索引）与负索引（相对当前）。
 * 忽略其余记录（vn/vt/o/g/注释等）。纯函数、无渲染依赖，可 headless 单测。</p>
 */
public final class ObjMeshParser {
    private ObjMeshParser() {
    }

    /**
     * 解析 OBJ 文本为三角形顶点数组（每三角形 9 个 float：xyz*3）。
     *
     * @throws IllegalArgumentException 顶点数 &lt; 3、面索引越界或格式非法时
     */
    public static float[] parse(String obj) {
        var vertices = new ArrayList<float[]>();
        var triangles = new ArrayList<float[]>();
        var lines = obj.split("\r?\n");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            var line = lines[lineIndex].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var parts = line.split("\\s+");
            switch (parts[0]) {
                case "v" -> vertices.add(parseVertex(parts, lineIndex));
                case "f" -> appendFace(vertices, parts, triangles, lineIndex);
                default -> {
                    // vn/vt/o/g/mtllib 等记录忽略
                }
            }
        }
        if (triangles.isEmpty()) {
            throw new IllegalArgumentException("obj has no faces");
        }
        var out = new float[triangles.size() * 9];
        for (int i = 0; i < triangles.size(); i++) {
            System.arraycopy(triangles.get(i), 0, out, i * 9, 9);
        }
        return out;
    }

    private static float[] parseVertex(String[] parts, int lineIndex) {
        if (parts.length < 4) {
            throw new IllegalArgumentException("obj line " + (lineIndex + 1) + ": malformed v record");
        }
        return new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])};
    }

    private static void appendFace(List<float[]> vertices, String[] parts, List<float[]> triangles, int lineIndex) {
        if (parts.length < 4) {
            throw new IllegalArgumentException("obj line " + (lineIndex + 1) + ": face needs >= 3 indices");
        }
        var count = vertices.size();
        var idx = new int[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            var raw = parts[i].indexOf('/') >= 0 ? parts[i].substring(0, parts[i].indexOf('/')) : parts[i];
            var parsed = Integer.parseInt(raw);
            int v = parsed > 0 ? parsed - 1 : count + parsed;
            if (v < 0 || v >= count) {
                throw new IllegalArgumentException("obj line " + (lineIndex + 1) + ": vertex index out of range: " + parts[i]);
            }
            idx[i - 1] = v;
        }
        for (int i = 1; i + 1 < idx.length; i++) {
            var a = vertices.get(idx[0]);
            var b = vertices.get(idx[i]);
            var c = vertices.get(idx[i + 1]);
            triangles.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]});
        }
    }
}
