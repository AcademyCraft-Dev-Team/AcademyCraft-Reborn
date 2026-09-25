package org.academy.internal.common.ability.darkmatter.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterSixWingsTest {
    @Test
    void reservationAndActivationMatchGammaContract() {
        assertEquals(120.0f, DarkmatterSixWings.MIN_RESERVED_CP, 0.0001f);
        assertEquals(10.0f, DarkmatterSixWings.ACTIVATION_MATTER_COST, 0.0001f);
    }

    @Test
    void secondMilestoneImprovesFlightAndAreaCombos() {
        assertEquals(0.05f, DarkmatterSixWings.Server.flightSpeed(1), 0.0001f);
        assertEquals(0.0575f, DarkmatterSixWings.Server.flightSpeed(2), 0.0001f);
        assertEquals(0.0805f, DarkmatterSixWings.Server.flightSpeed(5.0f, 2), 0.0001f);
        assertEquals(1.0, DarkmatterSixWings.Server.areaMultiplier(1), 0.0001);
        assertEquals(1.15, DarkmatterSixWings.Server.areaMultiplier(2), 0.0001);
    }

    @Test
    void thirdMilestoneChangesActivationAndGammaMagnitudeWithoutCategoryDiscount() {
        assertEquals(100.0f, DarkmatterSixWings.Server.adjustedCategoryCost(
                100.0f, 100.0f, 3), 0.0001f);
        assertEquals(70.0f, DarkmatterSixWings.Server.adjustedCategoryCost(
                100.0f, 70.0f, 3), 0.0001f);
        assertEquals(0.35f, DarkmatterSixWings.maintenanceRatio(0), 0.0001f);
        assertEquals(0.30f, DarkmatterSixWings.maintenanceRatio(1), 0.0001f);
        assertEquals(10.0f, DarkmatterSixWings.activationMatterCost(2), 0.0001f);
        assertEquals(5.0f, DarkmatterSixWings.activationMatterCost(3), 0.0001f);
        assertEquals(1.20f, DarkmatterSixWings.Server.gammaMagnitudeMultiplier(3), 0.0001f);
        assertEquals(0.05f, DarkmatterSixWings.Server.darkmatterPenetration(1.0f), 0.0001f);
        assertEquals(0.25f, DarkmatterSixWings.Server.darkmatterPenetration(5.0f), 0.0001f);
    }
}
