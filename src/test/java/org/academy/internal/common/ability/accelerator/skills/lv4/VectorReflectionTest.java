package org.academy.internal.common.ability.accelerator.skills.lv4;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorReflectionTest {
    @Test
    void requiresEnoughCpToPayMaintenanceWithoutDepletingCp() {
        assertFalse(VectorReflection.Server.hasSufficientCpToEnable(44.0f, 50.0f, 0.9f));
        assertFalse(VectorReflection.Server.hasSufficientCpToEnable(45.0f, 50.0f, 0.9f));
        assertTrue(VectorReflection.Server.hasSufficientCpToEnable(45.01f, 50.0f, 0.9f));
        assertFalse(VectorReflection.Server.hasSufficientCpToEnable(
                Float.NaN, 50.0f, 0.9f));
    }

    @Test
    void reversesAndAcceleratesProjectileVelocity() {
        var reflected = VectorReflection.Server.reflectedVelocity(new Vec3(1, -2, 3));
        assertEquals(-1.2, reflected.x, 1.0E-6);
        assertEquals(2.4, reflected.y, 1.0E-6);
        assertEquals(-3.6, reflected.z, 1.0E-6);
        assertEquals(Vec3.ZERO, VectorReflection.Server.reflectedVelocity(Vec3.ZERO));
    }

    @Test
    void limitsReflectedDamageByAvailableComputingPower() {
        assertEquals(8.0f, VectorReflection.Server.calculateReflectedDamage(10, 12, 3, false));
        assertEquals(10.0f, VectorReflection.Server.calculateReflectedDamage(10, 100, 3, false));
        assertEquals(0.0f, VectorReflection.Server.calculateReflectedDamage(10, 0, 3, false));
        assertEquals(10.0f, VectorReflection.Server.calculateReflectedDamage(10, 0, 3, true));
    }

    @Test
    void preservesUnreflectedDamageWhenComputingPowerIsInsufficient() {
        var partial = VectorReflection.Server.calculateReflection(10.0f, 12.0f, 3.0f, false);
        assertEquals(8.0f, partial.reflectedDamage(), 1.0E-6f);
        assertEquals(2.0f, partial.remainingDamage(), 1.0E-6f);
        assertEquals(4.0f, partial.baseCpCost(), 1.0E-6f);

        var full = VectorReflection.Server.calculateReflection(10.0f, 30.0f, 3.0f, false);
        assertEquals(10.0f, full.reflectedDamage(), 1.0E-6f);
        assertEquals(0.0f, full.remainingDamage(), 1.0E-6f);
        assertEquals(5.0f, full.baseCpCost(), 1.0E-6f);
    }

    @Test
    void forcedMovementUsesTheProjectileReflectionMinimumCost() {
        assertEquals(1.5f, VectorReflection.Server.projectileReflectionCost(0.0), 1.0E-6f);
        assertEquals(1.5f, VectorReflection.Server.projectileReflectionCost(1.0), 1.0E-6f);
        assertEquals(3.25f, VectorReflection.Server.projectileReflectionCost(3.25), 1.0E-6f);
        assertEquals(1.5f,
                VectorReflection.Server.projectileReflectionCost(Double.NaN), 1.0E-6f);
    }

    @Test
    void depletedOrInvalidCpRequiresSynchronousProtectionShutdown() {
        assertFalse(VectorReflection.Server.isComputingPowerDepleted(0.01f));
        assertTrue(VectorReflection.Server.isComputingPowerDepleted(0.00001f));
        assertTrue(VectorReflection.Server.isComputingPowerDepleted(0.0f));
        assertTrue(VectorReflection.Server.isComputingPowerDepleted(-1.0f));
        assertTrue(VectorReflection.Server.isComputingPowerDepleted(Float.NaN));
    }

}
