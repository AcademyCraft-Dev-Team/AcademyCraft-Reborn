package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureApiTest {
    @Test
    void structureCanBeTargetedFromInsideItsOuterBounds() {
        var bounds = new AABB(-4.0, 0.0, -4.0, 4.0, 6.0, 4.0);

        assertTrue(BlockStructureApi.viewerInsideStructureBounds(
                bounds,
                new AABB(-0.3, 1.0, -0.3, 0.3, 2.8, 0.3),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 2.62, 0.0)
        ));
        assertTrue(BlockStructureApi.viewerInsideStructureBounds(
                bounds,
                new AABB(-0.3, -0.5, -0.3, 0.3, 1.3, 0.3),
                new Vec3(0.0, -0.5, 0.0),
                new Vec3(0.0, 1.12, 0.0)
        ));
        assertTrue(BlockStructureApi.viewerInsideStructureBounds(
                bounds,
                new AABB(-0.3, 5.9, -0.3, 0.3, 6.2, 0.3),
                new Vec3(0.0, 6.1, 0.0),
                new Vec3(0.0, 7.72, 0.0)
        ));
        assertFalse(BlockStructureApi.viewerInsideStructureBounds(
                bounds,
                new AABB(7.7, 1.0, -0.3, 8.3, 2.8, 0.3),
                new Vec3(8.0, 1.0, 0.0),
                new Vec3(8.0, 2.62, 0.0)
        ));
    }
}
