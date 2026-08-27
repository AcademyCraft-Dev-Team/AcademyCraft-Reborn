package org.academy.internal.client.ability.mentalout;

import com.mojang.blaze3d.platform.InputConstants;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.program.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    void modularEditorAcceptsDeleteAndBackspaceForNodeDeletion() {
        assertTrue(ModularProgramScreen.isDeleteKey(InputConstants.KEY_DELETE));
        assertTrue(ModularProgramScreen.isDeleteKey(InputConstants.KEY_BACKSPACE));
        assertFalse(ModularProgramScreen.isDeleteKey(InputConstants.KEY_ESCAPE));
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
        var imported = PrecisionProgramBookMigrator.importProgram(
                UUID.fromString("d44789de-85a5-4b96-bc9d-2e9740450e14"),
                1,
                graph,
                null
        );
        assertTrue(imported.valid());
        var book = new ProgramBook(
                ProgramBook.CURRENT_SCHEMA_VERSION,
                9L,
                1,
                List.of(
                        ProgramBook.Slot.EMPTY,
                        new ProgramBook.Slot(imported.program()),
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY
                )
        );
        PrecisionOperationClient.handleSync(ProgramBookCodec.encode(book));

        assertEquals(graph, PrecisionOperationClient.graph(1));
        assertEquals(graph, PrecisionOperationClient.serverGraph(1));
        assertEquals(imported.program(), PrecisionOperationClient.program(1));
        assertEquals(imported.program(), PrecisionOperationClient.serverProgram(1));
        assertEquals(9L, PrecisionOperationClient.revision());

        PrecisionOperationClient.resetSession();

        assertEquals(PrecisionGraph.EMPTY, PrecisionOperationClient.graph(1));
        assertEquals(PrecisionGraph.EMPTY, PrecisionOperationClient.serverGraph(1));
        assertNull(PrecisionOperationClient.program(1));
        assertNull(PrecisionOperationClient.serverProgram(1));
        assertEquals(0L, PrecisionOperationClient.revision());
    }

    @Test
    void mixedProgramSyncPreservesNamedPortGraphWithoutLegacyExport() {
        var document = new ProgramEditorDocument(new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("ee896afa-c89e-454c-9192-333bb9bbbc2b"),
                "Mixed editor",
                PrecisionProgramNodeCatalog.MENTALOUT,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        ), AbilityProgramDefinitions.mentalout(), Set.of());
        document = document.addNode(PrecisionProgramNodeIds.ON_CAST, 0, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0, 48).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BRANCH, 112, 0).orElseThrow();
        document = document.addNode(
                PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.END_INTRUSION),
                224,
                0
        ).orElseThrow();
        document = document.connect(endpoint(0, "flow"), endpoint(2, "flow")).orElseThrow();
        document = document.connect(endpoint(1, "value"), endpoint(2, "condition")).orElseThrow();
        document = document.connect(endpoint(2, "true"), endpoint(3, "flow")).orElseThrow();
        var validation = document.validation();
        assertTrue(validation.valid(), () -> validation.diagnostics().toString());
        var program = document.program();
        var book = new ProgramBook(
                ProgramBook.CURRENT_SCHEMA_VERSION,
                12L,
                2,
                List.of(
                        ProgramBook.Slot.EMPTY,
                        ProgramBook.Slot.EMPTY,
                        new ProgramBook.Slot(program),
                        ProgramBook.Slot.EMPTY
                )
        );

        PrecisionOperationClient.handleSync(ProgramBookCodec.encode(book));

        assertEquals(program, PrecisionOperationClient.program(2));
        assertEquals(program, PrecisionOperationClient.serverProgram(2));
        assertEquals(PrecisionGraph.EMPTY, PrecisionOperationClient.graph(2));
        assertEquals(12L, PrecisionOperationClient.revision());
    }

    private static ProgramGraph.Endpoint endpoint(int nodeId, String port) {
        return new ProgramGraph.Endpoint(nodeId, port);
    }
}
