package org.academy.api.client.render.graph.subgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.academy.api.client.render.graph.model.Graph;
import org.junit.jupiter.api.Test;

class SubGraphRegistryTest {
    @Test
    void registerAndFind() {
        var registry = new SubGraphRegistry();
        var graph = new Graph("sub", List.of(), List.of(), List.of(), List.of());
        registry.register("sub1", graph);
        assertEquals(graph, registry.find("sub1"));
        assertNull(registry.find("missing"));
    }

    @Test
    void overwriteUpdatesEntry() {
        var registry = new SubGraphRegistry();
        registry.register("sub1", new Graph("a", List.of(), List.of(), List.of(), List.of()));
        var updated = new Graph("b", List.of(), List.of(), List.of(), List.of());
        registry.register("sub1", updated);
        assertEquals(updated, registry.find("sub1"));
    }

    @Test
    void clearRemovesAll() {
        var registry = new SubGraphRegistry();
        registry.register("sub1", new Graph("a", List.of(), List.of(), List.of(), List.of()));
        registry.clear();
        assertNull(registry.find("sub1"));
    }
}
