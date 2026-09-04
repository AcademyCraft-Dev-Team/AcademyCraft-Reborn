package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.BlockStructureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureSelectionTest {
    @Test
    void radiusTwoSphereContainsThirtyThreeGridPositions() {
        var center = new BlockPos(10, 20, 30);

        var positions = BlockStructureManager.positionsInSphere(center, 2.0);

        assertEquals(33, positions.size());
        assertTrue(positions.contains(center));
        assertTrue(positions.contains(center.offset(2, 0, 0)));
        assertFalse(positions.contains(center.offset(2, 1, 0)));
    }

    @Test
    void inwardShiftedRadiusTwoSphereContainsSurfaceCenteredThreeCube() {
        var support = new BlockPos(10, 20, 30);
        var thrustDirection = Direction.EAST;
        var positions = BlockStructureManager.positionsInSphere(
                support.relative(thrustDirection), 2.0);

        for (var depth = 0; depth < 3; depth++) {
            for (var y = -1; y <= 1; y++) {
                for (var z = -1; z <= 1; z++) {
                    assertTrue(positions.contains(support.offset(depth, y, z)),
                            "Missing 3x3x3 position at depth " + depth
                                    + ", y " + y + ", z " + z);
                }
            }
        }
    }

    @Test
    void ellipsoidUsesIndependentHorizontalAndVerticalRadii() {
        var center = new BlockPos(10, 20, 30);

        var positions = BlockStructureManager.positionsInEllipsoid(
                center, 3.0, 1.0, 3.0);

        assertTrue(positions.contains(center.offset(3, 0, 0)));
        assertTrue(positions.contains(center.offset(0, 1, 0)));
        assertFalse(positions.contains(center.offset(1, 1, 0)));
        assertFalse(positions.contains(center.offset(0, 2, 0)));
        assertFalse(positions.contains(center.offset(3, 0, 1)));
    }

    @Test
    void cubeCorePreservesCornersOutsideFlattenedEllipsoid() {
        var center = new BlockPos(10, 20, 30);

        var positions = BlockStructureManager.positionsInEllipsoidWithCubeCore(
                center, 5.0, 2.0, 5.0, 1);

        assertTrue(positions.contains(center.offset(5, 0, 0)));
        assertTrue(positions.contains(center.offset(0, 2, 0)));
        assertTrue(positions.contains(center.offset(1, 1, 1)));
        assertFalse(positions.contains(center.offset(2, 2, 2)));
        assertFalse(positions.contains(center.offset(0, 3, 0)));
    }

    @Test
    void lowerEllipsoidAndUpperCylinderFormAHouseMovingVolume() {
        var center = new BlockPos(10, 20, 30);

        var positions = BlockStructureManager.positionsInLowerEllipsoidWithUpperCylinder(
                center, 5.0, 2.0, 5.0, 5.0, 5);

        assertTrue(positions.contains(center.offset(0, -2, 0)));
        assertTrue(positions.contains(center.offset(5, 0, 0)));
        assertFalse(positions.contains(center.offset(0, -3, 0)));
        assertTrue(positions.contains(center.offset(5, 5, 0)));
        assertTrue(positions.contains(center.offset(0, 5, 5)));
        assertFalse(positions.contains(center.offset(5, 5, 5)));
        assertFalse(positions.contains(center.offset(0, 6, 0)));
        assertFalse(positions.contains(center.offset(0, 1, 6)));
    }

    @Test
    void allHouseMovingTiersFitTheStructureSnapshotLimit() {
        var center = BlockPos.ZERO;
        var small = BlockStructureManager.positionsInLowerEllipsoidWithUpperCylinder(
                center, 5.0, 2.0, 5.0, 5.0, 5).size();
        var medium = BlockStructureManager.positionsInLowerEllipsoidWithUpperCylinder(
                center, 7.0, 4.0, 7.0, 7.0, 7).size();
        var large = BlockStructureManager.positionsInLowerEllipsoidWithUpperCylinder(
                center, 9.0, 6.0, 9.0, 9.0, 9).size();

        assertTrue(small < medium);
        assertTrue(medium < large);
        assertTrue(large <= BlockStructureSnapshot.MAX_BLOCKS);
    }

    @Test
    void blockedFrontPropagatesThroughTheCandidateColumn() {
        var candidates = List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(1, 0, 1)
        );
        var occupied = Set.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(1, 0, 1)
        );

        var cropped = BlockStructureManager.cropImmediatelyBlocked(
                candidates,
                Direction.EAST,
                occupied::contains
        );

        assertEquals(List.of(
                new BlockPos(0, 0, 1),
                new BlockPos(1, 0, 1)
        ), cropped);
    }

    @Test
    void anAirGapStopsBlockedColumnPropagation() {
        var candidates = List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(2, 0, 0)
        );
        var occupied = Set.of(
                new BlockPos(0, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0)
        );

        var cropped = BlockStructureManager.cropImmediatelyBlocked(
                candidates,
                Direction.EAST,
                occupied::contains
        );

        assertEquals(List.of(new BlockPos(0, 0, 0)), cropped);
    }

    @Test
    void forwardPlaneLeavesOneBlockOfPlayerClearance() {
        var candidates = List.of(
                new BlockPos(0, 0, -2),
                new BlockPos(0, 0, -1),
                new BlockPos(0, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(0, 0, 2)
        );

        var cropped = BlockStructureManager.cropToForwardHalfSpace(
                candidates,
                new Vec3(0.5, 0.0, 0.5),
                new Vec3(0.0, 0.0, 1.0),
                1.0
        );

        assertEquals(List.of(
                new BlockPos(0, 0, 1),
                new BlockPos(0, 0, 2)
        ), cropped);
    }
}
