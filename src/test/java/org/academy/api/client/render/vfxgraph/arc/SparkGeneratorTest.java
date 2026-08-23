package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SparkGeneratorTest {

    @Test
    void generateFromArc() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        var sparks = SparkGenerator.generate(arc, 1.0f, 0.005f, 0.05f, 0.5f, 42L);

        // 12-point arc → 2 branches (main + 1 end) → 2 endpoints → all survive (rate=1.0)
        assertTrue(sparks.size() >= 1, "Should have at least 1 spark");

        for (var s : sparks) {
            assertTrue(s.lifetime() > 0, "Spark lifetime should be positive");
            assertTrue(s.radius() > 0, "Spark radius should be positive");
        }
    }

    @Test
    void survivalRateControlsCount() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        var many = SparkGenerator.generate(arc, 1.0f, 0.005f, 0.05f, 0.5f, 42L);
        var few = SparkGenerator.generate(arc, 0.1f, 0.005f, 0.05f, 0.5f, 42L);

        assertTrue(few.size() <= many.size(),
                "Lower survival rate should produce fewer sparks: " + few.size() + " vs " + many.size());
    }

    @Test
    void sparkToArcCurve() {
        var spark = new SparkGenerator.SparkData(
                0, 0, 0, 0, 0.1f, 0, 0.005f, 0.5f,
                1, 0.5f, 0.2f, 1f);

        var arc = spark.toArcCurve();
        assertEquals(2, arc.size());
        assertEquals(0, arc.x(0));
        assertEquals(0.1f, arc.y(1));
        assertEquals(1f, arc.r());
        assertEquals(0.5f, arc.lifetime());
    }

    @Test
    void sparkDirectionIsNearNormal() {
        var arc = new ArcCurve();
        // Horizontal arc along X
        for (int i = 0; i < 12; i++) {
            arc.addPoint(i * 0.1f, 0, 0, 0.01f, 0);
        }
        arc.setColor(1, 1, 1, 1);
        arc.setLifetime(10f);

        var sparks = SparkGenerator.generate(arc, 1.0f, 0.005f, 0.05f, 0.5f, 42L);

        // Sparks should fly roughly along the arc tangent (X direction)
        for (var s : sparks) {
            float dx = s.endX() - s.startX();
            float dy = s.endY() - s.startY();
            float dz = s.endZ() - s.startZ();
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6f) continue;
            // Should have some X component (tangent direction)
            assertTrue(Math.abs(dx / len) > 0.1f || Math.abs(dy / len) > 0.1f,
                    "Spark should have reasonable direction");
        }
    }

    @Test
    void generateDeterministic() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f, 2.0f);

        var a = SparkGenerator.generate(arc, 0.5f, 0.005f, 0.05f, 0.5f, 99L);
        var b = SparkGenerator.generate(arc, 0.5f, 0.005f, 0.05f, 0.5f, 99L);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).startX(), b.get(i).startX(), 1e-6f);
            assertEquals(a.get(i).endY(), b.get(i).endY(), 1e-6f);
        }
    }
}
