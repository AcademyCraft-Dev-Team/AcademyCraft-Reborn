package org.academy.internal.common.ability.darkmatter.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterCreationTest {
    @Test
    void contractLimitsAndReservationMatchAudit() {
        assertEquals(16, DarkmatterCreation.MAX_CREATURES);
        assertEquals(16, DarkmatterCreation.MAX_BEETLES);
        assertEquals(5.0f, DarkmatterCreation.MIN_INVESTMENT, 0.0001f);
        assertEquals(40.0f, DarkmatterCreation.RESERVED_CP_PER_BEETLE, 0.0001f);
    }

    @Test
    void secondMilestoneImprovesPursuitWithoutRaisingCountCap() {
        assertEquals(1.0, DarkmatterCreation.followSpeed(1), 0.0001);
        assertEquals(1.2, DarkmatterCreation.followSpeed(2), 0.0001);
        assertEquals(1.0, DarkmatterCreation.targetingRange(1), 0.0001);
        assertEquals(1.2, DarkmatterCreation.targetingRange(2), 0.0001);
        assertEquals(100, DarkmatterCreation.stuckTeleportTicks(1));
        assertEquals(60, DarkmatterCreation.stuckTeleportTicks(2));
    }

    @Test
    void thirdMilestoneBoostsModulesAndGammaRepeatOnly() {
        assertEquals(1.25f, DarkmatterCreation.moduleValueMultiplier(3), 0.0001f);
        assertEquals(100, DarkmatterCreation.gammaRepeatTicks(2));
        assertEquals(80, DarkmatterCreation.gammaRepeatTicks(3));
        assertEquals(1.0f, DarkmatterCreation.swarmDamageMultiplier(3, 8), 0.0001f);
        org.junit.jupiter.api.Assertions.assertFalse(DarkmatterCreation.unlocksSwarmCommand(3));
    }
}
