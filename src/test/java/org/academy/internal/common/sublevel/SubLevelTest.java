package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelTest {
    @Test
    void poseAndRevisionTrackMovement() {
        var subLevel = new SubLevel(SubLevelId.random(), new PlotPos(0, 0),
                new AABB(0, 0, 0, 2, 3, 1), StructurePose.identity(new Vec3(5, 10, 5)));
        assertEquals(SubLevelState.ALLOCATED, subLevel.state());
        var first = subLevel.worldBounds();
        assertEquals(new AABB(5, 10, 5, 7, 13, 6), first);
        subLevel.setPose(StructurePose.identity(new Vec3(6, 10, 5)));
        assertEquals(new AABB(6, 10, 5, 8, 13, 6), subLevel.worldBounds());
        assertEquals(1, subLevel.revision());
        assertEquals(new Vec3(7, 11.5, 5.5), subLevel.worldCenter());
        subLevel.setState(SubLevelState.ACTIVE);
        assertEquals(SubLevelState.ACTIVE, subLevel.state());
    }
}
