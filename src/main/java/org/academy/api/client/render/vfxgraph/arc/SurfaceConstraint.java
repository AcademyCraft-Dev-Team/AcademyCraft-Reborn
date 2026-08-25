package org.academy.api.client.render.vfxgraph.arc;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 表面约束器（M22-Rev2 / M29）：复刻 Blender「闪电附着」的 Sample Nearest Surface。
 *
 * <p>参考几何节点树：{@code Set Position(Position=Sample Nearest Surface.Value,
 * Selection=Endpoint Selection)}——**只把电弧两端点（Endpoint）吸附回目标网格最近表面点**，
 * 中间控制点保留起拱/噪声形态（电弧主体漂浮/拱起，只有两端贴表面，形成「附着」观感）。</p>
 *
 * <p>两种用法：</p>
 * <ol>
 *   <li>固定表面：{@link #SurfaceConstraint(SurfaceDistributor)}——对同一表面反复约束
 *       （旧 {@link ArcSimulator} 路径）；</li>
 *   <li>逐弧表面：无参构造，{@link #constrain(ArcCurve)} 读 {@code arc.surface()}
 *       （每弧可挂不同表面，容器执行器 M29 路径），按数组身份缓存 {@link SurfaceDistributor}。</li>
 * </ol>
 */
public final class SurfaceConstraint {
    private final SurfaceDistributor fixed;
    private final Map<float[], SurfaceDistributor> cache = new IdentityHashMap<>();

    /** 逐弧表面：约束目标取自 {@link ArcCurve#surface()}。 */
    public SurfaceConstraint() {
        this.fixed = null;
    }

    /** 固定表面：对构造时给定的表面约束（旧 {@link ArcSimulator} 路径）。 */
    public SurfaceConstraint(SurfaceDistributor distributor) {
        this.fixed = distributor;
    }

    /**
     * 把 ArcCurve 的**两端点**（每段连续折线的首尾）投影到最近表面点。
     *
     * @param arc 目标弧线（就地修改端点位置）
     */
    public void constrain(ArcCurve arc) {
        var distributor = distributorFor(arc);
        if (distributor == null) return;
        constrain(arc, distributor);
    }

    private SurfaceDistributor distributorFor(ArcCurve arc) {
        if (fixed != null) return fixed;
        float[] surface = arc.surface();
        if (surface == null || surface.length == 0) return null;
        return cache.computeIfAbsent(surface, SurfaceDistributor::new);
    }

    private void constrain(ArcCurve arc, SurfaceDistributor distributor) {
        float[] tris = distributor.triangles();

        if (arc.size() < 2) return;

        // 只约束每段（segment）连续折线的两个端点（参考 Endpoint Selection）
        // pinStart（接触弧）：起点固定于表面点，不参与吸附（Blender End Size=1 仅末端）
        int runStart = 0;
        for (int i = 1; i <= arc.size(); i++) {
            if (i == arc.size() || arc.segment(i) != arc.segment(i - 1)) {
                if (!arc.pinStart()) {
                    constrainPoint(arc, runStart, tris);
                }
                constrainPoint(arc, i - 1, tris);
                runStart = i;
            }
        }
    }

    /**
     * 把端点投影到网格最近表面点（复刻 Blender Sample Nearest Surface + Endpoint Selection）：
     * 对每个三角形求最近点（Closest Point on Triangle），取全局最近者，就地改写端点。
     */
    private void constrainPoint(ArcCurve arc, int i, float[] tris) {
        float px = arc.x(i);
        float py = arc.y(i);
        float pz = arc.z(i);

        float[] nearest = MeshDistance.nearestPoint(tris, px, py, pz);
        if (nearest[0] != px || nearest[1] != py || nearest[2] != pz) {
            arc.setPoint(i, nearest[0], nearest[1], nearest[2]);
        }
    }
}