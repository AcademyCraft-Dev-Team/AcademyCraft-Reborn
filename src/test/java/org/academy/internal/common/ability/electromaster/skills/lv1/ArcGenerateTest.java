package org.academy.internal.common.ability.electromaster.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArcGenerateTest {
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

    @Test
    void damageUsesReferenceBaseAndSharedPlayerMultiplier() {
        assertEquals(4.0f, ArcGenerate.getDamage(1.0f, 1.0f));
        assertEquals(9.0f, ArcGenerate.getDamage(1.5f, 1.5f));
        assertEquals(0.0f, ArcGenerate.getDamage(-1.0f, 1.0f));
    }

    @Test
    void unreflectedVisualKeepsTheOriginalSinglePathAndBranchData() {
        var start = new Vec3(1, 2, 3);
        var end = new Vec3(4, 6, 8);
        var paths = ArcGenerate.createUnreflectedArcPaths(
                start,
                end,
                101L,
                List.of(new ArcGenerate.BranchSpec(0.4f, new Vector3f(1, 2, 3), 202L))
        );

        assertEquals(1, paths.size());
        assertLine(paths.getFirst(), start, end);
        assertEquals(101L, jaggedSeed(paths.getFirst()));
        assertEquals(1, paths.getFirst().branches().size());
        assertEquals(0.4f, paths.getFirst().branches().getFirst().attachmentProgress());
        assertEquals(202L, jaggedSeed(paths.getFirst().branches().getFirst().child()));
    }

    @Test
    void reflectedVisualTruncatesOutboundAndUsesAFullLengthReturnPath() {
        var start = new Vec3(0, 1, 2);
        var mirrorPoint = new Vec3(0, 1, 7);
        var returnEnd = new Vec3(0, 1, -3);
        var paths = ArcGenerate.createReflectedArcPaths(
                start,
                mirrorPoint,
                returnEnd,
                0.5f,
                101L,
                List.of(
                        new ArcGenerate.BranchSpec(0.25f, new Vector3f(1, 0, 2), 201L),
                        new ArcGenerate.BranchSpec(0.5f, new Vector3f(-1, 1, 2), 202L),
                        new ArcGenerate.BranchSpec(0.75f, new Vector3f(0, -1, 2), 203L)
                )
        );

        assertEquals(2, paths.size());
        var outbound = paths.get(0);
        var returning = paths.get(1);
        assertLine(outbound, start, mirrorPoint);
        assertLine(returning, mirrorPoint, returnEnd);

        assertEquals(List.of(0.5f, 1.0f), outbound.branches().stream()
                .map(branch -> branch.attachmentProgress()).toList());
        assertEquals(List.of(0.25f, 0.0f), returning.branches().stream()
                .map(branch -> branch.attachmentProgress()).toList());
        assertEquals(List.of(201L, 202L), outbound.branches().stream()
                .map(branch -> jaggedSeed(branch.child())).toList());
        assertEquals(List.of(
                ArcGenerate.deriveReturnSeed(201L),
                ArcGenerate.deriveReturnSeed(202L)
        ), returning.branches().stream().map(branch -> jaggedSeed(branch.child())).toList());
        assertVector(
                new Vec3(0.5, 0.0, 1.0),
                assertInstanceOf(LinePath.class, outbound.branches().getFirst().child().path()).end()
        );
        assertVector(
                new Vec3(-0.5, 0.5, 1.0),
                assertInstanceOf(LinePath.class, returning.branches().get(1).child().path()).end()
        );

        assertEquals(101L, jaggedSeed(outbound));
        assertEquals(ArcGenerate.deriveReturnSeed(101L), jaggedSeed(returning));
        assertNotEquals(101L, ArcGenerate.deriveReturnSeed(101L));
    }
}
