package org.academy.internal.common.ability.aeromanip.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacuumDomainTest {
    @Test
    void baseDomainIsFiftyBlocksAndMilestoneThreeDoublesIt() {
        assertEquals(50.0, VacuumDomain.radiusForMilestone(0));
        assertEquals(50.0, VacuumDomain.radiusForMilestone(2));
        assertEquals(100.0, VacuumDomain.radiusForMilestone(3));
    }

    @Test
    void includesTheSphereBoundaryButRejectsOutsideTargets() {
        var center = new Vec3(4, 8, 12);

        assertTrue(VacuumDomain.isInsideDomain(
                center, center.add(VacuumDomain.RADIUS, 0, 0), VacuumDomain.RADIUS));
        assertFalse(VacuumDomain.isInsideDomain(
                center, center.add(VacuumDomain.RADIUS + 0.01, 0, 0), VacuumDomain.RADIUS));
    }

    @Test
    void pulsesRapidlyDepleteAirButStopAtTheDrowningThreshold() {
        assertEquals(260, VacuumDomain.airSupplyAfterPulse(300, 300, 40, false));
        assertEquals(-20, VacuumDomain.airSupplyAfterPulse(10, 300, 40, false));
        assertEquals(-20, VacuumDomain.airSupplyAfterPulse(-20, 300, 40, false));
    }

    @Test
    void breathingBubbleRestoresTheTargetsAir() {
        assertEquals(300, VacuumDomain.airSupplyAfterPulse(-20, 300, 40, true));
    }

    @Test
    void percentDamageBeginsOnlyAfterSuffocation() {
        assertFalse(VacuumDomain.shouldDealDamage(10, 0));
        assertFalse(VacuumDomain.shouldDealDamage(9, -20));
        assertTrue(VacuumDomain.shouldDealDamage(10, -20));
        assertEquals(5.0f, VacuumDomain.baseDamage(100.0f));
    }
}
