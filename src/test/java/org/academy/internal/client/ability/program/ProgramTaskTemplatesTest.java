package org.academy.internal.client.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTaskTemplatesTest {
    @Test
    void everyTaskIsAnEditableStandardGraphForItsActualCategory() {
        assertEquals(10, ProgramTaskTemplates.Kind.values().length);
        var categories = new HashSet<Identifier>();
        for (var kind : ProgramTaskTemplates.Kind.values()) {
            var definition = definition(kind);
            var capabilities = ProgramTaskTemplates.requiredCapabilities(definition, kind);
            assertFalse(capabilities.isEmpty(), kind.name());
            var empty = empty(definition);
            var result = ProgramTaskTemplates.create(empty, definition, capabilities, kind);
            assertEquals(empty.id(), result.id());
            assertEquals(empty.name(), result.name());
            assertEquals(empty.category(), result.category());
            assertEquals(empty.schemaVersion(), result.schemaVersion());
            assertTrue(empty.graph().nodes().isEmpty(), "Creating the example must not mutate the input slot");
            assertTrue(definition.compile(result, capabilities).valid(), kind.name());
            assertEquals(result.graph().nodes().size(), result.editorLayout().nodePositions().size());
            assertEquals(1, result.graph().nodes().stream().filter(node ->
                    definition.nodeLookup().find(node.type()).role() == ProgramNodeRole.ENTRY).count());
            for (var node : result.graph().nodes()) {
                var metadata = definition.editorCatalog().entry(node.type());
                assertNotNull(metadata, node.type().toString());
                assertTrue(metadata.visible(), node.type().toString());
                assertEquals(ProgramEditorNodeCatalog.PresentationStatus.AVAILABLE, metadata.presentationStatus());
                assertNotNull(definition.editorCatalog().schema(node.type(), node.configuration()));
                assertFalse(node.type().getPath().endsWith("/staticize"));
                assertNotEquals(CommonProgramNodeIds.DEBUG_OUTPUT, node.type());
            }
            var moved = new ProgramEditorDocument(result, definition, capabilities)
                    .moveNode(result.graph().nodes().getFirst().id(), 64, 64);
            assertTrue(moved.successful());
            assertTrue(moved.document().validation().valid());
            var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(result));
            assertTrue(decoded.valid());
            assertEquals(result, decoded.program(), "Templates require no private persistence format");
            categories.add(kind.category());
        }
        assertEquals(Set.of("accelerator", "electromaster", "aeromanip", "teleport", "meltdowner",
                "darkmatter", "mentalout"), categories.stream().map(Identifier::getPath).collect(Collectors.toSet()));
    }

    @Test
    void taskPaletteRequiresTheOriginalCapabilitiesAndDoesNotCrossCategories() {
        for (var definition : AbilityProgramDefinitions.all()) {
            var kinds = ProgramTaskTemplates.forCategory(definition);
            assertFalse(kinds.isEmpty());
            assertTrue(ProgramTaskTemplates.available(definition, Set.of()).isEmpty());
            var allRequired = kinds.stream().flatMap(kind ->
                    ProgramTaskTemplates.requiredCapabilities(definition, kind).stream()).collect(Collectors.toSet());
            assertEquals(kinds, ProgramTaskTemplates.available(definition, allRequired));
            for (var kind : kinds) {
                var missing = new HashSet<>(allRequired);
                missing.remove(ProgramTaskTemplates.requiredCapabilities(definition, kind).iterator().next());
                assertFalse(ProgramTaskTemplates.available(definition, missing).contains(kind));
                assertThrows(IllegalArgumentException.class, () -> ProgramTaskTemplates.create(
                        empty(definition), definition, missing, kind));
            }
        }
        var mental = AbilityProgramDefinitions.mentalout();
        assertEquals(Set.of(AcademyCraft.academy(SkillNames.MENTAL_STUPOR)),
                ProgramTaskTemplates.requiredCapabilities(mental, ProgramTaskTemplates.Kind.MENTALOUT_MENTAL_STUPOR));
    }

    @Test
    void populatedMalformedAndForeignSlotsAreNeverOverwritten() {
        var kind = ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_STRUCTURE;
        var definition = definition(kind);
        var capabilities = ProgramTaskTemplates.requiredCapabilities(definition, kind);
        var example = ProgramTaskTemplates.create(empty(definition), definition, capabilities, kind);
        assertThrows(IllegalArgumentException.class, () -> ProgramTaskTemplates.create(
                example, definition, capabilities, kind));
        var dangling = new AbilityProgram(1, UUID.randomUUID(), "damaged slot", definition.category(),
                new ProgramGraph(List.of(), List.of(new ProgramGraph.Edge(new ProgramGraph.Endpoint(1, "flow"),
                        new ProgramGraph.Endpoint(2, "flow")))), ProgramEditorLayout.EMPTY);
        assertThrows(IllegalArgumentException.class, () -> ProgramTaskTemplates.create(
                dangling, definition, capabilities, kind));
        var foreign = AbilityProgramDefinitions.mentalout();
        assertThrows(IllegalArgumentException.class, () -> ProgramTaskTemplates.create(
                empty(foreign), definition, capabilities, kind));
        assertThrows(IllegalArgumentException.class, () -> ProgramTaskTemplates.create(
                empty(definition), foreign, capabilities, kind));
    }

    @Test
    void basicStructureTasksReuseTheExistingTwoNodeDefaults() {
        var kinds = Arrays.stream(ProgramTaskTemplates.Kind.values())
                .filter(kind -> kind.name().endsWith("_GUIDE_STRUCTURE")
                        || kind.name().endsWith("_ROTATE_STRUCTURE")).toList();
        assertEquals(6, kinds.size());
        for (var kind : kinds) {
            var definition = definition(kind);
            var program = ProgramTaskTemplates.create(empty(definition), definition,
                    ProgramTaskTemplates.requiredCapabilities(definition, kind), kind);
            assertEquals(2, program.graph().nodes().size());
            assertEquals(1, program.graph().edges().size());
            var action = program.graph().nodes().stream().filter(node ->
                    definition.nodeLookup().find(node.type()).role() == ProgramNodeRole.ACTION).findFirst().orElseThrow();
            assertTrue(action.type().getPath().endsWith(kind.name().endsWith("_GUIDE_STRUCTURE")
                    ? "/structure/move_group" : "/structure/rotate_group"));
            assertEquals(definition.editorCatalog().entry(action.type()).defaultConfiguration(), action.configuration());
        }
    }

    @Test
    void targetedActionsHaveExplicitTypedTargetsRatherThanImplicitAreaSelection() {
        for (var kind : List.of(ProgramTaskTemplates.Kind.DARKMATTER_DISASSEMBLE_BLOCK,
                ProgramTaskTemplates.Kind.MENTALOUT_MENTAL_STUPOR)) {
            var definition = definition(kind);
            var program = ProgramTaskTemplates.create(empty(definition), definition,
                    ProgramTaskTemplates.requiredCapabilities(definition, kind), kind);
            var target = program.graph().nodes().stream().filter(node ->
                    node.type().equals(CommonProgramNodeIds.LOOK_TARGET)).findFirst().orElseThrow();
            assertEquals(kind == ProgramTaskTemplates.Kind.DARKMATTER_DISASSEMBLE_BLOCK ? "block" : "entity",
                    target.configuration().getAsJsonObject().get("target_type").getAsString());
            assertFalse(program.graph().nodes().stream().anyMatch(node ->
                    node.type().equals(CommonProgramNodeIds.ENTITIES_AROUND)));
            if (kind == ProgramTaskTemplates.Kind.MENTALOUT_MENTAL_STUPOR) {
                assertTrue(program.graph().nodes().stream().anyMatch(node ->
                        node.type().equals(CommonProgramNodeIds.collection("entity", "singleton"))));
            }
        }
    }

    @Test
    void forwardTeleportUsesTheCastersPositionAndDirectionWithAnEditableDistance() {
        var kind = ProgramTaskTemplates.Kind.TELEPORT_FORWARD;
        var definition = definition(kind);
        var capabilities = ProgramTaskTemplates.requiredCapabilities(definition, kind);
        var program = ProgramTaskTemplates.create(empty(definition), definition, capabilities, kind);
        var distance = program.graph().nodes().stream().filter(node ->
                node.type().equals(CommonProgramNodeIds.SCALAR_CONSTANT)).findFirst().orElseThrow();
        assertEquals("float", distance.configuration().getAsJsonObject().get("type").getAsString());
        assertEquals(3.0, distance.configuration().getAsJsonObject().get("value").getAsDouble(), 1E-9);
        assertFalse(program.graph().nodes().stream().anyMatch(node ->
                node.type().equals(CommonProgramNodeIds.WORLD_POSITION_CONSTANT)));
        var editedConfiguration = distance.configuration().getAsJsonObject();
        editedConfiguration.addProperty("value", "5.0");
        var edited = new ProgramEditorDocument(program, definition, capabilities)
                .configureNode(distance.id(), editedConfiguration);
        assertTrue(edited.successful());
        assertTrue(edited.document().validation().valid());
        var independent = ProgramTaskTemplates.create(empty(definition), definition, capabilities, kind);
        assertEquals(3.0, independent.graph().nodes().stream().filter(node -> node.type().equals(
                CommonProgramNodeIds.SCALAR_CONSTANT)).findFirst().orElseThrow().configuration()
                .getAsJsonObject().get("value").getAsDouble(), 1E-9);
        assertEquals("integer", definition.editorCatalog().entry(CommonProgramNodeIds.SCALAR_CONSTANT)
                .defaultConfiguration().getAsJsonObject().get("type").getAsString());
    }

    private static AbilityProgramDefinition definition(ProgramTaskTemplates.Kind kind) {
        return AbilityProgramDefinitions.require(kind.category());
    }

    private static AbilityProgram empty(AbilityProgramDefinition definition) {
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), "editable task",
                definition.category(), ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY);
    }
}
