package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramEditorCachePolicyTest {
    @Test
    void mutableNumberSurvivesGraphCopiesAndMustUseTheLiveEditorPath() {
        var number = new AtomicInteger(1);
        var encoded = new JsonObject();
        encoded.addProperty("value", number);
        var graph = graph(encoded);
        assertFalse(ProgramEditorCachePolicy.stableGraph(graph));
        number.set(-1);
        assertEquals(-1, graph.nodes().getFirst().configuration().getAsJsonObject().get("value").getAsInt());
    }

    @Test
    void parsedGraphsOwnTheirContainersAndCanUseSemanticSnapshots() {
        var encoded = JsonParser.parseString("{\"items\":[null,true,1,0.5,\"text\",{\"nested\":2}]}").getAsJsonObject();
        var graph = graph(encoded);
        assertTrue(ProgramEditorCachePolicy.stableGraph(graph));
        encoded.addProperty("mutable", new AtomicInteger(0));
        assertTrue(ProgramEditorCachePolicy.stableGraph(graph));
        assertFalse(ProgramEditorCachePolicy.stableGraph(graph(encoded)));
    }

    private static ProgramGraph graph(JsonObject encoded) {
        return new ProgramGraph(List.of(new ProgramGraph.Node(0, Identifier.parse("academy:test"), 1, encoded)), List.of());
    }
}
