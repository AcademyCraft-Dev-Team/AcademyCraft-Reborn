package org.academy.api.client.render.vfxgraph.serialize;

import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonVfxGraphCodecTest {
    private final SimpleNodeRegistry registry = new SimpleNodeRegistry();

    private NodeType spawnRateType() {
        return new NodeType("vfx.block.spawn_rate", "spawn", "Spawn Rate",
                List.of(
                        new PortSpec("rate", "Rate", PortDirection.INPUT, ValueType.FLOAT, Value.of(10f))
                ),
                List.of(new PropertySpec("lifetime", "Lifetime", ValueType.FLOAT, Value.of(1f), Optional.empty())));
    }

    private NodeType attrPositionType() {
        return new NodeType("vfx.op.attr_position", "attribute", "Attribute Position",
                List.of(new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.VEC3, Value.of(new Vector3f()))),
                List.of());
    }

    private NodeType mathMulType() {
        return new NodeType("vfx.op.mul", "math", "Multiply",
                List.of(
                        new PortSpec("a", "A", PortDirection.INPUT, ValueType.FLOAT, Value.of(0f)),
                        new PortSpec("b", "B", PortDirection.INPUT, ValueType.FLOAT, Value.of(0f)),
                        new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))
                ),
                List.of());
    }

    @Test
    void roundTripPreservesSystem() {
        registry.register(spawnRateType());
        registry.register(attrPositionType());
        registry.register(mathMulType());
        var codec = new JsonVfxGraphCodec(registry);

        var block = new VfxBlock("b1", "vfx.block.spawn_rate", Map.of("lifetime", "1.5"), portsOf(spawnRateType()));
        var init = new VfxContext("ctx_init", VfxContextType.INITIALIZE, "Initialize",
                List.of(new VfxBlock("b2", "vfx.block.init_velocity", Map.of(), List.of())), 200f, 0f);
        var op = new VfxOperatorNode("o1", "vfx.op.attr_position", Map.of(), portsOf(attrPositionType()), 10f, 20f);
        var system = new VfxSystem(
                "academy:vfxgraph/demo",
                List.of(
                        new VfxContext("ctx_spawn", VfxContextType.SPAWN, "Spawn", List.of(block), 0f, 0f),
                        init,
                        new VfxContext("ctx_update", VfxContextType.UPDATE, "Update", List.of(), 400f, 0f),
                        new VfxContext("ctx_out", VfxContextType.OUTPUT, "Output",
                                List.of(new VfxBlock("bout", "vfx.block.output_quad", Map.of(), List.of())), 600f, 0f)
                ),
                List.of(op),
                List.of(new VfxFlowEdge("ctx_spawn", "ctx_init"),
                        new VfxFlowEdge("ctx_init", "ctx_update"),
                        new VfxFlowEdge("ctx_update", "ctx_out")),
                List.of(new VfxDataEdge(new Edge.PortRef("o1", "out"), new Edge.PortRef("b2", "velocity"))),
                List.of(new GraphParameter("rate", "Rate", ValueType.FLOAT, Value.of(10f),
                        Optional.of(new GraphParameter.Range(0, 100)))),
                List.of("bout"));

        var decoded = codec.decode(codec.encode(system));

        assertEquals(system, decoded);
        // 端口由目录重建（与核心 GraphNode 同约定）
        assertEquals(1, decoded.contexts().getFirst().blocks().getFirst().ports().size());
        assertEquals("Rate", decoded.contexts().getFirst().blocks().getFirst().ports().getFirst().name());
        assertEquals(1, decoded.operators().getFirst().ports().size());
    }

    /**
     * 由 NodeType 派生端口（与 codec 的 derivePorts 行为一致），保证 round-trip 相等。
     */
    private static List<Port> portsOf(NodeType type) {
        return type.ports().stream()
                .map(s -> new Port(
                        s.id(), s.name(), s.direction(), s.type(), s.defaultValue()))
                .toList();
    }

    @Test
    void roundTripPreservesCurveParameter() {
        var codec = new JsonVfxGraphCodec(registry);
        var curve = new Curve(List.of(
                new Curve.Keyframe(
                        0f, 1f, 2f, 3f, Curve.Interpolation.BEZIER)));
        var system = new VfxSystem(
                "g",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new GraphParameter("anim", "Anim", ValueType.CURVE, Value.curve(curve), Optional.empty())),
                List.of());

        var decoded = codec.decode(codec.encode(system));
        var back = decoded.parameters().getFirst().defaultValue().asCurve();
        assertEquals(1, back.keyframes().size());
        assertEquals(2f, back.keyframes().getFirst().inTangent());
        assertEquals(Curve.Interpolation.BEZIER,
                back.keyframes().getFirst().interpolation());
    }

    @Test
    void encodeEmitsVfxKindAndVersion() {
        registry.register(spawnRateType());
        var codec = new JsonVfxGraphCodec(registry);
        var system = new VfxSystem("g", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var json = codec.encode(system);
        assertEquals(VfxGraphSchemaVersion.CURRENT, json.get(VfxGraphSchemaVersion.VERSION_FIELD).getAsInt());
        assertEquals("vfx", json.get(VfxGraphSchemaVersion.KIND_FIELD).getAsString());
    }

    /**
     * M28b：块级 flow round-trip。
     */
    @Test
    void roundTripPreservesBlockFlows() {
        var codec = new JsonVfxGraphCodec(registry);
        var system = new VfxSystem(
                "bf",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "", List.of(
                                new VfxBlock("s1", "vfx.block.spawn_rate", Map.of(), List.of()),
                                new VfxBlock("s2", "vfx.block.spawn_rate", Map.of(), List.of())), 0f, 0f),
                        new VfxContext("init", VfxContextType.INITIALIZE, "", List.of(
                                new VfxBlock("i1", "vfx.block.init_velocity", Map.of(), List.of()),
                                new VfxBlock("i2", "vfx.block.init_velocity", Map.of(), List.of())), 0f, 0f)
                ),
                List.of(),
                List.of(),
                List.of(
                        new VfxBlockFlowEdge("s1", "i1"),
                        new VfxBlockFlowEdge("s2", "i2")),
                List.of(),
                List.of(),
                List.of());

        var decoded = codec.decode(codec.encode(system));
        assertEquals(2, decoded.blockFlows().size());
        assertEquals("s1", decoded.blockFlows().get(0).fromBlockId());
        assertEquals("i2", decoded.blockFlows().get(1).toBlockId());
    }
}
