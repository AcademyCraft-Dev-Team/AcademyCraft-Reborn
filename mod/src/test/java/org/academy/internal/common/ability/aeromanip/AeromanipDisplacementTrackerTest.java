package org.academy.internal.common.ability.aeromanip;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeromanipDisplacementTrackerTest {
    @Test
    void cavitationCombinesTravelBeforeApplyingPointTwoBlockSteps() {
        assertEquals(0.0f, AeromanipDisplacementTracker.damageForDistance(0.19), 1.0e-6f);
        assertEquals(2.0f, AeromanipDisplacementTracker.damageForDistance(0.2), 1.0e-6f);
        assertEquals(20.0f, AeromanipDisplacementTracker.damageForDistance(2.0), 1.0e-6f);
    }

    @Test
    void settlementAddsTheStrongestCollisionSpeed() {
        assertEquals(20.0f,
                AeromanipDisplacementTracker.settlementDamage(2.0, 1.25), 1.0e-6f);
        assertEquals(20.0f,
                AeromanipDisplacementTracker.settlementDamage(2.0, Double.NaN), 1.0e-6f);
    }

    @Test
    void shortMovementsCarryOverAcrossSettlementBoundaries() {
        var carried = AeromanipDisplacementTracker.distanceRemainder(0.15);
        assertEquals(0.15, carried, 1.0e-9);
        assertEquals(2.0f, AeromanipDisplacementTracker.damageForDistance(carried + 0.1));
        assertEquals(0.05, AeromanipDisplacementTracker.distanceRemainder(carried + 0.1), 1.0e-9);
        assertEquals(0.0, AeromanipDisplacementTracker.distanceRemainder(0.2), 1.0e-9);
    }

    @Test
    void longPullsAndCollisionsCannotExceedOneBatchLimit() {
        assertEquals(20.0f, AeromanipDisplacementTracker.settlementDamage(200.0, 100.0));
        assertEquals(3.25f, AeromanipDisplacementTracker.settlementDamage(0.2, 1.25));
    }

    @Test
    void armorWearUsesTheCombinedTenTickDistanceAndRetainsMilestones() {
        assertEquals(24, AeromanipDisplacementTracker.armorWearForDistance(2.0, 0));
        assertEquals(36, AeromanipDisplacementTracker.armorWearForDistance(2.0, 2));
        assertEquals(40, AeromanipDisplacementTracker.armorWearForDistance(10.0, 2));
        assertEquals(64, AeromanipDisplacementTracker.armorWearForDistance(10.0, 3));
    }

    @Test
    void collisionSpeedOnlyCountsBlockedMotionAlongTheAirflowForce() {
        assertEquals(1.2, AeromanipDisplacementTracker.collisionSpeed(
                new Vec3(1.2, 0.0, 0.0),
                Vec3.ZERO,
                new Vec3(0.4, 0.0, 0.0),
                true,
                false
        ), 1.0e-6);
        assertEquals(0.0, AeromanipDisplacementTracker.collisionSpeed(
                new Vec3(1.0, 0.0, 1.0),
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(0.0, 0.0, 0.4),
                true,
                false
        ), 1.0e-6);
        assertEquals(0.0, AeromanipDisplacementTracker.collisionSpeed(
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO,
                true,
                false
        ), 1.0e-6);
    }
}
