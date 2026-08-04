package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThunderLanceTest {
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
    void unreflectedVisualKeepsFourFullLengthStrands() {
        var hand = new Vec3(1, 2, 3);
        var target = new Vec3(1, 2, 11);
        var offsets = strandOffsets();
        var seeds = List.of(11L, 12L, 13L, 14L);

        var paths = ThunderLance.createUnreflectedQuickArcPaths(hand, target, offsets, seeds);

        assertEquals(4, paths.size());
        for (var i = 0; i < paths.size(); i++) {
            assertLine(paths.get(i), hand, target.add(offsets.get(i)));
            assertEquals(seeds.get(i).longValue(), jaggedSeed(paths.get(i)));
        }
    }

    @Test
    void reflectedVisualUsesFourClippedOutboundAndFourFullLengthReturnStrands() {
        var hand = new Vec3(1, 2, 3);
        var target = new Vec3(1, 2, 11);
        var mirrorPoint = hand.lerp(target, 0.25);
        var returnEnd = mirrorPoint.subtract(target.subtract(hand));
        var offsets = strandOffsets();
        var seeds = List.of(11L, 12L, 13L, 14L);

        var paths = ThunderLance.createReflectedQuickArcPaths(
                hand,
                mirrorPoint,
                returnEnd,
                0.25f,
                offsets,
                seeds
        );

        assertEquals(8, paths.size());
        for (var i = 0; i < offsets.size(); i++) {
            var reflectionPoint = hand.lerp(target.add(offsets.get(i)), 0.25);
            var strandReturnEnd = returnEnd.subtract(offsets.get(i).scale(0.75));
            assertLine(paths.get(i), hand, reflectionPoint);
            assertLine(paths.get(i + offsets.size()), reflectionPoint, strandReturnEnd);
            assertEquals(
                    hand.distanceTo(target.add(offsets.get(i))),
                    reflectionPoint.distanceTo(strandReturnEnd),
                    1.0e-9
            );
            assertEquals(seeds.get(i).longValue(), jaggedSeed(paths.get(i)));
            assertEquals(ThunderLance.deriveReturnSeed(seeds.get(i)), jaggedSeed(paths.get(i + offsets.size())));
            assertNotEquals(seeds.get(i).longValue(), ThunderLance.deriveReturnSeed(seeds.get(i)));
        }
    }

    private static List<Vec3> strandOffsets() {
        return List.of(
                new Vec3(0.5, 0.5, 0),
                new Vec3(0.5, -0.5, 0),
                new Vec3(-0.5, 0.5, 0),
                new Vec3(-0.5, -0.5, 0)
        );
    }

    private static void assertLine(ArcPath path, Vec3 expectedStart, Vec3 expectedEnd) {
        var line = assertInstanceOf(LinePath.class, path.path());
        assertVector(expectedStart, line.start());
        assertVector(expectedEnd, line.end());
    }

    private static void assertVector(Vec3 expected, Vector3fc actual) {
        assertEquals(expected.x, actual.x(), 1.0e-6);
        assertEquals(expected.y, actual.y(), 1.0e-6);
        assertEquals(expected.z, actual.z(), 1.0e-6);
    }

    private static long jaggedSeed(ArcPath path) {
        return assertInstanceOf(JaggedModifier.class, path.modifiers().getFirst()).seed();
    }
}
