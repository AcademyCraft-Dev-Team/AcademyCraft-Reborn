package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
