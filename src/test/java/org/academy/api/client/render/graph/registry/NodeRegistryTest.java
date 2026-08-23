package org.academy.api.client.render.graph.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.academy.api.client.render.graph.GraphFixtures;
import org.junit.jupiter.api.Test;

class NodeRegistryTest {
    @Test
    void registerAndFind() {
        var registry = new SimpleNodeRegistry();
        registry.register(GraphFixtures.addType());

        assertNotNull(registry.find("math.add"));
        assertNull(registry.find("missing"));
        assertEquals(1, registry.all().size());
    }

    @Test
    void duplicateIdThrows() {
        var registry = new SimpleNodeRegistry();
        registry.register(GraphFixtures.addType());
        assertThrows(IllegalStateException.class, () -> registry.register(GraphFixtures.addType()));
    }

    @Test
    void reRegisterSameInstanceIsIdempotent() {
        var registry = new SimpleNodeRegistry();
        var type = GraphFixtures.addType();
        registry.register(type);
        registry.register(type);
        assertTrue(registry.all().contains(type));
    }
}
