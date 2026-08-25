package org.academy.api.client.render.graph;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.joml.Vector3f;

/**
 * 测试夹具：提供常用节点类型与图节点构造，保证与解码逻辑（由 NodeType 派生端口）一致。
 */
public final class GraphFixtures {
    private GraphFixtures() {
    }

    public static NodeType addType() {
        return new NodeType(
                "math.add", "math", "Add",
                List.of(
                        new PortSpec("a", "A", PortDirection.INPUT, ValueType.FLOAT, Value.of(0f)),
                        new PortSpec("b", "B", PortDirection.INPUT, ValueType.FLOAT, Value.of(0f)),
                        new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))
                ),
                List.of()
        );
    }

    public static NodeType vec3AddType() {
        return new NodeType(
                "math.add_vec3", "math", "Add Vec3",
                List.of(
                        new PortSpec("a", "A", PortDirection.INPUT, ValueType.VEC3, Value.of(new Vector3f())),
                        new PortSpec("b", "B", PortDirection.INPUT, ValueType.VEC3, Value.of(new Vector3f())),
                        new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.VEC3, Value.of(new Vector3f()))
                ),
                List.of()
        );
    }

    public static NodeType samplerInputType() {
        return new NodeType(
                "input.tex", "input", "Texture",
                List.of(
                        new PortSpec("tex", "Tex", PortDirection.INPUT, ValueType.SAMPLER, Value.sampler("minecraft:textures/misc/white.png")),
                        new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))
                ),
                List.of()
        );
    }

    public static NodeType constantType() {
        return new NodeType(
                "input.constant", "input", "Constant",
                List.of(
                        new PortSpec("out", "Out", PortDirection.OUTPUT, ValueType.FLOAT, Value.of(0f))
                ),
                List.of(new PropertySpec("value", "Value", ValueType.FLOAT, Value.of(0f), java.util.Optional.empty()))
        );
    }

    /** 由 NodeType 派生端口构造节点，与 {@code JsonGraphCodec#derivePorts} 行为一致。 */
    public static GraphNode node(NodeType type, String id, float x, float y) {
        return node(type, id, Map.of(), x, y);
    }

    public static GraphNode node(NodeType type, String id, Map<String, String> properties, float x, float y) {
        var ports = type.ports().stream()
                .map(s -> new Port(s.id(), s.name(), s.direction(), s.type(), s.defaultValue()))
                .toList();
        return new GraphNode(id, type.id(), properties, ports, x, y);
    }
}
