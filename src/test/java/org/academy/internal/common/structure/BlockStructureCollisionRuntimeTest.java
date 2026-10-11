package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureCollisionRuntimeTest {
    private static final AABB QUERY_BOUNDS = new AABB(-4, -4, -4, 4, 4, 4);

    @Test
    void carryExclusionIsScopedAndRestored() {
        var collision = new StubCollision();

        assertFalse(BlockStructureCollisionRuntime.isIgnored(collision));
        BlockStructureCollisionRuntime.runIgnoring(collision, () ->
                assertTrue(BlockStructureCollisionRuntime.isIgnored(collision)));
        assertFalse(BlockStructureCollisionRuntime.isIgnored(collision));
    }

    @Test
    void indexSupplementPreservesForeignShapesAndDeduplicatesStructuresByIdentity() {
        var firstBlock = Shapes.box(0, 0, 0, 1, 1, 1);
        var secondBlock = Shapes.box(2, 0, 0, 3, 1, 1);
        var ordinaryEntityShape = Shapes.box(-2, 0, 0, -1, 1, 1);
        // A foreign contribution may even have the same geometry as our broad-phase box.
        var foreignShape = Shapes.box(0, 0, 0, 3, 1, 1);
        var queried = new StubCollision(firstBlock, secondBlock);
        var missed = new StubCollision(Shapes.box(0, 2, 0, 1, 3, 1));
        var query = new BlockStructureCollisionRuntime.CollisionQuery(QUERY_BOUNDS);
        var upstream = new ArrayList<>(List.of(ordinaryEntityShape, foreignShape));
        upstream.addAll(query.collect(queried));

        var result = query.appendTo(upstream, List.of(queried, missed, missed));

        assertEquals(5, result.size());
        for (int index = 0; index < upstream.size(); index++) {
            assertSame(upstream.get(index), result.get(index));
        }
        assertSame(missed.shapes.getFirst(), result.getLast());
        assertEquals(1, queried.queries);
        assertEquals(1, missed.queries);
        assertSame(QUERY_BOUNDS, missed.lastBounds);
        assertEquals(4, upstream.size());
    }

    @Test
    void equalGeometryDoesNotHideASeparateStructure() {
        var shape = Shapes.block();
        var first = new StubCollision(shape);
        var second = new StubCollision(shape);
        var query = new BlockStructureCollisionRuntime.CollisionQuery(QUERY_BOUNDS);

        var result = query.appendTo(List.of(), List.of(first, second));

        assertEquals(List.of(shape, shape), result);
        assertEquals(1, first.queries);
        assertEquals(1, second.queries);
    }

    @Test
    void carryExclusionAlsoAppliesToTheIndexAndRestoresAfterNestedFailure() {
        var carried = new StubCollision(Shapes.block());
        var other = new StubCollision(Shapes.block());
        var original = List.of(Shapes.box(-2, 0, 0, -1, 1, 1));

        BlockStructureCollisionRuntime.runIgnoring(carried, () -> {
            assertThrows(IllegalStateException.class, () ->
                    BlockStructureCollisionRuntime.runIgnoring(other, () -> {
                        assertTrue(BlockStructureCollisionRuntime.isIgnored(other));
                        throw new IllegalStateException("nested collision query failed");
                    }));
            assertTrue(BlockStructureCollisionRuntime.isIgnored(carried));
            var query = new BlockStructureCollisionRuntime.CollisionQuery(QUERY_BOUNDS);
            assertSame(original, query.appendTo(original, List.of(carried)));
        });

        assertFalse(BlockStructureCollisionRuntime.isIgnored(carried));
        assertEquals(0, carried.queries);
        var nextQuery = new BlockStructureCollisionRuntime.CollisionQuery(QUERY_BOUNDS);
        assertEquals(2, nextQuery.appendTo(original, List.of(carried)).size());
        assertEquals(1, carried.queries);
    }

    private static final class StubCollision implements BlockStructureCollision {
        private final List<VoxelShape> shapes;
        private int queries;
        private AABB lastBounds;

        private StubCollision(VoxelShape... shapes) {
            this.shapes = List.of(shapes);
        }

        @Override
        public void collectCollisionShapes(AABB bounds, Consumer<VoxelShape> output) {
            queries++;
            lastBounds = bounds;
            shapes.forEach(output);
        }

        @Override
        public boolean supports(AABB entityBounds, double tolerance) {
            return false;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StubCollision collision && shapes.equals(collision.shapes);
        }

        @Override
        public int hashCode() {
            return shapes.hashCode();
        }
    }
}
