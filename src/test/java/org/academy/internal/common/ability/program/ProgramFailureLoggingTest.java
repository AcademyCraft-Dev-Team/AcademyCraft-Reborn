package org.academy.internal.common.ability.program;

import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramActionRejection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgramFailureLoggingTest {
    @Test
    void settlementWrappersDoNotSuppressDifferentRootCausesOrReasons() {
        var program = UUID.randomUUID();
        assertEquals(0, record(program, new IllegalStateException("preparing"), 100));
        assertEquals(-1, record(program, new IllegalStateException("preparing"), 101));
        assertEquals(0, record(program, new IllegalArgumentException("preparing"), 102));
        assertEquals(0, record(program, new IllegalStateException("conflicting inputs"), 103));
        assertEquals(-1, record(program, new IllegalStateException("conflicting inputs"), 104));
        assertEquals(1, record(program, new IllegalStateException("preparing"), 1_300));
    }

    @Test
    void stableTypedReasonsDistinguishRejectionsWithoutFragmentingOnNativeMessages() {
        var program = UUID.randomUUID();
        assertEquals(0, record(program, new ProgramActionRejection(
                ProgramActionRejection.Reason.PREPARING, new IllegalStateException("native detail one")), 100));
        assertEquals(-1, record(program, new ProgramActionRejection(
                ProgramActionRejection.Reason.PREPARING, new IllegalStateException("native detail two")), 101));
        assertEquals(0, record(program, new ProgramActionRejection(
                ProgramActionRejection.Reason.CONFLICTING_INPUTS,
                new IllegalStateException("native detail two")), 102));
    }

    @Test
    void retainedNativeMessageTokensAreBounded() {
        var program = UUID.randomUUID();
        var prefix = "native detail ".repeat(40);
        assertEquals(0, record(program, new IllegalStateException(prefix + "first"), 100));
        assertEquals(-1, record(program, new IllegalStateException(prefix + "second"), 101));
    }

    private static long record(UUID program, RuntimeException cause, long tick) {
        var transaction = ProgramActionTransaction.sequential();
        var wrapped = assertThrows(ProgramActionTransaction.SettlementException.class,
                () -> transaction.stage(2, () -> {
                    throw cause;
                }));
        return ProgramVm.recordFailure(program, 2, AcademyCraft.academy("test/action"), wrapped, tick);
    }
}
