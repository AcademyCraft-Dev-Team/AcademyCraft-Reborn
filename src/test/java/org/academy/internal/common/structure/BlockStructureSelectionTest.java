package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
}
