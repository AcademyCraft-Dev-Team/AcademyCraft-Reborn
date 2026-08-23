package org.academy.api.client.render.graph.model;

import java.util.List;
import java.util.Optional;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

/**
 * 黑板参数（契约）。图暴露给使用方的可调参数。
 */
public record GraphParameter(
        String id,
        String name,
        ValueType type,
        Value defaultValue,
        Optional<Range> range
) {
    public GraphParameter {
        range = range == null ? Optional.empty() : range;
    }

    public record Range(double min, double max) {
    }
}
