package org.academy.internal.common.ability.aeromanip;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeromanipDisplacementTrackerTest {
    @Test
    void cavitationScalesWithTravelAndMilestones() {
        assertEquals(2.0f, AeromanipDisplacementTracker.damageForDistance(2.0, 0), 1.0e-6f);
        assertEquals(4.0f, AeromanipDisplacementTracker.damageForDistance(2.0, 1), 1.0e-6f);
        assertEquals(24, AeromanipDisplacementTracker.armorWearForDistance(2.0, 0));
        assertEquals(36, AeromanipDisplacementTracker.armorWearForDistance(2.0, 2));
    }

    @Test
    void accountableDistanceRejectsInvalidValuesAndCapsTeleportSpikes() {
        assertEquals(3.0, AeromanipDisplacementTracker.accountableDistance(
                Vec3.ZERO, new Vec3(100.0, 0.0, 0.0), 0));
        assertEquals(4.0, AeromanipDisplacementTracker.accountableDistance(
                Vec3.ZERO, new Vec3(100.0, 0.0, 0.0), 3));
        assertEquals(0.0, AeromanipDisplacementTracker.accountableDistance(
                null, Vec3.ZERO, 0));
    }
}
