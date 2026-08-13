package org.academy.internal.client.ability.mentalout;

import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraphCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionOperationScreenTest {
    @AfterEach
    void resetPrecisionSession() {
        PrecisionOperationClient.resetSession();
    }

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

    @Test
    void logoutClearsWorldSpecificSlotCache() {
        var graph = new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(
                                1,
                                PrecisionGraph.NodeKind.ROSTER,
                                0.0,
                                8.0,
                                8.0
                        ),
                        new PrecisionGraph.Node(
                                2,
                                PrecisionGraph.NodeKind.MENTAL_STUPOR,
                                0.0,
                                24.0,
                                8.0
                        )
                ),
                List.of(new PrecisionGraph.Edge(1, 0, 2, 0))
        );
        var encoded = new byte[][]{
                PrecisionGraphCodec.encode(PrecisionGraph.EMPTY),
                PrecisionGraphCodec.encode(graph),
                PrecisionGraphCodec.encode(PrecisionGraph.EMPTY),
                PrecisionGraphCodec.encode(PrecisionGraph.EMPTY)
        };
        PrecisionOperationClient.handleSync(9L, encoded);

        assertEquals(graph, PrecisionOperationClient.graph(1));
        assertEquals(graph, PrecisionOperationClient.serverGraph(1));
        assertEquals(9L, PrecisionOperationClient.revision());

        PrecisionOperationClient.resetSession();

        assertEquals(PrecisionGraph.EMPTY, PrecisionOperationClient.graph(1));
        assertEquals(PrecisionGraph.EMPTY, PrecisionOperationClient.serverGraph(1));
        assertEquals(0L, PrecisionOperationClient.revision());
    }
}
