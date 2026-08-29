package org.academy.internal.common.ability.aeromanip.skills.lv4;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void entityNozzleFacesTowardThePlayer() {
        var direction = HighSpeedJet.towardPlayerDirection(
                new Vec3(0.0, 1.0, 0.0),
                new AABB(3.0, 0.0, -1.0, 5.0, 2.0, 1.0),
                Vec3.ZERO);
        assertEquals(-1.0, direction.x, 1.0e-9);
        assertEquals(0.0, direction.y, 1.0e-9);
        assertEquals(0.0, direction.z, 1.0e-9);
    }

    @Test
    void entityThrustRunsOppositeThePlayerFacingNozzle() {
        var thrust = HighSpeedJet.entityThrustDirection(new Vec3(-1.0, 0.0, 0.0));

        assertEquals(1.0, thrust.x, 1.0e-9);
        assertEquals(0.0, thrust.y, 1.0e-9);
        assertEquals(0.0, thrust.z, 1.0e-9);
    }

    @Test
    void temporaryBlockNozzleUsesTheDominantDirectionAsItsFace() {
        assertEquals(Direction.UP,
                HighSpeedJet.nearestFace(new Vec3(0.1, 0.9, -0.2)));
        assertEquals(Direction.WEST,
                HighSpeedJet.nearestFace(new Vec3(-4.0, 0.0, 1.0)));
        assertEquals(Direction.SOUTH,
                HighSpeedJet.nearestFace(new Vec3(0.0, -0.1, 2.0)));
    }

    @Test
    void nozzleExpiresOnlyAfterItsOwnerMovesBeyondSixtyFourBlocks() {
        var owner = new Vec3(0.0, 64.0, 0.0);

        assertFalse(HighSpeedJet.isOutsideNozzleRetentionRange(owner, Vec3.ZERO));
        assertTrue(HighSpeedJet.isOutsideNozzleRetentionRange(
                owner.add(0.0, 0.001, 0.0), Vec3.ZERO));
    }
}
