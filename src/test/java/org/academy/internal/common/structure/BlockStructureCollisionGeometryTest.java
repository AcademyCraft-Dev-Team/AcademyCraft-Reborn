package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureCollisionGeometryTest {
    @Test
    void adjacentFullBoxesMergeAcrossMultipleAxes() {
        var merged = BlockStructureCollisionGeometry.mergeBoxes(List.of(
                new AABB(0, 0, 0, 1, 1, 1),
                new AABB(1, 0, 0, 2, 1, 1),
                new AABB(0, 0, 1, 1, 1, 2),
                new AABB(1, 0, 1, 2, 1, 2)
        ));

        assertEquals(1, merged.size());
        assertBox(new AABB(0, 0, 0, 2, 1, 2), merged.getFirst());
    }

    @Test
    void boxesWithDifferentCrossSectionsRemainSeparate() {
        var merged = BlockStructureCollisionGeometry.mergeBoxes(List.of(
                new AABB(0, 0, 0, 1, 0.5, 1),
                new AABB(1, 0, 0, 2, 1, 1)
        ));

        assertEquals(2, merged.size());
    }

    @Test
    void quarterTurnProducesTheExpectedWorldAlignedBounds() {
        var rotated = BlockStructureCollisionGeometry.rotateBox(
                new AABB(0, 0, 0, 1, 1, 0.5),
                1.0,
                1.0,
                90.0f
        );

        assertBox(new AABB(1.5, 0, 0, 2, 1, 1), rotated);
    }

    @Test
    void supportUsesTheConcreteCollisionSurface() {
        var surface = List.of(new AABB(0, 0, 0, 1, 0.5, 1));

        assertTrue(BlockStructureCollisionGeometry.supports(
                surface,
                new AABB(0.2, 0.5, 0.2, 0.8, 2.3, 0.8),
                0.01
        ));
        assertFalse(BlockStructureCollisionGeometry.supports(
                surface,
                new AABB(1.1, 0.5, 0.2, 1.7, 2.3, 0.8),
                0.01
        ));
    }

    private static void assertBox(AABB expected, AABB actual) {
        assertEquals(expected.minX, actual.minX, 1.0e-9);
        assertEquals(expected.minY, actual.minY, 1.0e-9);
        assertEquals(expected.minZ, actual.minZ, 1.0e-9);
        assertEquals(expected.maxX, actual.maxX, 1.0e-9);
        assertEquals(expected.maxY, actual.maxY, 1.0e-9);
        assertEquals(expected.maxZ, actual.maxZ, 1.0e-9);
    }
}
