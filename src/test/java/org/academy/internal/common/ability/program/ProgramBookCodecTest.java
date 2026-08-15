package org.academy.internal.common.ability.program;

import com.google.gson.JsonParser;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramBookCodecTest {
    @Test
    void roundTripsSemanticGraphConfigurationAndEditorLayout() {
        var program = program(Map.of(
                9, new ProgramEditorLayout.NodePosition(48.5, -2.0),
                4, new ProgramEditorLayout.NodePosition(-16.0, 32.25)
        ));
        var book = new ProgramBook(
                ProgramBook.CURRENT_SCHEMA_VERSION,
                27,
                2,
                List.of(
                        ProgramBook.Slot.EMPTY,
                        new ProgramBook.Slot(program),
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY
                )
        );

        var decoded = ProgramBookCodec.decode(ProgramBookCodec.encode(book));

        assertTrue(decoded.valid());
        assertEquals(book, decoded.book());
    }

    @Test
    void layoutMapOrderDoesNotChangeWireRepresentation() {
        var forward = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        forward.put(4, new ProgramEditorLayout.NodePosition(-16.0, 32.25));
        forward.put(9, new ProgramEditorLayout.NodePosition(48.5, -2.0));
        var reverse = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        reverse.put(9, new ProgramEditorLayout.NodePosition(48.5, -2.0));
        reverse.put(4, new ProgramEditorLayout.NodePosition(-16.0, 32.25));

        assertArrayEquals(
                ProgramBookCodec.encodeProgram(program(forward)),
                ProgramBookCodec.encodeProgram(program(reverse))
        );
    }

    @Test
    void rejectsTruncatedTrailingOversizedAndUnsupportedData() {
        var encoded = ProgramBookCodec.encode(ProgramBook.empty(4));
        var truncated = Arrays.copyOf(encoded, encoded.length - 1);
        var trailing = Arrays.copyOf(encoded, encoded.length + 1);
        var unsupported = encoded.clone();
        unsupported[0]++;

        assertEquals(ProgramBookCodec.Diagnostic.MALFORMED,
                ProgramBookCodec.decode(truncated).diagnostic());
        assertEquals(ProgramBookCodec.Diagnostic.MALFORMED,
                ProgramBookCodec.decode(trailing).diagnostic());
        assertEquals(ProgramBookCodec.Diagnostic.UNSUPPORTED_VERSION,
                ProgramBookCodec.decode(unsupported).diagnostic());
        assertEquals(ProgramBookCodec.Diagnostic.TOO_LARGE,
                ProgramBookCodec.decode(new byte[ProgramBookCodec.MAX_BOOK_ENCODED_BYTES + 1]).diagnostic());
    }

    @Test
    void singleProgramEnvelopeCannotBeReplacedByAWholeBook() {
        var wholeBook = ProgramBookCodec.encode(ProgramBook.empty(4));
        var decoded = ProgramBookCodec.decodeProgram(wholeBook);

        assertEquals(ProgramBookCodec.Diagnostic.MALFORMED, decoded.diagnostic());
        assertNull(decoded.program());
    }

    @Test
    void emptySingleProgramEnvelopeRoundTripsAsAnIntentionalClear() {
        var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(null));

        assertTrue(decoded.valid());
        assertNull(decoded.program());
    }

    private static AbilityProgram program(Map<Integer, ProgramEditorLayout.NodePosition> positions) {
        var graph = new ProgramGraph(
                List.of(
                        new ProgramGraph.Node(
                                4,
                                PrecisionProgramNodeIds.ON_CAST,
                                1,
                                JsonParser.parseString("{\"mode\":\"cast\"}")
                        ),
                        new ProgramGraph.Node(
                                9,
                                PrecisionProgramNodeIds.id(
                                        org.academy.internal.common.ability.mentalout.precision.PrecisionGraph
                                                .NodeKind.MENTAL_STUPOR
                                ),
                                1,
                                JsonParser.parseString("{\"parameter\":12.5,\"nested\":[true,null]}")
                        )
                ),
                List.of(new ProgramGraph.Edge(
                        new ProgramGraph.Endpoint(4, "flow"),
                        new ProgramGraph.Endpoint(9, "flow")
                ))
        );
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("b71ee728-a7b8-44b8-8948-a93785ec6e7f"),
                "Codec sample",
                PrecisionProgramNodeCatalog.MENTALOUT,
                graph,
                new ProgramEditorLayout(positions)
        );
    }
}
