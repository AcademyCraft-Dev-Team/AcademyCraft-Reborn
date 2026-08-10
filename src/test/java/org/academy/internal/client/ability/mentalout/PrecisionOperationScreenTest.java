package org.academy.internal.client.ability.mentalout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionOperationScreenTest {
    @Test
    void durationInsertionOnlyAcceptsDigits() {
        assertTrue(PrecisionOperationScreen.isDurationInsertionAllowed(""));
        assertTrue(PrecisionOperationScreen.isDurationInsertionAllowed("0123"));
        assertFalse(PrecisionOperationScreen.isDurationInsertionAllowed("12a"));
        assertFalse(PrecisionOperationScreen.isDurationInsertionAllowed("-1"));
        assertFalse(PrecisionOperationScreen.isDurationInsertionAllowed("1.5"));
    }

    @Test
    void durationParserAcceptsPermanentAndInclusiveRange() {
        assertEquals(0, PrecisionOperationScreen.parseDurationSeconds("").orElseThrow());
        assertEquals(1, PrecisionOperationScreen.parseDurationSeconds("1").orElseThrow());
        assertEquals(3600, PrecisionOperationScreen.parseDurationSeconds("3600").orElseThrow());
    }

    @Test
    void durationParserRejectsOutOfRangeAndMalformedValues() {
        for (var value : new String[]{"0", "3601", "-1", "1.5", "text", "12a"}) {
            assertTrue(PrecisionOperationScreen.parseDurationSeconds(value).isEmpty(), value);
        }
    }
}
