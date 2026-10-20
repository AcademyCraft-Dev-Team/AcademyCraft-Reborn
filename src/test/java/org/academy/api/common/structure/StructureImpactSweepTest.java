package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureImpactSweepTest {
    @Test
    void fastThrowHitsAnEntityBetweenPublishedPoses() {
        var boxes = List.of(new AABB(0, 0, 0, 1, 1, 1));
        assertTrue(StructureImpactSweep.intersects(boxes, StructurePose.identity(Vec3.ZERO),
                StructurePose.identity(new Vec3(6, 0, 0)), new AABB(3, 0.2, 0.2, 3.2, 1.8, 0.8)));
    }

    @Test
    void gapBetweenCellsDoesNotDamageUnrelatedEntities() {
        var boxes = List.of(new AABB(0, 0, 0, 1, 1, 1), new AABB(0, 0, 3, 1, 1, 4));
        assertFalse(StructureImpactSweep.intersects(boxes, StructurePose.identity(Vec3.ZERO),
                StructurePose.identity(new Vec3(6, 0, 0)), new AABB(3, 0.2, 1.2, 3.2, 1.8, 2.8)));
    }

    @Test
    void rotationUsesOrientedCellsRatherThanTheirBroadPhaseBox() {
        var pose = StructurePose.fromYawAroundPivot(Vec3.ZERO, 45, Vec3.ZERO);
        var boxes = List.of(new AABB(0, 0, 0, 4, 1, 0.1));
        assertFalse(StructureImpactSweep.intersects(boxes, pose, pose,
                new AABB(0.1, 0.1, 2.4, 0.3, 0.9, 2.7)));
        assertTrue(StructureImpactSweep.intersects(boxes, pose, pose,
                new AABB(1.3, 0.1, 1.3, 1.5, 0.9, 1.5)));
    }

    @Test
    void rotationSweepSamplesTheArcEvenWithoutOriginTranslation() {
        var boxes = List.of(new AABB(0, 0, 0, 4, 1, 0.05));
        double angle = Math.toRadians(15);
        var target = AABB.ofSize(new Vec3(Math.cos(angle) * 3, 0.5, Math.sin(angle) * 3), 0.08, 0.8, 0.08);
        assertTrue(StructureImpactSweep.intersects(boxes, StructurePose.identity(Vec3.ZERO),
                StructurePose.fromYawAroundPivot(Vec3.ZERO, 90, Vec3.ZERO), target));
    }

    @Test
    void halfTurnKeepsArcCandidatesOutsideTheEndpointUnion() {
        var box = new AABB(0, 0, 0, 4, 1, 0.05);
        var boxes = List.of(box);
        var previous = StructurePose.identity(Vec3.ZERO);
        var current = StructurePose.fromYawAroundPivot(Vec3.ZERO, 180, Vec3.ZERO);
        var target = AABB.ofSize(new Vec3(0, 0.5, 3), 0.2, 0.8, 0.2);
        var endpoints = BlockStructureOrientedBox.transform(box, previous).bounds()
                .minmax(BlockStructureOrientedBox.transform(box, current).bounds());
        assertFalse(endpoints.intersects(target));
        assertTrue(StructureImpactSweep.sweptBounds(boxes, previous, current, box.getCenter()).intersects(target));
        assertTrue(StructureImpactSweep.intersects(boxes, previous, current, target));
    }

    @Test
    void halfTurnBoundsIncludeTheArcAroundAnOffsetCenterOfMass() {
        var box = new AABB(10, 2, -3, 18, 3, -2.95);
        var boxes = List.of(box);
        var center = new Vec3(11, 2.5, -2.975);
        var origin = new Vec3(30, 40, 50);
        var previous = StructurePose.identity(origin);
        var current = StructurePose.fromYawAroundPivot(origin, 180, center);
        var midpoint = StructurePose.fromYawAroundPivot(origin, 90, center);
        var target = AABB.ofSize(midpoint.localToWorldPoint(new Vec3(17, 2.5, -2.975)), 0.2, 0.8, 0.2);
        var endpoints = BlockStructureOrientedBox.transform(box, previous).bounds()
                .minmax(BlockStructureOrientedBox.transform(box, current).bounds());
        assertFalse(endpoints.intersects(target));
        assertTrue(StructureImpactSweep.sweptBounds(boxes, previous, current, center).intersects(target));
    }
}
