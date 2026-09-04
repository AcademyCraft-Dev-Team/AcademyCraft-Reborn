package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void supportSurfaceFindsAndCorrectsAFeetPenetration() {
        var surface = List.of(new AABB(0, 0, 0, 1, 1, 1));
        var entity = new AABB(0.2, 0.92, 0.2, 0.8, 2.72, 0.8);

        var supportY = BlockStructureCollisionGeometry.supportSurfaceY(
                surface, entity, 0.18);

        assertTrue(supportY.isPresent());
        assertEquals(1.0, supportY.getAsDouble(), 1.0e-9);
        assertEquals(0.08, supportY.getAsDouble() - entity.minY, 1.0e-9);
    }

    @Test
    void supportSurfaceSelectsTheNearestOverlappingFloor() {
        var surfaces = List.of(
                new AABB(0, 0, 0, 1, 1, 1),
                new AABB(0, 1.25, 0, 1, 1.5, 1)
        );
        var entity = new AABB(0.2, 1.42, 0.2, 0.8, 3.22, 0.8);

        var supportY = BlockStructureCollisionGeometry.supportSurfaceY(
                surfaces, entity, 0.5);

        assertTrue(supportY.isPresent());
        assertEquals(1.5, supportY.getAsDouble(), 1.0e-9);
    }

    @Test
    void sweepFindsEarliestPositiveAxisImpact() {
        var hit = BlockStructureCollisionGeometry.sweep(
                new AABB(0, 0, 0, 1, 1, 1),
                new AABB(2, 0, 0, 3, 1, 1),
                new Vec3(2, 0, 0)
        );

        assertNotNull(hit);
        assertEquals(0.5, hit.time(), 1.0e-9);
        assertEquals(new Vec3(-1, 0, 0), hit.normal());
    }

    @Test
    void sweepFindsNegativeAxisImpactAndRejectsParallelMiss() {
        var hit = BlockStructureCollisionGeometry.sweep(
                new AABB(3, 0, 0, 4, 1, 1),
                new AABB(1, 0, 0, 2, 1, 1),
                new Vec3(-2, 0, 0)
        );

        assertNotNull(hit);
        assertEquals(0.5, hit.time(), 1.0e-9);
        assertEquals(new Vec3(1, 0, 0), hit.normal());
        assertNull(BlockStructureCollisionGeometry.sweep(
                new AABB(0, 0, 0, 1, 1, 1),
                new AABB(2, 2, 0, 3, 3, 1),
                new Vec3(2, 0, 0)
        ));
    }

    @Test
    void rigidBoxSetUsesTheMostRestrictiveWorldCollision() {
        var movement = BlockStructureCollisionGeometry.collideBoxes(
                List.of(
                        new AABB(0, 0, 0, 1, 1, 1),
                        new AABB(2, 0, 0, 3, 1, 1)
                ),
                List.of(Shapes.create(new AABB(4, 0, 0, 5, 1, 1))),
                new Vec3(2, 0, 0)
        );

        assertEquals(new Vec3(1, 0, 0), movement);
    }

    @Test
    void rigidBoxSetKeepsRequestedMovementWhenWorldIsClear() {
        var requested = new Vec3(1.5, -0.25, 2.0);

        var movement = BlockStructureCollisionGeometry.collideBoxes(
                List.of(new AABB(0, 0, 0, 1, 1, 1)),
                List.of(),
                requested
        );

        assertEquals(requested, movement);
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
