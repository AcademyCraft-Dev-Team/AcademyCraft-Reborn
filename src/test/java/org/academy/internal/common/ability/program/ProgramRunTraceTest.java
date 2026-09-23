package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgramRunTraceTest {
    @Test
    void boundsLoopTraceAndSanitizesDetails() {
        var trace = new ProgramRunTrace();
        for (var index = 0; index < ProgramRunTrace.MAX_STEPS + 3; index++) {
            trace.record(index, "next", "line one\nline two " + "x".repeat(200));
        }
        assertEquals(ProgramRunTrace.MAX_STEPS, trace.steps().size());
        assertTrue(trace.truncated());
        assertFalse(trace.steps().getFirst().detail().contains("\n"));
        assertTrue(trace.steps().getFirst().detail().length() <= ProgramRunTrace.MAX_DETAIL_LENGTH);
    }
}
