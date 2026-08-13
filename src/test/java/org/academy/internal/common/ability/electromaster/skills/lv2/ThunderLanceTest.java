package org.academy.internal.common.ability.electromaster.skills.lv2;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.path.PolylinePath;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThunderLanceTest {
    private static void assertVector(Vec3 expected, Vector3fc actual) {
        assertEquals(expected.x, actual.x(), 1.0e-6);
        assertEquals(expected.y, actual.y(), 1.0e-6);
        assertEquals(expected.z, actual.z(), 1.0e-6);
    }

    @Test
    void quickModeUsesReferenceDamageAndCurrentMultipliers() {
        assertEquals(16.0f, ThunderLance.calculateQuickDamage(1.0f, 1.0f));
        assertEquals(36.0f, ThunderLance.calculateQuickDamage(1.5f, 1.5f));
        assertEquals(0.0f, ThunderLance.calculateQuickDamage(-1.0f, 1.0f));
    }

    @Test
    void handPositionUsesAStableFallbackWhenLookingStraightUp() {
        var hand = ThunderLance.calculateHandPosition(Vec3.ZERO, new Vec3(0, 1, 0));

        assertEquals(0.4, hand.x, 1.0e-9);
        assertEquals(1.7, hand.y, 1.0e-9);
        assertEquals(0.0, hand.z, 1.0e-9);
    }

    @Test
    void spearBundleHasAConcentratedCoreAndIrregularStrandsConvergingAtBothEnds() {
        var start = new Vec3(1, 2, 3);
        var end = new Vec3(1, 2, 43);
        var paths = ElectromasterArcEffects.spearBundle(start, end, 7, 0.20f, 91L);

        assertEquals(8, paths.size());
        var core = assertInstanceOf(LinePath.class, paths.getFirst().path());
        assertVector(start, core.start());
        assertVector(end, core.end());
        assertEquals(6, paths.getFirst().branches().size());
        for (var branch : paths.getFirst().branches()) {
            assertTrue(branch.attachmentProgress() >= 0.12f && branch.attachmentProgress() <= 0.90f);
            var branchPath = branch.child();
            var branchLine = assertInstanceOf(LinePath.class, branchPath.path());
            assertEquals(0.0f, branchLine.start().lengthSquared(), 1.0e-6f);
            assertTrue(branchLine.end().length() < 1.0f, "minor branches should remain short");
            assertInstanceOf(JaggedModifier.class, branchPath.modifiers().getFirst());
            assertInstanceOf(TaperModifier.class, branchPath.modifiers().getLast());
        }

        var widestRadius = 0.0;
        var clockwiseTurns = 0;
        var counterclockwiseTurns = 0;
        for (var path : paths.subList(1, paths.size())) {
            var strand = assertInstanceOf(PolylinePath.class, path.path());
            assertVector(start, strand.vertices().getFirst());
            assertVector(end, strand.vertices().getLast());
            assertInstanceOf(JaggedModifier.class, path.modifiers().getFirst());
            assertInstanceOf(TaperModifier.class, path.modifiers().getLast());
            for (var vertex : strand.vertices()) {
                widestRadius = Math.max(widestRadius,
                        Math.sqrt(Math.pow(vertex.x() - start.x, 2) + Math.pow(vertex.y() - start.y, 2)));
            }
            for (var index = 2; index < strand.vertices().size() - 1; index++) {
                var previous = strand.vertices().get(index - 1);
                var current = strand.vertices().get(index);
                var turn = (previous.x() - start.x) * (current.y() - start.y)
                        - (previous.y() - start.y) * (current.x() - start.x);
                if (turn > 1.0e-5) counterclockwiseTurns++;
                if (turn < -1.0e-5) clockwiseTurns++;
            }
        }
        assertTrue(widestRadius > 0.10, "outer arcs should remain visible around the spear core");
        assertTrue(widestRadius <= 0.20, "outer arcs should stay concentrated around the spear core");
        assertTrue(clockwiseTurns > 0 && counterclockwiseTurns > 0,
                "outer arcs should cross irregularly instead of winding like a spring");
    }

    @Test
    void chainBundleUsesSeveralIndependentlyJaggedArcs() {
        var start = new Vec3(1, 2, 3);
        var end = new Vec3(4, 5, 6);
        var paths = ElectromasterArcEffects.chainBundle(start, end, 17L);

        assertEquals(3, paths.size());
        var seeds = paths.stream()
                .map(path -> assertInstanceOf(JaggedModifier.class, path.modifiers().getFirst()).seed())
                .distinct()
                .toList();
        assertEquals(3, seeds.size());
        for (var path : paths) {
            var line = assertInstanceOf(LinePath.class, path.path());
            assertVector(start, line.start());
            assertVector(end, line.end());
        }
    }
}
