package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramEditorDocumentTest {
    @Test
    void everyVisibleMentaloutNodeHasADecodableDefaultAndStableGroup() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();

        assertEquals(
                CommonProgramNodeCatalog.INSTANCE.types().size()
                        + PrecisionProgramNodeCatalog.INSTANCE.types().size(),
                catalog.entries().size()
        );
        for (var entry : catalog.entries()) {
            assertNotNull(catalog.schema(entry.id(), entry.defaultConfiguration()), entry.id().toString());
            assertFalse(entry.displayName().isBlank());
            assertTrue(entry.type().scope().allowsCategory(catalog.category()));
        }
    }

    @Test
    void everyBuiltInNodePortAndScopeMarkerHasEnglishAndChineseText() {
        for (var language : new String[]{"en_us", "zh_cn"}) {
            var translations = loadLanguage(language);
            var missing = new ArrayList<String>();
            for (var definition : AbilityProgramDefinitions.all()) {
                for (var entry : definition.editorCatalog().entries()) {
                    collectMissing(translations, entry.translationKey(), missing);
                    collectMissing(translations, entry.descriptionTranslationKey(), missing);
                    for (var port : entry.defaultSchema().inputs()) {
                        collectMissing(translations, entry.portTranslationKey(port.name()), missing);
                    }
                    for (var port : entry.defaultSchema().outputs()) {
                        collectMissing(translations, entry.portTranslationKey(port.name()), missing);
                    }
                }
            }
            collectMissing(translations,
                    "screen.academy.program.node_scope.category_restricted", missing);
            collectMissing(translations,
                    "screen.academy.program.node_scope.category_specific", missing);
            assertTrue(missing.isEmpty(), language + " missing or blank: " + missing);
        }
    }

    @Test
    void buildsAValidProgramWithNamedTypedPorts() {
        var document = emptyDocument();
        document = document.addNode(PrecisionProgramNodeIds.ON_CAST, -80, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0, 40).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BRANCH, 80, 0).orElseThrow();
        document = document.connect(endpoint(0, "flow"), endpoint(2, "flow")).orElseThrow();
        document = document.connect(endpoint(1, "value"), endpoint(2, "condition")).orElseThrow();

        var validation = document.validation();
        assertTrue(validation.valid(), () -> validation.diagnostics().toString());
        assertEquals(2, document.program().graph().edges().size());
    }

    @Test
    void configurationAndLayoutEditsPreserveSemanticIdentity() {
        var document = emptyDocument()
                .addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 2, 3).orElseThrow();
        var id = document.program().id();
        var configuration = new JsonObject();
        configuration.addProperty("value", true);

        document = document.configureNode(0, configuration).orElseThrow();
        document = document.moveNode(0, 48.5, -32.25).orElseThrow();

        assertEquals(id, document.program().id());
        assertTrue(document.program().graph().nodes().getFirst().configuration()
                .getAsJsonObject().get("value").getAsBoolean());
        assertEquals(new ProgramEditorLayout.NodePosition(48.5, -32.25),
                document.program().editorLayout().nodePositions().get(0));
    }

    @Test
    void rejectsTypeMismatchAndConnectionLimitWithoutDamagingTheDocument() {
        var document = emptyDocument();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0, 40).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_NOT, 80, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.INTEGER_ADD, 160, 0).orElseThrow();

        var mismatch = document.connect(endpoint(0, "value"), endpoint(3, "left"));
        assertFalse(mismatch.successful());
        assertEquals(ProgramDiagnosticCode.TYPE_MISMATCH, mismatch.diagnostic().code());

        document = document.connect(endpoint(0, "value"), endpoint(2, "value")).orElseThrow();
        var occupied = document.connect(endpoint(1, "value"), endpoint(2, "value"));
        assertFalse(occupied.successful());
        assertEquals(ProgramDiagnosticCode.TOO_MANY_CONNECTIONS, occupied.diagnostic().code());
        assertEquals(1, document.program().graph().edges().size());
    }

    @Test
    void rejectsDataCyclesButAllowsIntentionalFlowCycles() {
        var data = emptyDocument();
        data = data.addNode(CommonProgramNodeIds.BOOLEAN_NOT, 0, 0).orElseThrow();
        data = data.addNode(CommonProgramNodeIds.BOOLEAN_NOT, 80, 0).orElseThrow();
        data = data.connect(endpoint(0, "result"), endpoint(1, "value")).orElseThrow();

        var cycle = data.connect(endpoint(1, "result"), endpoint(0, "value"));
        assertFalse(cycle.successful());
        assertEquals(ProgramDiagnosticCode.DATA_CYCLE, cycle.diagnostic().code());

        var flow = emptyDocument();
        flow = flow.addNode(CommonProgramNodeIds.BRANCH, 0, 0).orElseThrow();
        flow = flow.addNode(CommonProgramNodeIds.BRANCH, 80, 0).orElseThrow();
        flow = flow.connect(endpoint(0, "true"), endpoint(1, "flow")).orElseThrow();
        flow = flow.connect(endpoint(1, "true"), endpoint(0, "flow")).orElseThrow();
        assertEquals(2, flow.program().graph().edges().size());
    }

    @Test
    void removingANodeAlsoRemovesItsEdgesAndEditorPosition() {
        var document = emptyDocument();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_NOT, 80, 0).orElseThrow();
        document = document.connect(endpoint(0, "value"), endpoint(1, "value")).orElseThrow();

        document = document.removeNode(0).orElseThrow();

        assertEquals(1, document.program().graph().nodes().size());
        assertTrue(document.program().graph().edges().isEmpty());
        assertFalse(document.program().editorLayout().nodePositions().containsKey(0));
    }

    @Test
    void removingMultipleNodesIsAtomicAndCleansAllConnectedState() {
        var document = emptyDocument();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_NOT, 80, 0).orElseThrow();
        document = document.addNode(CommonProgramNodeIds.BOOLEAN_NOT, 160, 0).orElseThrow();
        document = document.connect(endpoint(0, "value"), endpoint(1, "value")).orElseThrow();
        document = document.connect(endpoint(1, "result"), endpoint(2, "value")).orElseThrow();

        document = document.removeNodes(Set.of(0, 1)).orElseThrow();

        assertEquals(List.of(2), document.program().graph().nodes().stream()
                .map(ProgramGraph.Node::id).toList());
        assertTrue(document.program().graph().edges().isEmpty());
        assertEquals(Set.of(2), document.program().editorLayout().nodePositions().keySet());
    }

    @Test
    void editorPreservesTheAbilityDefinitionBudgetAcrossEdits() {
        var definition = AbilityProgramDefinitions.mentalout();
        var document = emptyDocument();
        for (var index = 0; index < definition.limits().maxNodes(); index++) {
            document = document.addNode(
                    CommonProgramNodeIds.BOOLEAN_CONSTANT, index * 8.0, 0.0).orElseThrow();
        }

        var overflow = document.addNode(CommonProgramNodeIds.BOOLEAN_CONSTANT, 0.0, 16.0);

        assertFalse(overflow.successful());
        assertEquals(ProgramDiagnosticCode.TOO_MANY_NODES, overflow.diagnostic().code());
    }

    private static ProgramEditorDocument emptyDocument() {
        return new ProgramEditorDocument(new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("2843c840-2737-403b-8bd2-0f2ac1183d06"),
                "Editor test",
                PrecisionProgramNodeCatalog.MENTALOUT,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        ), AbilityProgramDefinitions.mentalout(), Set.of());
    }

    private static ProgramGraph.Endpoint endpoint(int nodeId, String port) {
        return new ProgramGraph.Endpoint(nodeId, port);
    }

    private static JsonObject loadLanguage(String language) {
        var path = "/assets/academy/lang/" + language + ".json";
        var stream = ProgramEditorDocumentTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static void collectMissing(
            JsonObject translations,
            String key,
            ArrayList<String> missing
    ) {
        if (!translations.has(key) || translations.get(key).getAsString().isBlank()) missing.add(key);
    }
}
