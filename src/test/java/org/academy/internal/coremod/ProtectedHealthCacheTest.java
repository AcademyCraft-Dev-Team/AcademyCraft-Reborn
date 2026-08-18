package org.academy.internal.coremod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtectedHealthCacheTest {
    @Test
    void fullHealthTracksLegitimateMaximumIncrease() {
        var initial = ProtectedHealthCache.reconcile(0L, false, 20.0f, 20.0f);
        var increased = ProtectedHealthCache.reconcile(initial, true, 20.0f, 40.0f);

        assertEquals(40.0f, ProtectedHealthCache.health(increased));
        assertEquals(40.0f, ProtectedHealthCache.maxHealth(increased));
    }

    @Test
    void damagedHealthDoesNotReceiveFreeHealingFromMaximumIncrease() {
        var initial = ProtectedHealthCache.reconcile(0L, false, 12.0f, 20.0f);
        var increased = ProtectedHealthCache.reconcile(initial, true, 12.0f, 40.0f);

        assertEquals(12.0f, ProtectedHealthCache.health(increased));
        assertEquals(40.0f, ProtectedHealthCache.maxHealth(increased));
    }

    @Test
    void maximumDecreaseClampsProtectedHealth() {
        var initial = ProtectedHealthCache.reconcile(0L, false, 40.0f, 40.0f);
        var decreased = ProtectedHealthCache.reconcile(initial, true, 40.0f, 24.0f);

        assertEquals(24.0f, ProtectedHealthCache.health(decreased));
        assertEquals(24.0f, ProtectedHealthCache.maxHealth(decreased));
    }

    @Test
    void imagineBreakerDamageUsesReconciledRaisedMaximum() {
        var initial = ProtectedHealthCache.reconcile(0L, false, 20.0f, 20.0f);
        var increased = ProtectedHealthCache.reconcile(initial, true, 20.0f, 40.0f);
        var damaged = ProtectedHealthCache.subtract(increased, 5.0f);

        assertEquals(35.0f, ProtectedHealthCache.health(damaged));
        assertEquals(40.0f, ProtectedHealthCache.maxHealth(damaged));
    }
}
