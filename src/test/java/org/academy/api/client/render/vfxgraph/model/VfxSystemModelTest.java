package org.academy.api.client.render.vfxgraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VfxSystemModelTest {
    @Test
    void buildsAndQueriesContainerModel() {
        var block = new VfxBlock("b1", "vfx.block.spawn_rate", Map.of("rate", "10"), List.of());
        var context = new VfxContext("ctx_spawn", VfxContextType.SPAWN, "Spawn", List.of(block), 0f, 0f);
        var op = new VfxOperatorNode("o1", "vfx.op.attr_position", Map.of(), List.of(), 10f, 20f);
        var system = new VfxSystem(
                "demo",
                List.of(context),
                List.of(op),
                List.of(new VfxFlowEdge("ctx_spawn", "ctx_init")),
                List.of(),
                List.of(),
                List.of("b1"));

        assertEquals("demo", system.id());
        assertEquals(2, system.nodes().size());
        assertEquals(block, system.findNode("b1"));
        assertEquals(op, system.findNode("o1"));
        assertNull(system.findNode("missing"));
        assertEquals(context, system.findContext("ctx_spawn"));
        assertNull(system.findContext("missing"));
    }

    @Test
    void contextDisplayNameFallsBackToStageName() {
        assertEquals("SPAWN", new VfxContext("c", VfxContextType.SPAWN, "", List.of(), 0f, 0f).displayName());
        assertEquals("My Spawn",
                new VfxContext("c", VfxContextType.SPAWN, "My Spawn", List.of(), 0f, 0f).displayName());
    }

    @Test
    void particleAttributeTypesMatchParticleBufferSemantics() {
        assertEquals(org.academy.api.client.render.graph.type.ValueType.VEC3, ParticleAttribute.POSITION.valueType());
        assertEquals(org.academy.api.client.render.graph.type.ValueType.VEC3, ParticleAttribute.VELOCITY.valueType());
        assertEquals(org.academy.api.client.render.graph.type.ValueType.FLOAT, ParticleAttribute.SIZE.valueType());
        assertEquals(org.academy.api.client.render.graph.type.ValueType.COLOR, ParticleAttribute.COLOR.valueType());
        assertEquals(org.academy.api.client.render.graph.type.ValueType.FLOAT, ParticleAttribute.ALPHA.valueType());
        assertEquals(3, ParticleAttribute.POSITION.channels());
        assertEquals(1, ParticleAttribute.LIFETIME.channels());
    }

    @Test
    void defensiveCopiesAreImmutable() {
        var block = new VfxBlock("b1", "t", new java.util.HashMap<>(Map.of("rate", "10")), new java.util.ArrayList<>());
        assertEquals(1, block.properties().size());
        // Map.copyOf/List.copyOf 是防御拷贝：对返回的不可变视图修改必须抛异常
        assertThrows(UnsupportedOperationException.class, () -> block.properties().put("x", "1"));
        assertThrows(UnsupportedOperationException.class, () -> block.ports().add(null));
    }
}
