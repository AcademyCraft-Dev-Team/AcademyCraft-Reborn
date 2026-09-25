package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureCollisionRuntimeTest {
    @Test
    void carryExclusionIsScopedAndRestored() {
        var collision = new StubCollision();

        assertFalse(BlockStructureCollisionRuntime.isIgnored(collision));
        BlockStructureCollisionRuntime.runIgnoring(collision, () ->
                assertTrue(BlockStructureCollisionRuntime.isIgnored(collision)));
        assertFalse(BlockStructureCollisionRuntime.isIgnored(collision));
    }

    private static final class StubCollision implements BlockStructureCollision {
        @Override
        public void collectCollisionShapes(AABB bounds, Consumer<VoxelShape> output) {
        }

        @Override
        public boolean supports(AABB entityBounds, double tolerance) {
            return false;
        }
    }
}
