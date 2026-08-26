package org.academy.api.client.render.graph.model;

import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphModelTest {
    @Test
    void graphCopiesCollections() {
        var nodes = new ArrayList<>(List.of(GraphFixtures.node(GraphFixtures.addType(), "n1", 0f, 0f)));
        var graph = new Graph("g", nodes, List.of(), List.of(), List.of("n1"));

        nodes.clear();
        assertEquals(1, graph.nodes().size());
    }

    @Test
    void nodeCopiesPortsAndProperties() {
        var ports = new ArrayList<Port>();
        var node = new GraphNode("n1", "t", Map.of("k", "v"), ports, 1f, 2f);

        ports.add(new Port("p", "P", PortDirection.INPUT,
                ValueType.FLOAT,
                Value.of(0f)));

        assertTrue(node.ports().isEmpty());
        assertEquals("v", node.properties().get("k"));
    }

    @Test
    void parameterRangeDefaultsToEmpty() {
        var p = new GraphParameter("id", "name",
                ValueType.FLOAT,
                Value.of(1f), null);

        assertTrue(p.range().isEmpty());
    }

    @Test
    void portRefIsStructurallyEqual() {
        assertEquals(new Edge.PortRef("a", "b"), new Edge.PortRef("a", "b"));
    }
}
