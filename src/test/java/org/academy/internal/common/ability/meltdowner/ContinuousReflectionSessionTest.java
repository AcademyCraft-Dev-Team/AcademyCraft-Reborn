package org.academy.internal.common.ability.meltdowner;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ContinuousReflectionSessionTest {
    @Test
    void firstContactCoversTheFirstPulseWithoutDoubleCharging() {
        var session = new ContinuousReflectionSession();
        var reflector = UUID.randomUUID();
        var payments = new AtomicInteger();

        assertTrue(session.activate(reflector, 3, 20, 20, () -> {
            payments.incrementAndGet();
            return true;
        }));
        assertTrue(session.renewIfDue(reflector, 20, 20, () -> {
            payments.incrementAndGet();
            return true;
        }));

        assertEquals(1, payments.get());
        assertEquals(40, session.nextChargeTick());
    }

    @Test
    void sameReflectorReusesLeaseButReplacementPaysAgain() {
        var session = new ContinuousReflectionSession();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var payments = new AtomicInteger();

        assertTrue(session.activate(first, 1, 10, 10, () -> {
            payments.incrementAndGet();
            return true;
        }));
        assertTrue(session.activate(first, 8, 10, 10, () -> {
            payments.incrementAndGet();
            return true;
        }));
        assertTrue(session.activate(second, 8, 10, 10, () -> {
            payments.incrementAndGet();
            return true;
        }));

        assertEquals(2, payments.get());
        assertTrue(session.isActiveFor(second));
    }

    @Test
    void paidLeaseRemainsEligibleWhenCurrentCpCannotStartANewReflection() {
        var session = new ContinuousReflectionSession();
        var reflector = UUID.randomUUID();
        var payments = new AtomicInteger();

        assertTrue(session.activate(reflector, 1, 20, 20, () -> {
            payments.incrementAndGet();
            return true;
        }));
        assertTrue(ContinuousBeamReflection.isCandidateEligible(
                reflector,
                reflector,
                false,
                true
        ));
        assertTrue(session.activate(reflector, 10, 20, 20, () -> {
            payments.incrementAndGet();
            return false;
        }));

        assertEquals(1, payments.get());
        assertEquals(40, session.nextChargeTick());
    }

    @Test
    void zeroCpEligibilityOnlyAppliesToThePaidMaintainableReflector() {
        var leasedReflector = UUID.randomUUID();

        assertTrue(ContinuousBeamReflection.isCandidateEligible(
                leasedReflector,
                UUID.randomUUID(),
                true,
                false
        ));
        assertFalse(ContinuousBeamReflection.isCandidateEligible(
                leasedReflector,
                UUID.randomUUID(),
                false,
                true
        ));
        assertFalse(ContinuousBeamReflection.isCandidateEligible(
                leasedReflector,
                leasedReflector,
                false,
                false
        ));
        assertFalse(ContinuousBeamReflection.isCandidateEligible(
                null,
                leasedReflector,
                false,
                true
        ));
    }

    @Test
    void failedRenewalClearsSession() {
        var session = new ContinuousReflectionSession();
        var reflector = UUID.randomUUID();

        assertTrue(session.activate(reflector, 1, 10, 10, () -> true));
        assertFalse(session.renewIfDue(reflector, 20, 10, () -> false));
        assertFalse(session.isActiveFor(reflector));
    }

    @Test
    void failedReplacementPaymentClearsThePreviousLease() {
        var session = new ContinuousReflectionSession();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        assertTrue(session.activate(first, 1, 10, 10, () -> true));
        assertFalse(session.activate(second, 2, 10, 10, () -> false));

        assertFalse(session.isActiveFor(first));
        assertFalse(session.isActiveFor(second));
    }

    @Test
    void chargeDeadlineSaturatesAtLongMaxValue() {
        var session = new ContinuousReflectionSession();

        assertTrue(session.activate(
                UUID.randomUUID(),
                Long.MAX_VALUE - 10,
                Long.MAX_VALUE - 5,
                20,
                () -> true
        ));

        assertEquals(Long.MAX_VALUE, session.nextChargeTick());
        assertFalse(session.isExpired(Long.MAX_VALUE - 1));
        assertTrue(session.isExpired(Long.MAX_VALUE));
    }

    @Test
    void nextPulseUsesTheCurrentPulseWhenAlreadyAligned() {
        assertEquals(20, ContinuousBeamReflection.nextPulse(20, 20));
        assertEquals(20, ContinuousBeamReflection.nextPulse(1, 20));
        assertEquals(40, ContinuousBeamReflection.nextPulse(21, 20));
    }
}
