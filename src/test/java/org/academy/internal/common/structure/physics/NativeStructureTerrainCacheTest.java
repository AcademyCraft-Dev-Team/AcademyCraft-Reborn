package org.academy.internal.common.structure.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureTerrainCacheTest {
    @Test
    void sectionClipsCoverEveryHaloCellExactlyOnceAcrossNegativeCoordinates() {
        var scan = new AABB(-17, -1, 15, 10, 26, 42);
        var partitions = NativeStructureTerrainCache.partitionBounds(scan);
        assertEquals(27, partitions.size());
        var observed = new HashSet<BlockPos>();
        for (var part : partitions) {
            assertTrue(NativeStructurePhysics.contains(scan, part));
            assertTrue(part.getXsize() > 0 && part.getXsize() <= 16);
            assertTrue(part.getYsize() > 0 && part.getYsize() <= 16);
            assertTrue(part.getZsize() > 0 && part.getZsize() <= 16);
            assertEquals(((int) part.minX) >> 4, (((int) part.maxX) - 1) >> 4);
            assertEquals(((int) part.minY) >> 4, (((int) part.maxY) - 1) >> 4);
            assertEquals(((int) part.minZ) >> 4, (((int) part.maxZ) - 1) >> 4);
            for (var cell : BlockPos.betweenClosed((int) part.minX, (int) part.minY, (int) part.minZ,
                    (int) part.maxX - 1, (int) part.maxY - 1, (int) part.maxZ - 1)) {
                assertTrue(observed.add(cell.immutable()), "A cell cannot be extracted by two partitions");
            }
        }
        assertEquals(27 * 27 * 27, observed.size());
        for (var cell : BlockPos.betweenClosed(-17, -1, 15, 9, 25, 41)) {
            assertTrue(observed.contains(cell), "Air and solid cells must have the same complete coverage");
        }
    }

    @Test
    void exactSectionEndsAreExclusiveAndTraversalOrderIsStable() {
        var aligned = new AABB(-16, 64, 16, 0, 80, 32);
        assertEquals(List.of(aligned), NativeStructureTerrainCache.partitionBounds(aligned));
        var crossing = new AABB(-1, 0, 15, 1, 1, 17);
        var expected = List.of(new AABB(-1, 0, 15, 0, 1, 16), new AABB(0, 0, 15, 1, 1, 16),
                new AABB(-1, 0, 16, 0, 1, 17), new AABB(0, 0, 16, 1, 1, 17));
        var actual = NativeStructureTerrainCache.partitionBounds(crossing);
        assertEquals(expected, actual);
        assertEquals(actual, NativeStructureTerrainCache.partitionBounds(crossing));
        assertThrows(UnsupportedOperationException.class, actual::clear);
    }

    @Test
    void partitionPlanningRejectsFractionalEmptyAndUnboundedRangesBeforeAllocation() {
        for (var invalid : List.of(new AABB(0.5, 0, 0, 1, 1, 1), new AABB(Double.NaN, 0, 0, 1, 1, 1),
                new AABB(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1),
                new AABB(Integer.MIN_VALUE, 0, 0, Integer.MIN_VALUE + 1.0, 1, 1))) {
            assertThrows(IllegalArgumentException.class, () -> NativeStructureTerrainCache.partitionBounds(invalid));
        }
        var empty = assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                () -> NativeStructureTerrainCache.partitionBounds(new AABB(0, 0, 0, 0, 1, 1)));
        assertEquals(NativeStructurePhysicsGeometry.Failure.INVALID_BOUNDS, empty.failure());
        var oversized = assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                () -> NativeStructureTerrainCache.partitionBounds(new AABB(0, 0, 0, 65_537, 1, 1)));
        assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED, oversized.failure());
    }

    @Test
    void combinedWarmAndDirtyCostsMustFitTheCurrentWholeQueryAllowance() {
        assertDoesNotThrow(() -> NativeStructurePhysicsGeometry.requireTerrainBudget(2048, 131_072, 2048));
        assertDoesNotThrow(() -> NativeStructurePhysicsGeometry.requireTerrainBudget(0, 131_072, 0));
        for (var costs : List.of(new int[]{64, 100, 63}, new int[]{1, 131_073, 2048},
                new int[]{2049, 100, 2048}, new int[]{0, 0, -1}, new int[]{0, 0, 2049})) {
            var failure = assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                    () -> NativeStructurePhysicsGeometry.requireTerrainBudget(costs[0], costs[1], costs[2]));
            assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED, failure.failure());
        }
    }
}
