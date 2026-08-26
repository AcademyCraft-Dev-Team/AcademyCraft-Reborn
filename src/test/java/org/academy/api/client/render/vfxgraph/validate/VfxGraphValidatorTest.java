package org.academy.api.client.render.vfxgraph.validate;

import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.graph.validate.GraphIssue;
import org.academy.api.client.render.vfxgraph.model.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphValidatorTest {
    private SimpleNodeRegistry registry;
    private VfxGraphValidator validator;

    @BeforeEach
    void setUp() {
        registry = new SimpleNodeRegistry();
        registry.register(new NodeType("vfx.block.spawn_rate", "spawn", "Spawn Rate", List.of(), List.of()));
        registry.register(new NodeType("vfx.block.output_quad", "output", "Output Quad", List.of(), List.of()));
        registry.register(new NodeType("vfx.op.attr_position", "attribute", "Attribute Position",
                List.of(new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.VEC3, Value.of(new Vector3f()))),
                List.of()));
        validator = new VfxGraphValidator(registry);
    }

    private VfxSystem validSystem() {
        return new VfxSystem(
                "demo",
                List.of(
                        new VfxContext("ctx_spawn", VfxContextType.SPAWN, "", List.of(
                                new VfxBlock("b_spawn", "vfx.block.spawn_rate", Map.of(), List.of())), 0f, 0f),
                        new VfxContext("ctx_init", VfxContextType.INITIALIZE, "", List.of(), 0f, 0f),
                        new VfxContext("ctx_out", VfxContextType.OUTPUT, "", List.of(
                                new VfxBlock("b_out", "vfx.block.output_quad", Map.of(), List.of())), 0f, 0f)
                ),
                List.of(),
                List.of(
                        new VfxFlowEdge("ctx_spawn", "ctx_init"),
                        new VfxFlowEdge("ctx_init", "ctx_out")),
                List.of(),
                List.of(),
                List.of("b_out"));
    }

    private boolean hasError(List<GraphIssue> issues, String fragment) {
        return issues.stream().anyMatch(i -> i.severity() == GraphIssue.Severity.ERROR
                && i.message().contains(fragment));
    }

    @Test
    void validSystemProducesNoErrors() {
        assertTrue(validator.validate(validSystem()).isEmpty());
    }

    @Test
    void missingSpawnOrOutputIsError() {
        var noSpawn = new VfxSystem("g",
                List.of(new VfxContext("c", VfxContextType.UPDATE, "", List.of(), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertTrue(hasError(validator.validate(noSpawn), "no SPAWN context"));

        var noOutput = new VfxSystem("g",
                List.of(new VfxContext("c", VfxContextType.SPAWN, "", List.of(), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertTrue(hasError(validator.validate(noOutput), "no OUTPUT context"));
    }

    @Test
    void nonSpawnContextWithoutUpstreamIsError() {
        var system = new VfxSystem("g",
                List.of(
                        new VfxContext("ctx_spawn", VfxContextType.SPAWN, "", List.of(), 0f, 0f),
                        new VfxContext("ctx_update", VfxContextType.UPDATE, "", List.of(), 0f, 0f),
                        new VfxContext("ctx_out", VfxContextType.OUTPUT, "", List.of(), 0f, 0f)),
                List.of(),
                List.of(new VfxFlowEdge("ctx_spawn", "ctx_out")),
                List.of(), List.of(), List.of("missing"));
        var issues = validator.validate(system);
        assertTrue(hasError(issues, "no upstream flow"), "update context must have upstream flow");
    }

    @Test
    void flowCycleIsError() {
        var system = new VfxSystem("g",
                List.of(
                        new VfxContext("a", VfxContextType.SPAWN, "", List.of(), 0f, 0f),
                        new VfxContext("b", VfxContextType.UPDATE, "", List.of(), 0f, 0f)),
                List.of(),
                List.of(new VfxFlowEdge("a", "b"), new VfxFlowEdge("b", "a")),
                List.of(), List.of(), List.of());
        assertTrue(hasError(validator.validate(system), "flow cycle"));
    }

    @Test
    void dataEdgeTypeMismatchIsError() {
        var op = new VfxOperatorNode("o1", "vfx.op.attr_position", Map.of(),
                List.of(new Port(
                        "out", "Out", PortDirection.OUTPUT, ValueType.VEC3, Value.of(new Vector3f()))),
                0f, 0f);
        var system = new VfxSystem("g",
                List.of(
                        new VfxContext("ctx_spawn", VfxContextType.SPAWN, "", List.of(), 0f, 0f),
                        new VfxContext("ctx_out", VfxContextType.OUTPUT, "", List.of(
                                new VfxBlock("b_out", "vfx.block.output_quad", Map.of(), List.of())), 0f, 0f)),
                List.of(op),
                List.of(new VfxFlowEdge("ctx_spawn", "ctx_out")),
                // attr_position(VEC3 out) → output_quad 无输入端口 → "to missing port"
                List.of(new VfxDataEdge(new Edge.PortRef("o1", "out"), new Edge.PortRef("b_out", "nonexistent"))),
                List.of(),
                List.of("b_out"));
        var issues = validator.validate(system);
        assertTrue(hasError(issues, "to missing port"));
    }

    @Test
    void unknownNodeTypeIsError() {
        var system = new VfxSystem("g",
                List.of(new VfxContext("c", VfxContextType.SPAWN, "", List.of(
                        new VfxBlock("b", "vfx.block.missing", Map.of(), List.of())), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertTrue(hasError(validator.validate(system), "unknown node type"));
    }

    @Test
    void duplicateNodeIdIsError() {
        var system = new VfxSystem("g",
                List.of(
                        new VfxContext("c1", VfxContextType.SPAWN, "", List.of(
                                new VfxBlock("b", "vfx.block.spawn_rate", Map.of(), List.of())), 0f, 0f),
                        new VfxContext("c2", VfxContextType.INITIALIZE, "", List.of(
                                new VfxBlock("b", "vfx.block.spawn_rate", Map.of(), List.of())), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertTrue(hasError(validator.validate(system), "duplicate node id"));
    }

    @Test
    void missingOutputIsError() {
        var system = new VfxSystem("g",
                List.of(new VfxContext("c", VfxContextType.OUTPUT, "", List.of(), 0f, 0f)),
                List.of(), List.of(), List.of(), List.of(), List.of("missing"));
        assertTrue(hasError(validator.validate(system), "output references missing node"));
    }
}
