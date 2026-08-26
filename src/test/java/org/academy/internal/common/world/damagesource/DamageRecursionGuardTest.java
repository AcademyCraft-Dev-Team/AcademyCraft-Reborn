package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DamageRecursionGuardTest {
    @Test
    void blocksSameCallbackAcrossNestedThirdPartyDamageCalls() {
        var key = new Object();
        var calls = new AtomicInteger();

        assertTrue(DamageRecursionGuard.runGuarded(key, () -> {
            calls.incrementAndGet();
            assertTrue(DamageRecursionGuard.isActive(key));
            assertFalse(DamageRecursionGuard.runGuarded(key, calls::incrementAndGet));
        }));

        assertEquals(1, calls.get());
        assertFalse(DamageRecursionGuard.isActive(key));
    }

    @Test
    void independentDamageCallbacksCanStillNest() {
        var outer = new Object();
        var inner = new Object();
        var calls = new AtomicInteger();

        assertTrue(DamageRecursionGuard.runGuarded(outer, () -> {
            calls.incrementAndGet();
            assertTrue(DamageRecursionGuard.runGuarded(inner, calls::incrementAndGet));
        }));

        assertEquals(2, calls.get());
    }

    @Test
    void exceptionsAlwaysReleaseTheGuard() {
        var key = new Object();

        assertThrows(IllegalStateException.class, () -> DamageRecursionGuard.runGuarded(
                key,
                () -> {
                    throw new IllegalStateException("damage callback failed");
                }
        ));

        assertFalse(DamageRecursionGuard.isActive(key));
        assertTrue(DamageRecursionGuard.runGuarded(key, () -> {
        }));
    }
}
