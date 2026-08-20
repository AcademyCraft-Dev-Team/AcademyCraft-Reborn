package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramTriggersTest {
    @Test
    void triggerNodesHaveOneFlowOutputAndNoInputs() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        for (var id : List.of(
                CommonProgramNodeIds.TRIGGER_HURT,
                CommonProgramNodeIds.TRIGGER_LOOP,
                CommonProgramNodeIds.TRIGGER_MELEE,
                CommonProgramNodeIds.TRIGGER_MOVEMENT
        )) {
            var entry = catalog.entry(id);
            assertEquals(ProgramNodeRole.ENTRY, entry.type().role());
            assertTrue(entry.defaultSchema().inputs().isEmpty());
            assertEquals(1, entry.defaultSchema().outputs().size());
            assertEquals("flow", entry.defaultSchema().outputs().getFirst().name());
        }
    }

    @Test
    void periodicAndMovementTriggersMatchTheirConfiguration() {
        var loop = configuration("interval", 20);
        var loopProgram = program(CommonProgramNodeIds.TRIGGER_LOOP, loop);
        assertTrue(ProgramTriggers.matches(
                loopProgram, ProgramTriggers.Type.LOOP, null, 20));
        assertFalse(ProgramTriggers.matches(
                loopProgram, ProgramTriggers.Type.LOOP, null, 19));
        loop.addProperty("interval", 0);
        assertTrue(ProgramTriggers.matches(
                program(CommonProgramNodeIds.TRIGGER_LOOP, loop),
                ProgramTriggers.Type.LOOP, null, 37));
        loop.addProperty("enabled", false);
        assertFalse(ProgramTriggers.matches(
                program(CommonProgramNodeIds.TRIGGER_LOOP, loop),
                ProgramTriggers.Type.LOOP, null, 37));

        var movement = configuration("condition", "sprint");
        var movementProgram = program(CommonProgramNodeIds.TRIGGER_MOVEMENT, movement);
        assertTrue(ProgramTriggers.matches(
                movementProgram,
                ProgramTriggers.Type.MOVEMENT,
                CommonProgramNodeCatalog.MovementCondition.SPRINT,
                0));
        assertFalse(ProgramTriggers.matches(
                movementProgram,
                ProgramTriggers.Type.MOVEMENT,
                CommonProgramNodeCatalog.MovementCondition.SNEAK,
                0));
    }

    @Test
    void onlyManualCompatibleEntryKindsAcceptKeyExecution() {
        assertTrue(ProgramTriggers.acceptsManualExecution(compiled(
                CommonProgramNodeIds.TRIGGER_LOOP, configuration("interval", 20))));
        assertTrue(ProgramTriggers.acceptsManualExecution(compiled(
                CommonProgramNodeIds.TRIGGER_MOVEMENT, configuration("condition", "jump"))));
        assertFalse(ProgramTriggers.acceptsManualExecution(compiled(
                CommonProgramNodeIds.TRIGGER_MELEE, new JsonObject())));
        assertFalse(ProgramTriggers.acceptsManualExecution(compiled(
                CommonProgramNodeIds.TRIGGER_HURT, new JsonObject())));
    }

    @Test
    void triggerConfigurationIsBoundedAndOnlyOneEntryIsAllowed() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var loopDefaults = catalog.entry(CommonProgramNodeIds.TRIGGER_LOOP)
                .defaultConfiguration().getAsJsonObject();
        assertTrue(loopDefaults.get("enabled").getAsBoolean());
        assertEquals(40, loopDefaults.get("interval").getAsInt());
        assertNull(catalog.schema(
                CommonProgramNodeIds.TRIGGER_LOOP,
                configuration("interval", -1)
        ));
        assertNull(catalog.schema(
                CommonProgramNodeIds.TRIGGER_LOOP,
                configuration("interval", 1201)
        ));

        var graph = new ProgramGraph(
                List.of(
                        node(1, CommonProgramNodeIds.TRIGGER_MELEE, new JsonObject()),
                        node(2, CommonProgramNodeIds.TRIGGER_HURT, new JsonObject())
                ),
                List.of()
        );
        var result = AbilityProgramDefinitions.mentalout().compile(graph, Set.of());
        assertFalse(result.valid());
        assertEquals(ProgramDiagnosticCode.MULTIPLE_ENTRIES,
                result.diagnostics().getFirst().code());

        var empty = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "entry-editor-test",
                PrecisionProgramNodeCatalog.MENTALOUT,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );
        var document = new ProgramEditorDocument(empty, catalog, Set.of())
                .addNode(CommonProgramNodeIds.TRIGGER_MELEE, 0, 0)
                .orElseThrow();
        var duplicate = document.addNode(CommonProgramNodeIds.TRIGGER_HURT, 80, 0);
        assertFalse(duplicate.successful());
        assertEquals(ProgramDiagnosticCode.MULTIPLE_ENTRIES, duplicate.diagnostic().code());
    }

    @Test
    void loopCostMultiplierUsesTheFortyTickBaseline() {
        assertEquals(1.0f, ProgramTriggers.loopCostMultiplier(40));
        assertEquals(1.0f, ProgramTriggers.loopCostMultiplier(80));
        assertEquals(2.0f, ProgramTriggers.loopCostMultiplier(39));
        assertEquals(2.0f, ProgramTriggers.loopCostMultiplier(5));
        assertEquals(10.0f, ProgramTriggers.loopCostMultiplier(1));
        assertEquals(10.0f, ProgramTriggers.loopCostMultiplier(0));
    }

    private static CompiledProgram compiled(
            net.minecraft.resources.Identifier type,
            JsonObject configuration
    ) {
        var result = AbilityProgramDefinitions.mentalout()
                .compile(program(type, configuration), Set.of());
        if (!result.valid()) throw new AssertionError(result.diagnostics());
        return result.program();
    }

    private static AbilityProgram program(
            net.minecraft.resources.Identifier type,
            JsonObject configuration
    ) {
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "trigger-test",
                PrecisionProgramNodeCatalog.MENTALOUT,
                new ProgramGraph(List.of(node(1, type, configuration)), List.of()),
                ProgramEditorLayout.EMPTY
        );
    }

    private static ProgramGraph.Node node(
            int id,
            net.minecraft.resources.Identifier type,
            JsonObject configuration
    ) {
        return new ProgramGraph.Node(id, type, 1, configuration);
    }

    private static JsonObject configuration(String field, Number value) {
        var configuration = new JsonObject();
        configuration.addProperty(field, value);
        return configuration;
    }

    private static JsonObject configuration(String field, String value) {
        var configuration = new JsonObject();
        configuration.addProperty(field, value);
        return configuration;
    }
}
