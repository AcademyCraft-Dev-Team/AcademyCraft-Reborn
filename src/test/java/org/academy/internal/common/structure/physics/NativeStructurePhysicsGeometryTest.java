package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Shape mathematics needs no vanilla bootstrap; real states and chunk guards run in the gameplay probe. */
class NativeStructurePhysicsGeometryTest {
    @Test
    void fullBlockAndBottomSlabKeepTheirActualVolumeAndOffset() {
        var offset = new PhysicsVector(-3, 80, 7);
        var full = NativeStructurePhysicsGeometry.decompose(Shapes.block(), offset, 64);
        assertEquals(List.of(new PhysicsBox(new PhysicsVector(-2.5, 80.5, 7.5),
                PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5))), full);
        var slab = NativeStructurePhysicsGeometry.decompose(Shapes.box(0, 0, 0, 1, 0.5, 1), offset, 64);
        assertEquals(0.5, volume(slab), 1.0E-12);
        assertTrue(contains(slab, -2.5, 80.25, 7.5));
        assertFalse(contains(slab, -2.5, 80.75, 7.5));
    }

    @Test
    void stairsPreserveTheirMissingUpperQuarterInsteadOfFillingBounds() {
        var shape = Shapes.or(Shapes.box(0, 0, 0, 1, 0.5, 1), Shapes.box(0, 0.5, 0.5, 1, 1, 1));
        var boxes = NativeStructurePhysicsGeometry.decompose(shape, PhysicsVector.ZERO, 64);
        assertEquals(2, boxes.size());
        assertEquals(0.75, volume(boxes), 1.0E-12);
        assertTrue(contains(boxes, 0.5, 0.25, 0.25));
        assertTrue(contains(boxes, 0.5, 0.75, 0.75));
        assertFalse(contains(boxes, 0.5, 0.75, 0.25));
    }

    @Test
    void tallPostAndSeparatedRailShapeKeepsItsHeightAndGaps() {
        // Synthetic compound, not a claim about vanilla fence collision (which differs from its model).
        var post = Shapes.box(0.375, 0, 0.375, 0.625, 1.5, 0.625);
        var lowerRail = Shapes.box(0, 0.375, 0.4375, 1, 0.5625, 0.5625);
        var upperRail = Shapes.box(0, 0.75, 0.4375, 1, 0.9375, 0.5625);
        var boxes = NativeStructurePhysicsGeometry.decompose(Shapes.or(post, lowerRail, upperRail), PhysicsVector.ZERO, 64);
        assertTrue(contains(boxes, 0.5, 1.4, 0.5));
        assertTrue(contains(boxes, 0.125, 0.8, 0.5));
        assertFalse(contains(boxes, 0.125, 0.65, 0.5));
        assertFalse(contains(boxes, 0.125, 0.125, 0.5));
    }

    @Test
    void hollowContainerShapeRemainsHollowAndHasNoOverlappingMassPieces() {
        var shape = Shapes.or(Shapes.box(0, 0, 0, 1, 0.125, 1),
                Shapes.box(0, 0.125, 0, 0.125, 1, 1), Shapes.box(0.875, 0.125, 0, 1, 1, 1),
                Shapes.box(0.125, 0.125, 0, 0.875, 1, 0.125), Shapes.box(0.125, 0.125, 0.875, 0.875, 1, 1));
        var boxes = NativeStructurePhysicsGeometry.decompose(shape, PhysicsVector.ZERO, 64);
        assertEquals(1 - 0.75 * 0.875 * 0.75, volume(boxes), 1.0E-12);
        assertFalse(contains(boxes, 0.5, 0.5, 0.5));
        assertTrue(contains(boxes, 0.05, 0.5, 0.5));
    }

    @Test
    void emptyShapesCostNoBoxesButNonemptyShapesCannotExceedRemainingCapacity() {
        assertEquals(List.of(), NativeStructurePhysicsGeometry.decompose(Shapes.empty(), PhysicsVector.ZERO, 0));
        var full = assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                () -> NativeStructurePhysicsGeometry.decompose(Shapes.block(), PhysicsVector.ZERO, 0));
        assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED, full.failure());
        var stairs = Shapes.or(Shapes.box(0, 0, 0, 1, 0.5, 1), Shapes.box(0, 0.5, 0.5, 1, 1, 1));
        assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED,
                assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                        () -> NativeStructurePhysicsGeometry.decompose(stairs, PhysicsVector.ZERO, 1)).failure());
    }

    @Test
    void shapeAndNativeCoordinateBoundsRejectRatherThanClamp() {
        for (var shape : List.of(Shapes.box(-1.01, 0, 0, 1, 1, 1),
                Shapes.box(0, 0, 0, 1, 2.01, 1), Shapes.box(0, 0, 0, 5.0E-7, 1, 1))) {
            assertEquals(NativeStructurePhysicsGeometry.Failure.INVALID_SHAPE,
                    assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                            () -> NativeStructurePhysicsGeometry.decompose(shape, PhysicsVector.ZERO, 64)).failure());
        }
        assertEquals(NativeStructurePhysicsGeometry.Failure.INVALID_SHAPE,
                assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                        () -> NativeStructurePhysicsGeometry.decompose(Shapes.block(), new PhysicsVector(1.0E6, 0, 0), 64)).failure());
    }

    @Test
    void terrainBatchesHonor64PerBodyAnd2048PerWorldWithoutDroppingTail() {
        var box = new PhysicsBox(new PhysicsVector(0.5, 0.5, 0.5), PhysicsQuaternion.IDENTITY, new PhysicsVector(0.5, 0.5, 0.5));
        var input = new ArrayList<>(Collections.nCopies(129, box));
        var batches = NativeStructurePhysicsGeometry.batches(input);
        assertEquals(List.of(64, 64, 1), batches.stream().map(List::size).toList());
        input.clear();
        assertEquals(129, batches.stream().mapToInt(List::size).sum());
        assertThrows(UnsupportedOperationException.class, () -> batches.getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> batches.clear());
        assertEquals(32, NativeStructurePhysicsGeometry.batches(Collections.nCopies(2048, box)).size());
        assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED,
                assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                        () -> NativeStructurePhysicsGeometry.batches(Collections.nCopies(2049, box))).failure());
    }

    @Test
    void terrainScanBudgetRejectsOverflowAndEmptyRangesBeforeReadingChunks() {
        assertDoesNotThrow(() -> NativeStructurePhysicsGeometry.requireCellBudget(0, 0, 0, 63, 15, 63));
        assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED,
                assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                        () -> NativeStructurePhysicsGeometry.requireCellBudget(0, 0, 0, 64, 15, 63)).failure());
        assertEquals(NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED,
                assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                        () -> NativeStructurePhysicsGeometry.requireCellBudget(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)).failure());
        assertEquals(NativeStructurePhysicsGeometry.Failure.INVALID_BOUNDS,
                assertThrows(NativeStructurePhysicsGeometry.GeometryException.class,
                        () -> NativeStructurePhysicsGeometry.requireCellBudget(2, 0, 0, 1, 0, 0)).failure());
    }

    private static double volume(List<PhysicsBox> boxes) {
        return boxes.stream().mapToDouble(box -> 8 * box.halfExtents().x() * box.halfExtents().y() * box.halfExtents().z()).sum();
    }

    private static boolean contains(List<PhysicsBox> boxes, double x, double y, double z) {
        return boxes.stream().anyMatch(box -> Math.abs(x - box.center().x()) < box.halfExtents().x()
                && Math.abs(y - box.center().y()) < box.halfExtents().y()
                && Math.abs(z - box.center().z()) < box.halfExtents().z());
    }
}
