package org.academy.api.common.entitycontrol;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class WorkBudgetTest {
    @Test void countsCannotResetWithinTheSameTick() {
        var budget = new WorkBudget(() -> 0, 100);
        budget.beginTick(10);
        for (int i = 0; i < 8; i++) assertTrue(budget.spend(WorkBudget.Operation.PATH));
        budget.beginTick(10);
        assertFalse(budget.spend(WorkBudget.Operation.PATH));
        budget.beginTick(11);
        assertTrue(budget.spend(WorkBudget.Operation.PATH));
    }
    @Test void nestedMeasurementsCountTimeOnceAndDeferFurtherOperations() {
        var clock = new AtomicLong();
        var budget = new WorkBudget(clock::get, 100);
        budget.beginTick(1);
        try (var outer = budget.measure()) {
            clock.set(30);
            try (var inner = budget.measure()) { clock.set(80); }
            assertTrue(budget.hasTime());
            clock.set(110);
            assertFalse(budget.spend(WorkBudget.Operation.ACTION));
        }
        assertEquals(110, budget.metrics().nanos());
        assertEquals(110, budget.metrics().longestOperationNanos());
        assertEquals(1, budget.metrics().deferred()[WorkBudget.Operation.ACTION.ordinal()]);
    }
    @Test void metricsDoNotExposeMutableCounters() {
        var budget = new WorkBudget();
        budget.beginTick(1);
        budget.spend(WorkBudget.Operation.BLOCK);
        var snapshot = budget.metrics();
        snapshot.used()[WorkBudget.Operation.BLOCK.ordinal()] = 500;
        assertEquals(1, snapshot.used()[WorkBudget.Operation.BLOCK.ordinal()]);
    }
}
