package org.academy.internal.common.ability.aeromanip;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeromanipTargetingTest {
    @Test
    void widenedViewRayUsesDistanceToTheCandidateAabb() {
        var box = new AABB(2.0, 1.0, 3.0, 4.0, 2.0, 5.0);
        assertEquals(0.0, AeromanipTargeting.distanceToBoxSqr(
                new Vec3(3.0, 1.5, 4.0), box), 1.0e-9);
        assertEquals(4.0, AeromanipTargeting.distanceToBoxSqr(
                new Vec3(6.0, 1.5, 4.0), box), 1.0e-9);
    }

    @Test
    void entityAttachmentPointFollowsTheRequestedOutwardDirection() {
        var box = new AABB(-1.0, 0.0, -0.5, 1.0, 2.0, 0.5);
        var point = AeromanipTargeting.pointOutside(box, new Vec3(1.0, 0.0, 0.0), 0.08);
        assertEquals(1.08, point.x, 1.0e-9);
        assertEquals(1.0, point.y, 1.0e-9);
        assertEquals(0.0, point.z, 1.0e-9);
    }

    @Test
    void accelerationStartsStationaryTargetsAndStopsAtForwardLimit() {
        var started = AeromanipTargeting.acceleratedVelocity(Vec3.ZERO, new Vec3(2, 0, 0), 0.2, 0.6);
        assertEquals(0.2, started.x, 1.0e-9);

        var limited = AeromanipTargeting.acceleratedVelocity(new Vec3(0.55, 0.2, 0),
                new Vec3(1, 0, 0), 0.2, 0.6);
        assertEquals(0.6, limited.x, 1.0e-9);
        assertEquals(0.2, limited.y, 1.0e-9);
    }

    @Test
    void explicitJetCeilingAllowsStackedNozzlesToExceedNormalAirflowSpeed() {
        var velocity = AeromanipTargeting.acceleratedVelocity(
                new Vec3(2.95, 0, 0), new Vec3(1, 0, 0), 0.5, 6.0, 6.0);
        assertEquals(3.45, velocity.x, 1.0e-9);

        var capped = AeromanipTargeting.acceleratedVelocity(
                new Vec3(5.9, 0, 0), new Vec3(1, 0, 0), 0.5, 6.0, 6.0);
        assertEquals(6.0, capped.x, 1.0e-9);
    }

    @Test
    void steeringConvergesTowardTheRequestedAirflow() {
        var velocity = AeromanipTargeting.steeredVelocity(
                new Vec3(0.4, 0, 0), new Vec3(0, 0, 2), 0.5, 1.2);
        assertEquals(0.2, velocity.x, 1.0e-9);
        assertEquals(0.6, velocity.z, 1.0e-9);
    }

    @Test
    void nonFiniteInputsCannotCreateInvalidVelocity() {
        var velocity = AeromanipTargeting.acceleratedVelocity(
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 1.0, 1.0);
        assertEquals(Vec3.ZERO, velocity);
    }

    @Test
    void updraftAimsAtAnAirborneCore() {
        var direction = AeromanipTargeting.updraftDirection(
                Vec3.ZERO, new Vec3(3, 0.5, 0), 3.0);
        assertEquals(-3.0, direction.x, 1.0e-9);
        assertEquals(2.5, direction.y, 1.0e-9);
    }

    @Test
    void controlDistanceUsesOneServerValidatedStepAndClampsToRange() {
        assertEquals(3.5, AeromanipTargeting.adjustControlDistance(2.5, 99, 1.0, 2.0, 10.0), 1.0e-9);
        assertEquals(2.0, AeromanipTargeting.adjustControlDistance(2.5, -99, 1.0, 2.0, 10.0), 1.0e-9);
        assertEquals(10.0, AeromanipTargeting.adjustControlDistance(10.0, 1, 1.0, 2.0, 10.0), 1.0e-9);
    }
}
