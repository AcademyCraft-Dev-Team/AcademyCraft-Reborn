package org.academy.internal.common.ability.accelerator.skills.lv4;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorReflectionTest {
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
        assertEquals(1.2f, VectorReflection.Server.calculateReflectedDamage(10, 12, 3, false));
        assertEquals(10.0f, VectorReflection.Server.calculateReflectedDamage(10, 100, 3, false));
        assertEquals(0.0f, VectorReflection.Server.calculateReflectedDamage(10, 0, 3, false));
        assertEquals(10.0f, VectorReflection.Server.calculateReflectedDamage(10, 0, 3, true));
    }

    @Test
    void preservesUnreflectedDamageWhenComputingPowerIsInsufficient() {
        var partial = VectorReflection.Server.calculateReflection(10.0f, 12.0f, 3.0f, false);
        assertEquals(1.2f, partial.reflectedDamage(), 1.0E-6f);
        assertEquals(8.8f, partial.remainingDamage(), 1.0E-6f);
        assertEquals(4.0f, partial.baseCpCost(), 1.0E-6f);

        var full = VectorReflection.Server.calculateReflection(10.0f, 30.0f, 3.0f, false);
        assertEquals(10.0f, full.reflectedDamage(), 1.0E-6f);
        assertEquals(0.0f, full.remainingDamage(), 1.0E-6f);
        assertEquals(10.0f, full.baseCpCost(), 1.0E-6f);
    }

    @Test
    void forcedMovementUsesTheProjectileReflectionMinimumCost() {
        assertEquals(1.5f, VectorReflection.Server.projectileReflectionCost(0.0), 1.0E-6f);
        assertEquals(1.5f, VectorReflection.Server.projectileReflectionCost(1.0), 1.0E-6f);
        assertEquals(3.25f, VectorReflection.Server.projectileReflectionCost(3.25), 1.0E-6f);
        assertEquals(1.5f,
                VectorReflection.Server.projectileReflectionCost(Double.NaN), 1.0E-6f);
    }
}
