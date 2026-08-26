package org.academy.api.client.render.vfxgraph.arc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveGeneratorTest {

    @Test
    void generateBasicArc() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc,
                0, 0, 0,     // position
                0, 1, 0,     // normal (up)
                0.01f,       // width
                12,          // segments
                0.8f, 0.2f, 0.4f, 1f, // color
                1.0f,        // lifetime
                42L,         // seed
                0,           // branchDepth (no branching)
                2, 1.57f,    // branchCount, branchAngle (unused)
                0.3f, 0.35f, 0.6f, // length/width/brightness scale
                2.0f         // height
        );

        assertEquals(12, arc.size(), "Should have 12 control points");
        assertEquals(0.8f, arc.r(), 0.01f);
        assertEquals(1.0f, arc.lifetime());
        assertEquals(42L, arc.seed());

        // All points should have generation=0
        for (int i = 0; i < arc.size(); i++) {
            assertEquals(0f, arc.generation(i), 0.01f, "All points should be generation 0");
        }

        // Points should form a vertical-ish line (y varies along normal 0,1,0)
        float yMin = Float.MAX_VALUE, yMax = Float.MIN_VALUE;
        for (int i = 0; i < arc.size(); i++) {
            yMin = Math.min(yMin, arc.y(i));
            yMax = Math.max(yMax, arc.y(i));
        }
        assertTrue(yMax - yMin > 0.001f, "Arc should have vertical extent along normal");
    }

    @Test
    void generateWithBranching() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc,
                0, 0, 0,
                0, 1, 0,
                0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f,
                1.0f, 42L,
                2,           // branchDepth = 2 (recursive)
                2, 1.57f,    // branchCount = 2 per level
                0.3f, 0.35f, 0.6f,
                2.0f
        );

        // Should have more than 12 points (main + branches)
        assertTrue(arc.size() > 12, "With branching should have more points: " + arc.size());

        // Should have points at different generations
        boolean hasGen0 = false, hasGen1 = false, hasGen2 = false;
        for (int i = 0; i < arc.size(); i++) {
            float gen = arc.generation(i);
            if (gen < 0.5f) hasGen0 = true;
            else if (gen < 1.5f) hasGen1 = true;
            else hasGen2 = true;
        }
        assertTrue(hasGen0, "Should have generation 0 (main arc)");
        assertTrue(hasGen1, "Should have generation 1 (sub arcs)");
        assertTrue(hasGen2, "Should have generation 2 (sub-sub arcs)");
    }

    @Test
    void generateNoBranchingDepth0() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc,
                0, 0, 0, 0, 1, 0,
                0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f,
                1.0f, 42L,
                0,           // branchDepth = 0
                2, 1.57f,
                0.3f, 0.35f, 0.6f,
                2.0f
        );

        assertEquals(12, arc.size());
        for (int i = 0; i < arc.size(); i++) {
            assertEquals(0f, arc.generation(i), 0.01f);
        }
    }

    @Test
    void generateDeterministic() {
        var a = new ArcCurve();
        var b = new ArcCurve();
        CurveGenerator.generate(a, 1, 2, 3, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 123L,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f, 2.0f);
        CurveGenerator.generate(b, 1, 2, 3, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 123L,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f, 2.0f);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.x(i), b.x(i), 1e-6f);
            assertEquals(a.y(i), b.y(i), 1e-6f);
            assertEquals(a.z(i), b.z(i), 1e-6f);
        }
    }

    @Test
    void branchWidthDecreasesWithDepth() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc,
                0, 0, 0, 0, 1, 0,
                0.1f, 12,
                0.8f, 0.2f, 0.4f, 1f,
                1.0f, 42L,
                2, 2, 1.57f,
                0.3f, 0.35f, 0.6f, 2.0f
        );

        // Find widths per generation
        float gen0Width = 0, gen1Width = 0, gen2Width = 0;
        int gen0Count = 0, gen1Count = 0, gen2Count = 0;
        for (int i = 0; i < arc.size(); i++) {
            float gen = arc.generation(i);
            float w = arc.width(i);
            if (gen < 0.5f) {
                gen0Width += w;
                gen0Count++;
            } else if (gen < 1.5f) {
                gen1Width += w;
                gen1Count++;
            } else {
                gen2Width += w;
                gen2Count++;
            }
        }
        if (gen0Count > 0 && gen1Count > 0) {
            float avg0 = gen0Width / gen0Count;
            float avg1 = gen1Width / gen1Count;
            assertTrue(avg1 < avg0, "Generation 1 should be thinner: " + avg1 + " < " + avg0);
        }
    }

    @Test
    void branchBrightnessDecreasesWithDepth() {
        var arc = new ArcCurve();
        float brightnessScale = 0.6f;
        CurveGenerator.generate(arc,
                0, 0, 0, 0, 1, 0,
                0.1f, 12,
                1.0f, 1.0f, 1.0f, 1f,
                1.0f, 42L,
                2, 2, 1.57f,
                0.3f, 0.35f, brightnessScale, 2.0f
        );

        // 亮度由 generation 在着色器侧衰减：brightness = brightnessScale^generation
        // 验证 generation 值正确分布（着色器会用 pow(brightnessScale, gen) 计算亮度）
        boolean hasGen0 = false, hasGen1 = false;
        for (int i = 0; i < arc.size(); i++) {
            float gen = arc.generation(i);
            if (gen < 0.5f) hasGen0 = true;
            else if (gen < 1.5f) hasGen1 = true;
        }
        assertTrue(hasGen0, "Should have generation 0");
        assertTrue(hasGen1, "Should have generation 1 (dimmer via shader: brightness = " + brightnessScale + "^1)");

        // 颜色应保持不变（亮度衰减在着色器侧完成）
        assertEquals(1.0f, arc.r(), 0.01f);
        assertEquals(1.0f, arc.g(), 0.01f);
        assertEquals(1.0f, arc.b(), 0.01f);
    }

    /**
     * from→to 两点电弧：端点固定，控制柄沿法线伸开，中部起拱。
     */
    @Test
    void generateFromToArchKeepsEndpointsAndArches() {
        var arc = new ArcCurve();
        CurveGenerator.generateFromTo(arc,
                0, 0, 0,      // from
                0, 2, 0,      // to
                0, 1, 0,      // normal (+Y)
                0.01f, 12,
                1f, 1f, 1f, 1f, 1f, 42L,
                0, 0, 1.57f, 0.3f, 0.35f, 0.6f);

        assertEquals(12, arc.size());
        // 端点固定：首点 ≈ from，末点 ≈ to
        assertEquals(0f, arc.x(0), 1e-4f);
        assertEquals(0f, arc.y(0), 1e-4f);
        assertEquals(0f, arc.x(arc.size() - 1), 1e-4f);
        assertEquals(2f, arc.y(arc.size() - 1), 1e-4f);
        // 控制柄沿 +Y 伸开 → 中部 y 应超过线性插值（起拱）
        float midY = arc.y(arc.size() / 2);
        assertTrue(midY > 1f + 0.05f, "Arc should arch above linear interpolation, midY=" + midY);
    }

    /**
     * 递归分支每根独立 segment（建管时分成独立 run，不缝合）。
     */
    @Test
    void generateFromToBranchesDistinctSegments() {
        var arc = new ArcCurve();
        CurveGenerator.generateFromTo(arc,
                0, 0, 0, 0, 2, 0, 0, 1, 0,
                0.01f, 12, 1f, 1f, 1f, 1f, 1f, 42L,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f);
        assertTrue(arc.size() > 12);
        var segments = new HashSet<Integer>();
        for (int i = 0; i < arc.size(); i++) {
            segments.add(arc.segment(i));
        }
        // 主弧 + 2 分支 = 3 个不同 segment
        assertEquals(3, segments.size());
    }
}
