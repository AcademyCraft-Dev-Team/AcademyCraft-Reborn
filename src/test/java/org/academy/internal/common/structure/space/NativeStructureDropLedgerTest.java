package org.academy.internal.common.structure.space;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.academy.internal.common.structure.space.NativeStructureDropLedger.State.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeStructureDropLedgerTest {
    @Test
    void rejectedSecondOutputRetainsThirdAndNeverReplaysFirst() {
        var ledger = new NativeStructureDropLedger<Object>(4);
        var first = new Object(); var second = new Object(); var third = new Object();
        ledger.capture(first); ledger.capture(second); ledger.capture(third);
        var attempted = new ArrayList<Object>();
        assertThrows(IllegalStateException.class, () -> ledger.publish(value -> {
            attempted.add(value);
            return value != second;
        }));
        assertEquals(List.of(first, second), attempted);
        assertEquals(List.of(PUBLISHED, REJECTED, PENDING), states(ledger));
        assertThrows(IllegalStateException.class, () -> ledger.publish(value -> { attempted.add(value); return true; }));
        assertEquals(List.of(first, second), attempted);
        assertSame(third, ledger.entries().get(2).value());
    }

    @Test
    void throwingPublisherKeepsAmbiguousOutputSeparateFromUnattemptedOutput() {
        var ledger = new NativeStructureDropLedger<Object>(3);
        var first = new Object(); var second = new Object();
        ledger.capture(first); ledger.capture(second);
        assertThrows(IllegalArgumentException.class, () -> ledger.publish(value -> {
            throw new IllegalArgumentException("Entity callback failed after possible insertion");
        }));
        assertEquals(List.of(UNCERTAIN, PENDING), states(ledger));
        assertSame(first, ledger.entries().getFirst().value());
        assertSame(second, ledger.entries().getLast().value());
    }

    @Test
    void budgetFailureRetainsTheAlreadySplitTriggeringStack() {
        var ledger = new NativeStructureDropLedger<Object>(1);
        var first = new Object(); var overflow = new Object();
        ledger.capture(first);
        assertThrows(IllegalStateException.class, () -> ledger.capture(overflow));
        assertEquals(2, ledger.entries().size());
        assertSame(overflow, ledger.entries().getLast().value());
        assertEquals(List.of(PENDING, PENDING), states(ledger));
        assertThrows(IllegalStateException.class, () -> ledger.capture(first));
        assertEquals(2, ledger.entries().size(), "A duplicate identity is not another owned stack");
        assertThrows(IllegalStateException.class, () -> ledger.publish(value -> fail("Over-budget outputs must stay in recovery")));
    }

    private static List<NativeStructureDropLedger.State> states(NativeStructureDropLedger<?> ledger) {
        return ledger.entries().stream().map(NativeStructureDropLedger.Entry::state).toList();
    }
}
