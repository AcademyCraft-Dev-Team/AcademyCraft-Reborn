package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeCatalog;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeIds;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramClipboardCodecTest {
    private static final Identifier CATEGORY = Identifier.parse("academy:mentalout");

    @Test
    void fragmentKeepsAllSelectedNodesInternalEdgesAndLayout() {
        var source = program();

        var decoded = ProgramClipboardCodec.decodeFragment(
                ProgramClipboardCodec.encodeFragment(source, Set.of(2, 3)), CATEGORY);

        assertNotNull(decoded);
        assertEquals(List.of(2, 3), decoded.graph().nodes().stream()
                .map(ProgramGraph.Node::id).toList());
        assertEquals(1, decoded.graph().edges().size());
        assertEquals(Set.of(2, 3), decoded.editorLayout().nodePositions().keySet());
    }

    @Test
    void completeProgramsRoundTripAndRejectAnotherCategory() {
        var encoded = ProgramClipboardCodec.encodeProgram(program());

        assertNotNull(ProgramClipboardCodec.decodeProgram(encoded, CATEGORY));
        assertNull(ProgramClipboardCodec.decodeProgram(
                encoded, Identifier.parse("academy:teleport")));
        assertNull(ProgramClipboardCodec.decodeProgram("not a program", CATEGORY));
    }

    @Test
    void legacyAeromanipProgramsAreMigratedIntoTheUnifiedFamilyOnImport() {
        var category = AeromanipProgramNodeCatalog.AEROMANIP;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var legacyConfiguration = new JsonObject();
        legacyConfiguration.addProperty("power", 1.5f);
        legacyConfiguration.addProperty("radius", 4);
        legacyConfiguration.addProperty("duration", 6);
        legacyConfiguration.addProperty("restore_when_settled", false);
        var legacy = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "Legacy clipboard",
                category,
                new ProgramGraph(
                        List.of(
                                new ProgramGraph.Node(0, entryId, 1, new JsonObject()),
                                new ProgramGraph.Node(
                                        1, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, 1,
                                        blockPosition(10, 64, 10)),
                                new ProgramGraph.Node(
                                        2, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE, 1,
                                        legacyConfiguration),
                                new ProgramGraph.Node(
                                        3, CommonProgramNodeIds.DIRECTION_CONSTANT, 1,
                                        direction(0.0, 1.0, 0.0))
                        ),
                        List.of(
                                edge(0, "flow", 2, "flow"),
                                edge(1, "position", 2, "block"),
                                edge(3, "direction", 2, "direction")
                        )),
                ProgramEditorLayout.EMPTY
        );

        var decoded = ProgramClipboardCodec.decodeProgram(
                ProgramClipboardCodec.encodeProgram(legacy), category);

        assertNotNull(decoded);
        assertEquals(1, decoded.schemaVersion(),
                "the clipboard envelope version must stay compatible");
        assertNull(find(decoded, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE));
        var extract = find(decoded, AeromanipProgramNodeIds.STRUCTURE_EXTRACT);
        var control = find(decoded, AeromanipProgramNodeIds.STRUCTURE_CONTROL);
        var launch = find(decoded, AeromanipProgramNodeIds.STRUCTURE_LAUNCH);
        assertNotNull(extract);
        assertNotNull(control);
        assertNotNull(launch);
        assertTrue(hasEdge(decoded, 1, "position", extract.id(), "host"),
                "the legacy block port must keep feeding the extract host");
        assertTrue(hasEdge(decoded, 3, "direction", launch.id(), "direction"),
                "the legacy direction port must keep feeding the unified launch");
        assertTrue(hasEdge(decoded, extract.id(), "structure", control.id(), "structure"));
        assertTrue(hasEdge(decoded, control.id(), "control", launch.id(), "control"));
        assertEquals(1.5f, launch.configuration().getAsJsonObject().get("power").getAsFloat());
        assertEquals(6.0f, launch.configuration().getAsJsonObject().get("duration_seconds").getAsFloat());
        assertFalse(launch.configuration().getAsJsonObject()
                .get("restore_when_settled").getAsBoolean());
    }

    private static ProgramGraph.Node find(AbilityProgram program, Identifier type) {
        return program.graph().nodes().stream()
                .filter(node -> node.type().equals(type)).findFirst().orElse(null);
    }

    private static boolean hasEdge(
            AbilityProgram program, int fromNode, String fromPort, int toNode, String toPort
    ) {
        return program.graph().edges().stream().anyMatch(edge ->
                edge.from().nodeId() == fromNode && edge.from().port().equals(fromPort)
                        && edge.to().nodeId() == toNode && edge.to().port().equals(toPort));
    }

    private static JsonObject blockPosition(int x, int y, int z) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", "minecraft:overworld");
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return configuration;
    }

    private static JsonObject direction(double x, double y, double z) {
        var configuration = new JsonObject();
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return configuration;
    }

    private static AbilityProgram program() {
        var nodes = List.of(
                node(1, "entry"),
                node(2, "value/scalar"),
                node(3, "state/variable_set")
        );
        var edges = List.of(
                edge(1, "flow", 3, "flow"),
                edge(2, "value", 3, "value")
        );
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "Clipboard test",
                CATEGORY,
                new ProgramGraph(nodes, edges),
                new ProgramEditorLayout(Map.of(
                        1, new ProgramEditorLayout.NodePosition(0, 0),
                        2, new ProgramEditorLayout.NodePosition(20, 30),
                        3, new ProgramEditorLayout.NodePosition(80, 30)
                ))
        );
    }

    private static ProgramGraph.Node node(int id, String path) {
        return new ProgramGraph.Node(
                id, Identifier.parse("academy:program/core/" + path), 1, new JsonObject());
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(from, output),
                new ProgramGraph.Endpoint(to, input));
    }
}
