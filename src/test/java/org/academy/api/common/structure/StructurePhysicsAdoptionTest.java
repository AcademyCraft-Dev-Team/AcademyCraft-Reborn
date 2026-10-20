package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructurePhysicsAdoptionTest {
    @Test
    void partialOverlapUsesExistingSlackWithoutMovingBodiesOrGrowingBeyondBudget() {
        var previous = new AABB(-210, 57.25, 125, -191, 77.25, 144);
        var bodies = new AABB(-204, 63, 130, -187, 72, 138);
        assertEquals(new AABB(-206, 57.25, 125, -187, 77.25, 144),
                StructurePhysics.adoptionBounds(previous, bodies));
    }

    @Test
    void containedBodiesKeepBoundsAndOversizedCohortsAreRejected() {
        var previous = new AABB(0, 0, 0, 24, 24, 24);
        assertEquals(previous, StructurePhysics.adoptionBounds(previous, new AABB(2, 2, 2, 4, 4, 4)));
        assertNull(StructurePhysics.adoptionBounds(previous, new AABB(0, 0, 0, 25, 2, 2)));
    }
}
