package org.academy.api.client.render.graph.registry;

import java.util.Optional;
import org.academy.api.client.render.graph.model.GraphParameter.Range;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

/**
 * 节点属性规格（契约）。编辑器据此渲染属性编辑控件。
 */
public record PropertySpec(String id, String name, ValueType type, Value defaultValue, Optional<Range> range) {
    public PropertySpec {
        range = range == null ? Optional.empty() : range;
    }
}
