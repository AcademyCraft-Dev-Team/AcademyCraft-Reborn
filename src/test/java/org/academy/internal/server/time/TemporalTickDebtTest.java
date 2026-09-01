package org.academy.internal.server.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporalTickDebtTest {
    private static final long TICK = 50L;

    @Test
    void normalTickProgressPaysElapsedDebt() {
        var debt = new TemporalTickDebt(TICK, 1_000L, 2, 0L, 10);

        assertEquals(0, debt.update(50L, 11));
        assertEquals(0L, debt.debtNanos());
    }

    @Test
    void stalledTicksAreBoundedPerPassAndRemainAsDebt() {
        var debt = new TemporalTickDebt(TICK, 1_000L, 2, 0L, 10);

        assertEquals(2, debt.update(130L, 10));
        debt.consumeForcedTick(11);
        debt.consumeForcedTick(12);

        assertEquals(30L, debt.debtNanos());
    }

    @Test
    void longFreezeIsCapped() {
        var debt = new TemporalTickDebt(TICK, 1_000L, 2, 0L, 10);

        assertEquals(2, debt.update(10_000L, 10));
        assertEquals(1_000L, debt.debtNanos());
    }
}
