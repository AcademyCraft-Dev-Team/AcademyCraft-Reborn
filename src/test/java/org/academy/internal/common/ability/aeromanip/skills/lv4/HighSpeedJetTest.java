package org.academy.internal.common.ability.aeromanip.skills.lv4;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighSpeedJetTest {
    @Test
    void milestonesExpandNozzleCapacityAndDuration() {
        assertEquals(8, HighSpeedJet.maximumNozzles(0));
        assertEquals(12, HighSpeedJet.maximumNozzles(1));
        assertEquals(40, HighSpeedJet.activationDuration(1));
        assertEquals(60, HighSpeedJet.activationDuration(2));
    }

    @Test
    void activationCostsScaleWithTheNumberOfNozzles() {
        assertEquals(8.0f, HighSpeedJet.activationCpCost(0));
        assertEquals(16.0f, HighSpeedJet.activationCpCost(4));
        assertEquals(32.0f, HighSpeedJet.activationAirCost(4));
        assertEquals(0.0f, HighSpeedJet.activationAirCost(-1));
    }

    @Test
    void entityNozzleDefaultsToTheDirectionAwayFromThePlayer() {
        var direction = HighSpeedJet.outwardDirection(
                new Vec3(0.0, 1.0, 0.0),
                new AABB(3.0, 0.0, -1.0, 5.0, 2.0, 1.0),
                Vec3.ZERO);
        assertEquals(1.0, direction.x, 1.0e-9);
        assertEquals(0.0, direction.y, 1.0e-9);
        assertEquals(0.0, direction.z, 1.0e-9);
    }
}
