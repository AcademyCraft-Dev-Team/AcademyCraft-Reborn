package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.input.InputSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void entitiesWithoutAnAirPoolStillReceiveAeroDamage() {
        assertEquals(0, VacuumDomain.airSupplyInVacuum(false, 0));
        assertEquals(1.0f, VacuumDomain.baseDamage(20.0f, false));
    }

    @Test
    void aeroDamageStartsWithoutWaitingForTheProjectileWarmup() {
        assertFalse(VacuumDomain.shouldDealDamage(2));
        assertTrue(VacuumDomain.shouldDealDamage(10));
        assertTrue(VacuumDomain.shouldDealDamage(40));
    }

    @Test
    void allVisualRingsStayOnTheSphericalBoundary() {
        var center = new Vec3(3.0, -2.0, 7.0);
        var radius = 12.0;

        for (var ring = 0; ring < 3; ring++) {
            for (var segment = 0; segment < 16; segment++) {
                var point = VacuumDomain.boundaryPoint(
                        center, radius, ring, segment * Math.PI * 2.0 / 16.0
                );
                assertEquals(radius, point.distanceTo(center), 1.0e-9);
            }
        }
    }

    @Test
    void legacySinglePhaseBindingMigratesToPressAndRelease() {
        var legacy = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_Y,
                InputConstants.RELEASE,
                0
        );

        var migrated = VacuumDomain.maintainedBinding(legacy);

        assertEquals(InputSystem.ANY_ACTION, migrated.action());
        assertEquals(legacy.keys(), migrated.keys());
        assertEquals(legacy.modifiers(), migrated.modifiers());
    }
}
