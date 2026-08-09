package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacuumDomainTest {
    @Test
    void includesTheSphereBoundaryButRejectsOutsideTargets() {
        var center = new Vec3(4, 8, 12);

        assertTrue(VacuumDomain.isInsideDomain(center, center));
        assertTrue(VacuumDomain.isInsideDomain(center, center.add(VacuumDomain.RADIUS, 0, 0)));
        assertFalse(VacuumDomain.isInsideDomain(center, center.add(VacuumDomain.RADIUS + 0.01, 0, 0)));
    }

    @Test
    void vacuumClearsAirUnlessBreathingFilmProtectsTheTarget() {
        assertEquals(0, VacuumDomain.airSupplyInVacuum(false, 300));
        assertEquals(300, VacuumDomain.airSupplyInVacuum(true, 300));
    }

    @Test
    void percentImmuneTargetsStillTakeFallbackDamage() {
        assertEquals(5.0f, VacuumDomain.baseDamage(100.0f, false));
        assertEquals(1.0f, VacuumDomain.baseDamage(100.0f, true));
    }
}
