package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureTractionBoundsTest {
    private static final AABB REGION = new AABB(-5, -5, -5, 5, 5, 5);
    private static final AABB BODY = new AABB(-1, -1, -1, 1, 1, 1);

    @Test
    void usesWholeBodyInsteadOfOnlyCheckingTheAnchorTarget() {
        assertTrue(accepts(BODY, Vec3.ZERO, new Vec3(3.75, 0, 0), Vec3.ZERO));
        assertFalse(accepts(BODY, Vec3.ZERO, new Vec3(3.750001, 0, 0), Vec3.ZERO));
        assertFalse(accepts(BODY, Vec3.ZERO, new Vec3(4.5, 0, 0), Vec3.ZERO));
    }

    @Test
    void accountsForAnchorOffsetAndEachSignedAxis() {
        for (var axis : new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)}) {
            for (int sign : new int[]{-1, 1}) {
                var point = axis.scale(sign);
                assertTrue(accepts(BODY, point, point.add(axis.scale(sign * 3.75)), Vec3.ZERO));
                assertFalse(accepts(BODY, point, point.add(axis.scale(sign * 3.76)), Vec3.ZERO));
            }
        }
    }

    @Test
    void targetVelocityPredictionUsesSecondsInsteadOfTicks() {
        var target = new Vec3(3.5, 0, 0);
        assertTrue(accepts(BODY, Vec3.ZERO, target, new Vec3(5, 0, 0)));
        assertFalse(accepts(BODY, Vec3.ZERO, target, new Vec3(5.01, 0, 0)));
        assertTrue(accepts(BODY, Vec3.ZERO, target, new Vec3(-5, 0, 0)));
    }

    @Test
    void rotatingACompoundChangesItsPermittedTranslation() {
        var local = new AABB(-2, -0.5, -0.5, 2, 0.5, 0.5);
        var rotated = BlockStructureOrientedBox.transform(local,
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationZ(Math.PI / 2))).bounds();
        assertTrue(accepts(local, Vec3.ZERO, new Vec3(0, 3, 0), Vec3.ZERO));
        assertFalse(accepts(rotated, Vec3.ZERO, new Vec3(0, 3, 0), Vec3.ZERO));
    }

    @Test
    void rejectsCurrentBodyOutsideMarginEvenWhenRequestedTargetWouldFit() {
        var outside = BODY.move(3.9, 0, 0);
        assertFalse(accepts(outside, new Vec3(3.9, 0, 0), Vec3.ZERO, Vec3.ZERO));
    }

    @Test
    void rejectsImpossibleInsetsAndNonFiniteTargets() {
        assertFalse(NativeStructureTractionBounds.accepts(new AABB(0, 0, 0, 0.4, 2, 2),
                new AABB(0.1, 0.5, 0.5, 0.3, 1.5, 1.5), new Vec3(0.2, 1, 1), new Vec3(0.2, 1, 1), Vec3.ZERO));
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertFalse(accepts(BODY, Vec3.ZERO, new Vec3(invalid, 0, 0), Vec3.ZERO));
            assertFalse(accepts(BODY, Vec3.ZERO, Vec3.ZERO, new Vec3(0, invalid, 0)));
            assertFalse(accepts(BODY, new Vec3(0, 0, invalid), Vec3.ZERO, Vec3.ZERO));
        }
    }

    private static boolean accepts(AABB body, Vec3 point, Vec3 target, Vec3 velocity) {
        return NativeStructureTractionBounds.accepts(REGION, body, point, target, velocity);
    }
}
