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

    @Test
    void consumingCarryPreservesInteractionHistoryAndRevisions() {
        var initial = StructurePose.identity(new Vec3(5, 10, 5));
        var moved = StructurePose.identity(new Vec3(6, 10, 5));
        var subLevel = new SubLevel(SubLevelId.random(), new PlotPos(0, 0),
                new AABB(0, 0, 0, 2, 3, 1), initial);
        subLevel.setState(SubLevelState.SLEEPING);
        subLevel.geometryChanged();
        subLevel.setPose(moved);
        assertEquals(initial, subLevel.previousPose());
        assertEquals(initial, subLevel.carryPose());
        long revision = subLevel.revision();
        long geometryRevision = subLevel.geometryRevision();

        subLevel.consumeCarryPose();
        subLevel.consumeCarryPose();

        assertEquals(moved, subLevel.pose());
        assertEquals(moved, subLevel.carryPose(), "a resting body must not replay the consumed movement");
        assertEquals(initial, subLevel.previousPose(), "delayed interaction still needs the actual previous pose");
        assertEquals(revision, subLevel.revision());
        assertEquals(geometryRevision, subLevel.geometryRevision());
        assertEquals(SubLevelState.SLEEPING, subLevel.state());
        var next = StructurePose.identity(new Vec3(6, 11, 5));
        subLevel.setPose(next);
        assertEquals(moved, subLevel.previousPose());
        assertEquals(moved, subLevel.carryPose());
        assertEquals(next, subLevel.pose());
        assertEquals(revision + 1, subLevel.revision());
    }

    @Test
    void multiplePoseChangesAccumulateUntilCarryIsConsumed() {
        var initial = StructurePose.identity(Vec3.ZERO);
        var intermediate = StructurePose.identity(new Vec3(1, 0, 0));
        var latest = StructurePose.identity(new Vec3(1, 1, 0));
        var subLevel = new SubLevel(SubLevelId.random(), new PlotPos(0, 0),
                new AABB(0, 0, 0, 1, 1, 1), initial);
        subLevel.setPose(intermediate);
        subLevel.setPose(latest);
        assertEquals(initial, subLevel.carryPose());
        assertEquals(intermediate, subLevel.previousPose());
        subLevel.consumeCarryPose();
        assertEquals(latest, subLevel.carryPose());
        assertEquals(intermediate, subLevel.previousPose());
        assertEquals(2, subLevel.revision());
    }
}
