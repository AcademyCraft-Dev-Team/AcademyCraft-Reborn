package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelCollisionIndexTest {
    private static final SubLevelId ID = SubLevelId.random();
    private static final StructurePose IDENTITY = StructurePose.identity(new Vec3(10, 64, 10));

    @Test
    void broadPhaseUnionCoversTheGapButExactContactKeepsItEmpty() {
        var index = new SubLevelCollisionIndex();
        index.update(ID, List.of(new AABB(0, 0, 0, 1, 3, 1), new AABB(3, 0, 0, 4, 3, 1)), IDENTITY);
        var gap = new AABB(11.2, 64, 10.2, 12.8, 66, 10.8);
        // The query is only a broad phase: one structure entry covers the union, including the gap.
        assertEquals(1, index.query(gap).size());
        var entry = index.query(gap).getFirst();
        var entity = new AABB(11.2, 64.05, 10.2, 11.8, 65.8, 10.8);
        for (var local : entry.localBoxes()) {
            var box = SubLevelOrientedCollision.Box.of(local, entry.pose());
            assertFalse(SubLevelOrientedCollision.clip(box, entity, net.minecraft.core.Direction.Axis.Y, -0.5)
                    .blocked(), "an exact oracle must not fill the gap between two cells");
        }
        assertEquals(1, index.query(new AABB(10.2, 64, 10.2, 10.8, 66, 10.8)).size());
        assertEquals(1, index.query(new AABB(13.2, 64, 10.2, 13.8, 66, 10.8)).size());
        assertEquals(new AABB(10, 64, 10, 14, 67, 11), index.worldBounds(ID).orElseThrow());
        assertTrue(index.remove(ID));
        assertTrue(index.query(new AABB(10, 64, 10, 14, 67, 11)).isEmpty());
    }

    @Test
    void largeLocalSetsUseTheBoundedCellGridAndReuseTheEntryAcrossPoseChanges() {
        var boxes = new java.util.ArrayList<AABB>();
        for (int index = 0; index < 256; index++) boxes.add(new AABB(index, 0, 0, index + 1, 1, 1));
        var immutable = List.copyOf(boxes);
        var index = new SubLevelCollisionIndex();
        index.update(ID, immutable, IDENTITY);
        var entry = index.entry(ID).orElseThrow();
        assertEquals(1, entry.localBoxesIn(new AABB(50.2, 0.2, 0.2, 50.8, 0.8, 0.8)).size());
        assertTrue(entry.localBoxesIn(new AABB(900, 0, 0, 901, 1, 1)).isEmpty());
        index.update(ID, immutable, StructurePose.identity(new Vec3(20, 70, 20)), IDENTITY, 1, 2);
        assertSame(entry, index.entry(ID).orElseThrow(), "a pose-only update must keep the local geometry and grid");
        assertEquals(20, entry.pose().worldOrigin().x, 1.0E-9);
    }

    @Test
    void gridRetainsMergedFloorsThatExceedItsPerBoxCellBudget() {
        var boxes = new java.util.ArrayList<AABB>();
        var floor = new AABB(0, 0, 0, 128, 1, 1);
        boxes.add(floor);
        for (int index = 0; index < 128; index++) boxes.add(new AABB(1000 + index, 0, 0, 1001 + index, 1, 1));
        var index = new SubLevelCollisionIndex();
        index.update(ID, List.copyOf(boxes), IDENTITY);
        var entry = index.entry(ID).orElseThrow();
        assertEquals(List.of(floor), entry.localBoxesIn(new AABB(50.2, 0.2, 0.2, 50.8, 0.8, 0.8)));
        assertEquals(1, entry.localBoxesIn(new AABB(1000.2, 0.2, 0.2, 1000.8, 0.8, 0.8)).size());
        assertTrue(entry.localBoxesIn(new AABB(500, 0, 0, 501, 1, 1)).isEmpty());
    }

    @Test
    void rotationKeepsTheCenterCollidableWithoutAWholeStructureAabbResult() {
        var index = new SubLevelCollisionIndex();
        var rotation = new Quaterniond().rotationY(Math.PI / 4);
        var pose = new StructurePose(new Vec3(2, 5, 2), rotation);
        index.update(ID, List.of(new AABB(0, 0, 0, 1, 1, 1)), pose);
        var center = new AABB(2.45, 5.45, 2.45, 2.55, 5.55, 2.55);
        assertFalse(index.query(center).isEmpty());
        assertTrue(index.query(new AABB(4.5, 5.4, 4.5, 4.6, 5.6, 4.6)).isEmpty());
    }
}
