package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.PrecisionProgramNodeIds;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramVm;
import org.academy.internal.common.ability.program.ProgramVmResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTaskPreviewTest {
    @Test
    void readsCurrentConfigurationsAndKnownDefaultsWithoutEvaluatingTargetsOrPrices() {
        var kind = ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_STRUCTURE;
        var definition = AbilityProgramDefinitions.require(kind.category());
        var program = ProgramTaskTemplates.create(empty(kind.category()), definition,
                ProgramTaskTemplates.requiredCapabilities(definition, kind), kind);
        var preview = ProgramTaskPreview.inspect(program, definition, Set.of());
        var action = preview.nodes().stream().filter(node -> node.role() == ProgramNodeRole.ACTION).findFirst().orElseThrow();
        assertEquals(ProgramTaskPreview.Source.DEFAULT, input(action, "structure").source());
        assertEquals("screen.academy.program.preview.default.look_structure", input(action, "structure").defaultKey());
        assertEquals("3", input(action, "displacement").literal());
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("missing_capability")));
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("cost_runtime")));
        assertThrows(UnsupportedOperationException.class, () -> preview.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> action.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.notices().getFirst().arguments().add("changed"));
    }

    @Test
    void sampledInputsRemainConnectionsAndDoNotPretendToBeLiveValues() {
        var kind = ProgramTaskTemplates.Kind.TELEPORT_CHECKED_FORWARD;
        var definition = AbilityProgramDefinitions.require(kind.category());
        var program = ProgramTaskTemplates.create(empty(kind.category()), definition,
                ProgramTaskTemplates.requiredCapabilities(definition, kind), kind);
        var preview = ProgramTaskPreview.inspect(program, definition, Set.of());
        var action = preview.nodes().stream().filter(node -> node.role() == ProgramNodeRole.ACTION).findFirst().orElseThrow();
        assertEquals(ProgramTaskPreview.Source.CONNECTED, input(action, "destination").source());
        assertEquals("", input(action, "destination").literal());
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("sample_identity")));
        var offset = preview.nodes().stream().filter(node -> node.nodeType().equals(CommonProgramNodeIds.WORLD_POSITION_OFFSET)).findFirst().orElseThrow();
        assertEquals(ProgramTaskPreview.Source.LITERAL, input(offset, "distance").source());
        assertEquals("3.0", input(offset, "distance").literal());
    }

    @Test
    void malformedUnknownAndOversizedGraphsProduceBoundedExplanations() {
        var category = AcademyCraft.academy("teleport");
        var definition = AbilityProgramDefinitions.require(category);
        var nodes = new ArrayList<ProgramGraph.Node>();
        nodes.add(new ProgramGraph.Node(0, AcademyCraft.academy("does_not_exist"), 1, new JsonObject()));
        var invalid = new JsonObject(); invalid.addProperty("type", "float"); invalid.addProperty("value", "not-a-number");
        nodes.add(new ProgramGraph.Node(1, CommonProgramNodeIds.SCALAR_CONSTANT, 1, invalid));
        // Keep the real text codec's 256 UTF-16-unit limit while crossing the preview's 160-code-point limit.
        var config = new JsonObject(); config.addProperty("value", "x".repeat(130) + "🙂".repeat(50));
        assertDoesNotThrow(() -> CommonProgramNodeCatalog.TextConfiguration.CODEC.parse(JsonOps.INSTANCE, config).getOrThrow());
        for (int index = 2; index < 132; index++) nodes.add(new ProgramGraph.Node(index, CommonProgramNodeIds.TEXT_CONSTANT, 1, config));
        var program = new AbilityProgram(1, UUID.randomUUID(), "broken", category,
                new ProgramGraph(nodes, List.of(new ProgramGraph.Edge(new ProgramGraph.Endpoint(999, "nope"),
                        new ProgramGraph.Endpoint(2, "nope")))), ProgramEditorLayout.EMPTY);
        var preview = ProgramTaskPreview.inspect(program, definition, Set.of());
        assertEquals(4, preview.omittedNodes());
        assertTrue(preview.nodes().size() <= 128);
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("unknown_node")));
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("invalid_configuration")));
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("dangling_edge")));
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("truncated")));
        var text = preview.nodes().getFirst().configuration().getFirst().value();
        assertEquals(161, text.codePointCount(0, text.length()));
        assertTrue(text.endsWith("…"));
    }

    @Test
    void requiredUnwiredInputsAndExperimentalRestorationAreNeverCalledSafe() {
        var category = AcademyCraft.academy("accelerator");
        var definition = AbilityProgramDefinitions.require(category);
        var id = AcademyCraft.academy("program/accelerator/action/structure/staticize");
        var entry = definition.editorCatalog().entry(id);
        var program = new AbilityProgram(1, UUID.randomUUID(), "legacy", category,
                new ProgramGraph(List.of(new ProgramGraph.Node(1, id, entry.type().schemaVersion(),
                        entry.defaultConfiguration())), List.of()), ProgramEditorLayout.EMPTY);
        var preview = ProgramTaskPreview.inspect(program, definition, Set.of());
        assertTrue(preview.nodes().getFirst().inputs().stream().anyMatch(input -> input.source() == ProgramTaskPreview.Source.REQUIRED_MISSING));
        assertTrue(preview.notices().stream().anyMatch(notice -> notice.translationKey().endsWith("unreleased")));
    }

    @Test
    void electronBeamRequiresADirectionWhileMiningBeamCanUseTheLookDirection() {
        var definition = AbilityProgramDefinitions.require(AcademyCraft.academy("meltdowner"));
        for (var id : List.of(MeltdownerProgramNodeIds.ELECTRON_BEAM, MeltdownerProgramNodeIds.MINING_BEAM)) {
            var entry = definition.editorCatalog().entry(id);
            var graph = new ProgramGraph(List.of(
                    new ProgramGraph.Node(1, BaseAbilityProgramDefinition.entryId(definition.category()), 1, new JsonObject()),
                    new ProgramGraph.Node(2, id, entry.type().schemaVersion(), entry.defaultConfiguration())),
                    List.of(edge(1, "flow", 2, "flow")));
            var program = new AbilityProgram(1, UUID.randomUUID(), "beam defaults", definition.category(), graph, ProgramEditorLayout.EMPTY);
            var preview = ProgramTaskPreview.inspect(program, definition, entry.type().scope().requiredCapabilities());
            var action = preview.nodes().stream().filter(node -> node.nodeId() == 2).findFirst().orElseThrow();
            var direction = input(action, "direction");
            if (id.equals(MeltdownerProgramNodeIds.ELECTRON_BEAM)) {
                assertFalse(definition.compile(program, entry.type().scope().requiredCapabilities()).valid(),
                        "The real electron-beam schema requires its direction edge");
                assertEquals(ProgramTaskPreview.Source.REQUIRED_MISSING, direction.source());
                assertEquals("", direction.defaultKey());
            } else {
                assertTrue(definition.compile(program, entry.type().scope().requiredCapabilities()).valid());
                assertEquals(ProgramTaskPreview.Source.DEFAULT, direction.source());
                assertEquals("screen.academy.program.preview.default.look_direction", direction.defaultKey());
            }
        }
    }

    @Test
    void aConstantsConnectedConfigurationInputPreventsAFalseLiteralClaim() {
        var definition = AbilityProgramDefinitions.mentalout();
        var scalar = new JsonObject(); scalar.addProperty("type", "big_integer"); scalar.addProperty("value", "3");
        var target = new JsonObject(); target.addProperty("name", "observed");
        target.addProperty("type", ProgramValueTypes.BIG_INTEGER.id().toString());
        var graph = new ProgramGraph(List.of(
                new ProgramGraph.Node(1, PrecisionProgramNodeIds.ON_CAST, 1, new JsonObject()),
                new ProgramGraph.Node(2, CommonProgramNodeIds.SCALAR_CONSTANT, 1, scalar),
                new ProgramGraph.Node(3, CommonProgramNodeIds.GAME_TIME, 1, new JsonObject()),
                new ProgramGraph.Node(4, CommonProgramNodeIds.VARIABLE_SET, 1, target)), List.of(
                edge(1, "flow", 4, "flow"), edge(3, "time", 2, "value"), edge(2, "value", 4, "value")));
        var program = new AbilityProgram(1, UUID.randomUUID(), "dynamic constant", definition.category(), graph, ProgramEditorLayout.EMPTY);
        var compiled = definition.compile(program, Set.of());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var session = new ProgramVm.Session(compiled.program());
        assertEquals(ProgramVmResult.Status.COMPLETED, session.run(42, 100, definition.executors(), null).status());
        assertEquals(BigInteger.valueOf(42), session.variables().get("observed").value(),
                "The real executor reads the connected override, not the stored value 3");

        var preview = ProgramTaskPreview.inspect(program, definition, Set.of());
        var consumer = preview.nodes().stream().filter(node -> node.nodeId() == 4).findFirst().orElseThrow();
        assertEquals(ProgramTaskPreview.Source.CONNECTED, input(consumer, "value").source());
        assertEquals("", input(consumer, "value").literal());
        assertEquals(List.of(new ProgramGraph.Endpoint(2, "value")), input(consumer, "value").from());
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static ProgramTaskPreview.InputPreview input(ProgramTaskPreview.NodePreview node, String port) {
        return node.inputs().stream().filter(input -> input.port().equals(port)).findFirst().orElseThrow();
    }
    private static AbilityProgram empty(Identifier category) {
        return new AbilityProgram(1, UUID.randomUUID(), "preview", category, ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY);
    }
}
