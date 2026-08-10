package org.academy.internal.client.ability.mentalout;

import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionOperationScreenTest {
    @Test
    void numericInsertionOnlyAcceptsDigits() {
        assertTrue(PrecisionOperationScreen.isNumericInsertionAllowed(""));
        assertTrue(PrecisionOperationScreen.isNumericInsertionAllowed("0123"));
        assertFalse(PrecisionOperationScreen.isNumericInsertionAllowed("12a"));
        assertFalse(PrecisionOperationScreen.isNumericInsertionAllowed("-1"));
        assertFalse(PrecisionOperationScreen.isNumericInsertionAllowed("1.5"));
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

    @Test
    void rangeParserUsesThirtyTwoAsTheEmptyDefault() {
        assertEquals(32, PrecisionOperationScreen.parseRange("").orElseThrow());
        assertEquals(1, PrecisionOperationScreen.parseRange("1").orElseThrow());
        assertEquals(32, PrecisionOperationScreen.parseRange("32").orElseThrow());
        for (var value : new String[]{"0", "33", "-1", "1.5", "text"}) {
            assertTrue(PrecisionOperationScreen.parseRange(value).isEmpty(), value);
        }
    }

    @Test
    void inspectorReservesSpaceOnlyForVisibleParameterEditors() {
        assertEquals(0, PrecisionOperationScreen.parameterEditorHeight(PrecisionGraph.ParameterKind.NONE));
        for (var kind : PrecisionGraph.ParameterKind.values()) {
            if (kind != PrecisionGraph.ParameterKind.NONE) {
                assertEquals(32, PrecisionOperationScreen.parameterEditorHeight(kind), kind.name());
            }
        }
    }
}
