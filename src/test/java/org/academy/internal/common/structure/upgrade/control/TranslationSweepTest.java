package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationSweepTest {
    private static final AABB UNIT = new AABB(0, 0, 0, 1, 1, 1);

    @Test
    void continuousSweepFindsThinWallEvenWhenBothEndpointsAreClear() {
        var moving = TranslationSweep.Box.axisAligned(UNIT);
        var wall = TranslationSweep.Box.axisAligned(new AABB(2.333, -1, -1, 2.334, 2, 2));
        var hit = TranslationSweep.firstHit(moving, new Vec3(10, 0, 0), wall).orElseThrow();

        assertEquals(0.1333, hit.timeOfImpact(), 1.0e-12);
        assertEquals(new Vec3(-1, 0, 0), hit.normal());
        assertFalse(hit.initialOverlap());
        assertTrue(TranslationSweep.firstHit(moving, Vec3.ZERO, wall).isEmpty());
        assertTrue(TranslationSweep.firstHit(moving.translated(new Vec3(10, 0, 0)), Vec3.ZERO, wall).isEmpty());
    }

    @Test
    void restingContactAllowsSlidingAndSeparationButBlocksInwardMotion() {
        var moving = TranslationSweep.Box.axisAligned(UNIT);
        var floor = TranslationSweep.Box.axisAligned(new AABB(-2, -1, -2, 3, 0, 3));

        assertTrue(TranslationSweep.firstHit(moving, new Vec3(1, 0, 0), floor).isEmpty());
        assertTrue(TranslationSweep.firstHit(moving, new Vec3(0, 1, 0), floor).isEmpty());
        var inward = TranslationSweep.firstHit(moving, new Vec3(0, -1, 0), floor).orElseThrow();
        assertEquals(0.0, inward.timeOfImpact());
        assertFalse(inward.initialOverlap());
        assertEquals(new Vec3(0, 1, 0), inward.normal());
    }

    @Test
    void endingOnFaceIsAllowedButMovingBeyondItIsBlocked() {
        var moving = TranslationSweep.Box.axisAligned(UNIT);
        var wall = TranslationSweep.Box.axisAligned(new AABB(2, 0, 0, 3, 1, 1));

        assertTrue(TranslationSweep.firstHit(moving, new Vec3(1, 0, 0), wall).isEmpty());
        assertTrue(TranslationSweep.firstHit(moving, new Vec3(1.00001, 0, 0), wall).isPresent());
    }

    @Test
    void initialPenetrationIsReportedEvenWhenRequestedMotionWouldLeaveIt() {
        var moving = TranslationSweep.Box.axisAligned(UNIT);
        var wall = TranslationSweep.Box.axisAligned(new AABB(0.9, 0, 0, 2, 1, 1));

        var hit = TranslationSweep.firstHit(moving, new Vec3(-1, 0, 0), wall).orElseThrow();
        assertTrue(hit.initialOverlap());
        assertEquals(0.0, hit.timeOfImpact());
        assertTrue(TranslationSweep.firstHit(moving, Vec3.ZERO, wall).orElseThrow().initialOverlap());
    }

    @Test
    void rotatedRodDoesNotCollideWithAnEmptyCornerOfItsOuterAabb() {
        var pose = new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 4.0));
        var rod = TranslationSweep.Box.of(new AABB(-2, 0, -0.1, 2, 0.2, 0.1), pose);
        var corner = TranslationSweep.Box.axisAligned(new AABB(1.2, 0.5, 1.2, 1.3, 0.6, 1.3));

        assertTrue(rod.sweptBounds(new Vec3(0, 3, 0)).intersects(corner.bounds()));
        assertTrue(TranslationSweep.firstHit(rod, new Vec3(0, 3, 0), corner).isEmpty());
        var realSurface = TranslationSweep.Box.axisAligned(new AABB(1.2, 0.5, -1.3, 1.3, 0.6, -1.2));
        assertTrue(TranslationSweep.firstHit(rod, new Vec3(0, 3, 0), realSurface).isPresent());
    }

    @Test
    void rotatedObstacleAndNegativeWorldCoordinatesRemainSupported() {
        var shift = new Vec3(-1_000, -20, -2_000);
        var moving = TranslationSweep.Box.axisAligned(UNIT).translated(shift);
        var obstacle = TranslationSweep.Box.of(new AABB(-1, -0.1, -1, 1, 0.1, 1),
                new StructurePose(shift.add(0.5, 3, 0.5), new Quaterniond().rotationZ(0.2)));

        assertTrue(TranslationSweep.firstHit(moving, new Vec3(0, 5, 0), obstacle).isPresent());
    }

    @Test
    void subnormalNearParallelCrossAxisDoesNotTurnOverlapIntoFalseClearance() {
        var moving = TranslationSweep.Box.of(UNIT,
                new StructurePose(Vec3.ZERO, 0, Double.MIN_VALUE, 0, 1));
        var samePlace = TranslationSweep.Box.axisAligned(UNIT);
        assertTrue(TranslationSweep.firstHit(moving, Vec3.ZERO, samePlace).orElseThrow().initialOverlap());
    }

    @Test
    void emptyAndNonfiniteGeometryCannotBecomeClearanceCertificates() {
        assertThrows(IllegalArgumentException.class, () -> TranslationSweep.Box.axisAligned(new AABB(0, 0, 0, 0, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> TranslationSweep.Box.axisAligned(new AABB(0, 0, 0, Double.NaN, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> TranslationSweep.firstHit(
                TranslationSweep.Box.axisAligned(UNIT), new Vec3(Double.POSITIVE_INFINITY, 0, 0),
                TranslationSweep.Box.axisAligned(UNIT)));
    }
}
