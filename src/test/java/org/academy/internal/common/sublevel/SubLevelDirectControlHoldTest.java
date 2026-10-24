package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelDirectControlHoldTest {
    @Test
    void ordinaryManualHoldRetainsItsInitialStabilizationAndCaptureAngle() {
        var source = StructurePose.identity(new Vec3(3, 50, 8));
        var hold = new SubLevelDirectControl.Hold(UUID.randomUUID(), source, 100, 110);
        for (long tick = 100; tick <= 110; tick++) {
            assertTrue(hold.beginTick(false, source, tick));
            assertTrue(hold.stabilizing(tick));
        }
        assertTrue(hold.beginTick(false, OrientationTargets.axisAngle(new Vec3(1, 0, 0), 0.5), 111));
        assertFalse(hold.stabilizing(111));
        assertEquals(source, hold.target(), "Ordinary contact disturbance must not become the held target");
    }

    @Test
    void explicitAngularOperationBypassesInitialFreezeAndRetainsItsCompletedAngle() {
        var source = StructurePose.identity(Vec3.ZERO);
        var finalAngle = OrientationTargets.axisAngle(new Vec3(1, 2, 0), -0.7);
        var hold = new SubLevelDirectControl.Hold(UUID.randomUUID(), source, 100, 110);
        assertFalse(hold.beginTick(true, source, 101), "Angular owner also bypasses the initial velocity freeze");
        assertFalse(hold.beginTick(true, finalAngle, 102));
        assertTrue(hold.beginTick(false, finalAngle, 103));
        assertFalse(hold.stabilizing(103), "Finishing early must not resume the old translation freeze");
        assertEquals(finalAngle, hold.target());
        assertTrue(hold.beginTick(false, source, 104));
        assertEquals(finalAngle, hold.target(), "Only the ownership transition may update the held angle");
    }

    @Test
    void successiveAngularOperationsRebaseOnTheirOwnActualCompletion() {
        var source = StructurePose.identity(Vec3.ZERO);
        var first = OrientationTargets.axisAngle(new Vec3(0, 1, 0), 0.6);
        var second = OrientationTargets.axisAngle(new Vec3(0, 0, 1), -1.2);
        var hold = new SubLevelDirectControl.Hold(UUID.randomUUID(), source, 0, 10);
        assertFalse(hold.beginTick(true, source, 20));
        assertTrue(hold.beginTick(false, first, 30));
        assertEquals(first, hold.target());
        assertFalse(hold.beginTick(true, first, 40));
        assertFalse(hold.beginTick(true, second, 45));
        assertTrue(hold.beginTick(false, second, 50));
        assertEquals(second, hold.target());
        assertFalse(hold.stabilizing(50));
    }
}
