package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelSnapshotVisibilityTest {
    @Test void tracksNearbySurfaceEvenWhenOriginIsFarAway() {
        var bounds = new AABB(0, 0, 0, 512, 8, 8);
        assertTrue(SubLevelSnapshotVisibility.interested(new Vec3(510, 2, 2), bounds, false));
        assertTrue(SubLevelSnapshotVisibility.interested(new Vec3(768, 2, 2), bounds, false));
        assertFalse(SubLevelSnapshotVisibility.interested(new Vec3(769, 2, 2), bounds, false));
    }

    @Test void negativeWorldCoordinatesAndVerticalSurfaceDistancesUseTheSameBudget() {
        var bounds = new AABB(-512,-100,-20,-500,-90,-10);
        assertTrue(SubLevelSnapshotVisibility.interested(new Vec3(-506,166,-15),bounds,false));
        assertFalse(SubLevelSnapshotVisibility.interested(new Vec3(-506,167,-15),bounds,false));
        assertTrue(SubLevelSnapshotVisibility.interested(new Vec3(-506,-95,-15),bounds,false));
        assertFalse(SubLevelSnapshotVisibility.interested(new Vec3(Double.NaN,-95,-15),bounds,true));
    }

    @Test void hysteresisAvoidsRepeatedFullStateWhenWalkingAcrossBoundary() {
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var boundary = new Vec3(265, 0, 0);
        assertFalse(SubLevelSnapshotVisibility.interested(boundary, bounds, false));
        assertTrue(SubLevelSnapshotVisibility.interested(boundary, bounds, true));
        assertFalse(SubLevelSnapshotVisibility.interested(new Vec3(274, 0, 0), bounds, true));
    }
}
