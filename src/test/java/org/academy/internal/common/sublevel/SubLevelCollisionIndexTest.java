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
    void emptyGapBetweenTwoWallsIsNotCollidable() {
        var index = new SubLevelCollisionIndex();
        index.update(ID, List.of(new AABB(0, 0, 0, 1, 3, 1), new AABB(3, 0, 0, 4, 3, 1)), IDENTITY);
        assertTrue(index.query(new AABB(11.2, 64, 10.2, 12.8, 66, 10.8)).isEmpty());
        assertEquals(1, index.query(new AABB(10.2, 64, 10.2, 10.8, 66, 10.8)).size());
        assertEquals(1, index.query(new AABB(13.2, 64, 10.2, 13.8, 66, 10.8)).size());
        assertEquals(new AABB(10, 64, 10, 14, 67, 11), index.worldBounds(ID).orElseThrow());
        assertTrue(index.remove(ID));
        assertTrue(index.query(new AABB(10, 64, 10, 14, 67, 11)).isEmpty());
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
