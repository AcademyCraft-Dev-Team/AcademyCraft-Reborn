package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

import java.util.List;
import java.util.Optional;

public final class VfxBlockTypes {
    private VfxBlockTypes() {
    }

    public static NodeType type(String id, String category, String name, List<PropertySpec> props) {
        return new NodeType(id, category, name, List.of(), props);
    }

    public static NodeType typeWithPorts(String id, String category, String name, List<PortSpec> ports,
                                         List<PropertySpec> props) {
        return new NodeType(id, category, name, List.copyOf(ports), List.copyOf(props));
    }

    public static PortSpec in(String id, String name, ValueType type) {
        return new PortSpec(id, name, PortDirection.INPUT, type,
                switch (type) {
                    case FLOAT, TIME, INT -> Value.of(0f);
                    case COLOR -> Value.color(1f, 1f, 1f, 1f);
                    default -> Value.of(0f);
                });
    }

    public static PropertySpec prop(String id, ValueType type, Value def) {
        return new PropertySpec(id, id, type, def, Optional.empty());
    }
}
