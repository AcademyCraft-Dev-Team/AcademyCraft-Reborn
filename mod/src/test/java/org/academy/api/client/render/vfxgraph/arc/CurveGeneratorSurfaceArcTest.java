package org.academy.api.client.render.vfxgraph.arc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M29：表面短弧生成器（Blender Curve Line + Bezier 起拱 + Resample）单测。
 */
class CurveGeneratorSurfaceArcTest {

    @Test
    void generateSurfaceArcFillsSegments() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                0, 0, 0,       // 表面点
                0, 1, 0,       // 法线 +Y
                1.0f, 0.78f, 0.01f, 12,   // height/curve/width/segments
                0.8f, 0.2f, 0.4f, 1f,
                1f, 42L);

        assertEquals(12, arc.size());
        assertEquals(0.8f, arc.r(), 0.01f);
        assertEquals(1f, arc.lifetime());
        assertEquals(42L, arc.seed());
    }

    /**
     * 弧沿切平面展开（平躺表面），法线方向上拱（Blender Align axis=X：local X→法线、local Z 落切平面）。
     */
    @Test
    void generateSurfaceArcLiesOnSurfaceAndArches() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                1, 2, 3, 0, 1, 0,
                1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 1f, 7L);

        // 切平面展开：两端在表面点附近（y=2 平面），span 沿 x/z
        assertEquals(2f, arc.y(0), 1e-3f, "start on surface plane");
        assertEquals(2f, arc.y(arc.size() - 1), 1e-3f, "end on surface plane");
        // 法线上拱：中间 y > 2（帐篷拱）
        var above = false;
        for (var i = 0; i < arc.size(); i++) {
            if (arc.y(i) > 2.01f) {
                above = true;
                break;
            }
        }
        assertTrue(above, "Arc should arch above surface plane (y>2)");
    }

    /**
     * 端点应落在表面点附近（吸附后端点贴面）；基线中心在表面点。
     */
    @Test
    void generateSurfaceArcEndpointsNearSurface() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                0, 5, 0, 0, 1, 0,
                1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 1f, 9L);

        assertEquals(5f, arc.y(0), 1e-3f);
        assertEquals(5f, arc.y(arc.size() - 1), 1e-3f);
        // 基线中心 ≈ 表面点（两端中点）
        var cx = (arc.x(0) + arc.x(arc.size() - 1)) * 0.5f;
        var cz = (arc.z(0) + arc.z(arc.size() - 1)) * 0.5f;
        assertEquals(0f, cx, 1e-3f, "arch center x ≈ surface point");
        assertEquals(0f, cz, 1e-3f, "arch center z ≈ surface point");
    }

    /**
     * 确定性：同种子同参数结果一致。
     */
    @Test
    void generateSurfaceArcDeterministic() {
        var a = new ArcCurve();
        var b = new ArcCurve();
        CurveGenerator.generateSurfaceArc(a, 0, 0, 0, 0, 1, 0, 1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 1f, 42L);
        CurveGenerator.generateSurfaceArc(b, 0, 0, 0, 0, 1, 0, 1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 1f, 42L);
        assertEquals(a.size(), b.size());
        for (var i = 0; i < a.size(); i++) {
            assertEquals(a.x(i), b.x(i), 1e-6f);
            assertEquals(a.y(i), b.y(i), 1e-6f);
            assertEquals(a.z(i), b.z(i), 1e-6f);
        }
    }
}
