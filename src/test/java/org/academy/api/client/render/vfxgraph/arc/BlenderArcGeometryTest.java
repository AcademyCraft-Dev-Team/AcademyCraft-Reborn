package org.academy.api.client.render.vfxgraph.arc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M30：Blender「闪电附着」几何复刻验证——生成的表面弧应与实测 Blender 弧同构：
 * 平躺帐篷拱（两端贴面、中间拱起、水平跨度≈电弧高度、顶点高度随 FloatCurve.001 成长）。
 */
class BlenderArcGeometryTest {

    /**
     * 弧平躺在表面：两端 y=0（平面法线 +Y），切平面（x/z）方向展开。
     */
    @Test
    void surfaceArcLiesFlatOnPlane() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                0, 0, 0, 0, 1, 0,
                BlenderArcReference.ARC_HEIGHT, 0.78f, 0.006f, 12,
                BlenderArcReference.ARC_COLOR_BLUE[0], BlenderArcReference.ARC_COLOR_BLUE[1],
                BlenderArcReference.ARC_COLOR_BLUE[2], 1f,
                BlenderArcReference.ARC_LIFETIME, 42L);

        assertEquals(12, arc.size());
        assertEquals(0f, arc.y(0), 1e-4f, "start endpoint on plane");
        assertEquals(0f, arc.y(arc.size() - 1), 1e-4f, "end endpoint on plane");
        // 基线跨度 = 电弧高度 × 实例随机跨度缩放（M30：Blender Instance Scale = Random[0.4..1.2]×电弧宽度）
        var dx = arc.x(0) - arc.x(arc.size() - 1);
        var dz = arc.z(0) - arc.z(arc.size() - 1);
        var span = (float) Math.sqrt(dx * dx + dz * dz);
        assertTrue(span >= 0.4f, "horizontal span ≥ 0.4×height (instance random scale), got " + span);
        assertTrue(span <= 1.2f, "horizontal span ≤ 1.2×height (instance random scale), got " + span);
    }

    /**
     * 弧拱成长：age=0 近平展（FloatCurve.001(0)=0.112），age=lifetime 满拱。
     */
    @Test
    void surfaceArcArchGrowsWithAge() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                0, 0, 0, 0, 1, 0,
                1.0f, 0.78f, 0.006f, 12,
                1f, 1f, 1f, 1f, BlenderArcReference.ARC_LIFETIME, 42L);

        var apexAge0 = maxY(arc);
        arc.setAge(BlenderArcReference.ARC_LIFETIME);
        CurveGenerator.sampleSurfaceArch(arc);
        var apexFull = maxY(arc);

        assertTrue(apexFull > apexAge0, "arch grows with age: " + apexAge0 + " -> " + apexFull);
        // 满拱顶点高度应接近 0.5×height（实测 Blender apex≈0.51）：growth(1.0)=1.0 × random × height × curve
        // 用容差（random 0.4~1.2 变化）
        assertTrue(apexFull > 0.2f, "full arch apex should be substantial, got " + apexFull);
        assertTrue(apexFull < 1.2f, "full arch apex bounded, got " + apexFull);
    }

    /**
     * 管半径剖面：端粗中细（Blender FloatCurve.002），随 age 衰减（FloatCurve.005）。
     */
    @Test
    void surfaceArcRadiusProfileEndThickMiddleThin() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                0, 0, 0, 0, 1, 0,
                1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 20f, 42L);

        var end = arc.width(0);
        var mid = arc.width(arc.size() / 2);
        assertTrue(end > mid, "ends should be thicker than middle: end=" + end + " mid=" + mid);

        // 满龄半径衰减：出生 radius=width×profile(0.93)，临终 width×profile×0（FloatCurve.005→0）
        var dying = new ArcCurve();
        CurveGenerator.generateSurfaceArc(dying,
                0, 0, 0, 0, 1, 0,
                1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 20f, 42L);
        dying.setAge(20f);
        CurveGenerator.sampleSurfaceArch(dying);
        assertTrue(dying.width(arc.size() / 2) < arc.width(arc.size() / 2),
                "radius shrinks as arc ages");
    }

    /**
     * 表面弧携带表面（端点吸附用）且基线可逐帧重采样。
     */
    @Test
    void surfaceArcCarriesArchBase() {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                1, 2, 3, 0, 1, 0,
                1.0f, 0.78f, 0.01f, 12,
                1f, 1f, 1f, 1f, 20f, 42L);
        assertTrue(arc.hasArchBase());
        assertEquals(1f, arc.archX(), 1e-4f);
        assertEquals(2f, arc.archY(), 1e-4f);
        assertEquals(3f, arc.archZ(), 1e-4f);
        assertEquals(0f, arc.archNx(), 1e-6f);
        assertEquals(1f, arc.archNy(), 1e-6f);
        assertEquals(0f, arc.archNz(), 1e-6f);
    }

    /**
     * 曲线采样：FloatCurve 插值（BlenderArcCurves）。
     */
    @Test
    void curveSampling() {
        assertEquals(0.112f, BlenderArcCurves.sample(BlenderArcCurves.ARCH_GROWTH, 0f), 1e-4f);
        assertEquals(1.0f, BlenderArcCurves.sample(BlenderArcCurves.ARCH_GROWTH, 1f), 1e-4f);
        assertEquals(0.931f, BlenderArcCurves.sample(BlenderArcCurves.RADIUS_PROFILE, 0f), 1e-4f);
        assertEquals(0.475f, BlenderArcCurves.sample(BlenderArcCurves.RADIUS_PROFILE, 0.5f), 1e-3f);
        // 中点线性插值 (0.5 介于 0.186→0.814 段的 0.475 平台附近)
        assertEquals(1.0f, BlenderArcCurves.sample(BlenderArcCurves.RADIUS_AGE, 0f), 1e-4f);
        assertEquals(0.0f, BlenderArcCurves.sample(BlenderArcCurves.RADIUS_AGE, 1f), 1e-4f);
    }

    /**
     * 与 Blender 实测几何对照（frame40）：满龄弧应构成「平躺帐篷拱」——apex ≈ 0.5×height、
     * 水平跨度 ≈ height、管半径端粗中细 ≈ 0.0034/0.0024。因每弧 random 缩放 0.4~1.2 不同，
     * 用统计范围而非精确值断言。
     */
    @Test
    void archMatchesMeasuredBlenderGeometry() {
        var apexOk = false;
        var spanOk = false;
        var radiusOk = false;
        int apexMin = Integer.MAX_VALUE, apexMax = Integer.MIN_VALUE;
        float spanMin = 1e9f, spanMax = -1e9f;
        for (var seed : new long[]{1, 42, 931, 777, 12345, 99991}) {
            var arc = new ArcCurve();
            CurveGenerator.generateSurfaceArc(arc,
                    0, 0, 0, 0, 1, 0,
                    1.0f, 0.78f, 0.0035f, 12,
                    1f, 1f, 1f, 1f, 20f, seed);
            arc.setAge(20f);
            CurveGenerator.sampleSurfaceArch(arc);
            var maxY = maxY(arc);
            var span = span(arc);
            var apexPct = Math.round(maxY * 100f);
            apexMin = Math.min(apexMin, apexPct);
            apexMax = Math.max(apexMax, apexPct);
            spanMin = Math.min(spanMin, span);
            spanMax = Math.max(spanMax, span);
            // Blender 实测：apex≈0.51（0.5×height），span≈0.86（×随机实例缩放 0.4~1.2 变化范围内覆盖）
            if (maxY >= 0.23f && maxY <= 0.95f) apexOk = true;
            if (span >= 0.4f && span <= 1.2f) spanOk = true;

            // 管半径端粗中细：出生弧（age=0，radiusAge=1.0）FloatCurve.002 profile 最明显
            var born = new ArcCurve();
            CurveGenerator.generateSurfaceArc(born,
                    0, 0, 0, 0, 1, 0,
                    1.0f, 0.78f, 0.0035f, 12,
                    1f, 1f, 1f, 1f, 20f, seed);
            if (born.width(0) > born.width(born.size() / 2)) radiusOk = true;
        }
        assertTrue(apexOk, "apex should reach ~0.5×height for some seeds, range " + apexMin + "%~" + apexMax + "%");
        assertTrue(spanOk, "horizontal span within instance random scale [0.4,1.2]×height, observed [" + spanMin + "," + spanMax + "]");
        assertTrue(radiusOk, "tube radius end-thicker-than-middle (FloatCurve.002)");
    }

    private static float span(ArcCurve arc) {
        var dx = arc.x(0) - arc.x(arc.size() - 1);
        var dz = arc.z(0) - arc.z(arc.size() - 1);
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float maxY(ArcCurve arc) {
        var m = -1e9f;
        for (var i = 0; i < arc.size(); i++) {
            m = Math.max(m, arc.y(i));
        }
        return m;
    }
}
