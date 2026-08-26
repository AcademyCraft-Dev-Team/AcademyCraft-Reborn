package org.academy.api.client.render.vfxgraph.shape;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 网格三角形资产注册表（A3 / M29）：id → 三角化顶点数组。
 *
 * <p>供 {@code vfxgraph/nodes/VfxBlocks#buildShape} 的 {@code shape=mesh} 分支按 {@code mesh}
 * 属性查找；未注册/缺省时回退单位立方体。运行时可经 OBJ 文本解析后注册
 * （{@link ObjMeshParser#parse}），无渲染依赖、headless 可测。</p>
 *
 * <p>内置表面生成器（M29，Blender「闪电附着」场景复刻）：{@link #plane}（2×2 地面，复刻
 * Blender Plane）与 {@link #sphere}（UV 球，复刻悬浮 Sphere），经 {@link #resolve} 按
 * {@code builtin:plane}/{@code builtin:sphere} 查询，无需外部文件。</p>
 */
public final class MeshAssets {
    private static final Map<String, float[]> TRIANGLES = new HashMap<>();

    private MeshAssets() {
    }

    /**
     * 注册（或覆盖）一个网格 id → 三角化顶点数组。
     */
    public static void register(String id, float[] triangles) {
        TRIANGLES.put(id, triangles);
    }

    /**
     * 查询网格三角形；未注册返回 null。
     */
    public static @Nullable float[] triangles(String id) {
        return TRIANGLES.get(id);
    }

    /**
     * 清空注册表（测试/重载用）。
     */
    public static void clear() {
        TRIANGLES.clear();
    }

    /**
     * 查询表面三角形：先查注册表（外部 OBJ 注册），再查内置生成器
     * （{@code builtin:plane}/{@code builtin:sphere}）；均无返回 null。
     */
    public static @Nullable float[] resolve(String id) {
        if (id == null || id.isEmpty()) return null;
        var registered = TRIANGLES.get(id);
        if (registered != null) return registered;
        return switch (id) {
            case "builtin:plane" -> plane(2f);
            case "builtin:sphere" -> sphere(1f, 12);
            default -> null;
        };
    }

    /**
     * 水平平面（复刻 Blender Plane：x/z ∈ [-size/2, size/2]，y=0，法线 +Y）。
     *
     * @param size 边长
     * @return 2 个三角形（每三角形 9 个 float：xyz*3）
     */
    public static float[] plane(float size) {
        float h = size * 0.5f;
        // 顶点顺序保证法线朝 +Y（A=(-h,0,-h), B=(-h,0,h), C=(h,0,-h) 的 AB×AC 指向 +Y）
        return new float[]{
                -h, 0, -h, -h, 0, h, h, 0, -h,
                h, 0, h, h, 0, -h, -h, 0, h
        };
    }

    /**
     * UV 球（复刻 Blender Sphere：半径 radius，segment 经线 × segment/2 纬线，法线向外）。
     *
     * @param radius   球半径
     * @param segments 经线分段数（≥4；纬线数为一半）
     * @return 三角形数组（每三角形 9 个 float：xyz*3）
     */
    public static float[] sphere(float radius, int segments) {
        int seg = Math.max(4, segments);
        int rings = Math.max(3, seg / 2);
        var out = new ArrayList<Float>();

        float[] top = {0, radius, 0};
        float[] bottom = {0, -radius, 0};
        for (int s = 0; s < seg; s++) {
            float a0 = (float) (s * Math.PI * 2 / seg);
            float a1 = (float) ((s + 1) * Math.PI * 2 / seg);
            // 顶盖（r=0 极点 → r=1）
            float[] t0 = spherePoint(a0, 1, rings, radius);
            float[] t1 = spherePoint(a1, 1, rings, radius);
            appendTri(out, top, t1, t0);
            // 底盖（r=rings-1 → r=rings 极点）
            float[] b0 = spherePoint(a0, rings - 1, rings, radius);
            float[] b1 = spherePoint(a1, rings - 1, rings, radius);
            appendTri(out, bottom, b0, b1);
            // 中间环带（r=1..rings-1）
            for (int r = 1; r < rings - 1; r++) {
                float[] a = spherePoint(a0, r, rings, radius);
                float[] b = spherePoint(a1, r, rings, radius);
                float[] c = spherePoint(a0, r + 1, rings, radius);
                float[] d = spherePoint(a1, r + 1, rings, radius);
                appendTri(out, a, b, d);
                appendTri(out, a, d, c);
            }
        }
        var arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    /**
     * 球面上一点：经线角 az、纬线 index r（0=顶，rings=底）。
     */
    private static float[] spherePoint(float az, int r, int rings, float radius) {
        float phi = (float) (Math.PI * r / rings);
        return new float[]{
                radius * (float) Math.sin(phi) * (float) Math.cos(az),
                radius * (float) Math.cos(phi),
                radius * (float) Math.sin(phi) * (float) Math.sin(az)
        };
    }

    private static void appendTri(ArrayList<Float> out, float[] a, float[] b, float[] c) {
        out.add(a[0]);
        out.add(a[1]);
        out.add(a[2]);
        out.add(b[0]);
        out.add(b[1]);
        out.add(b[2]);
        out.add(c[0]);
        out.add(c[1]);
        out.add(c[2]);
    }
}
