package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;

final class ParamNodes {
    private ParamNodes() {
    }

    static SimNode floatParam(GraphNode node) {
        var properties = node.properties();
        var param = BlockProps.getString(properties, "param", "");
        var value = BlockProps.getFloat(properties, "value", 0f);
        return (buf, ctx) -> {
            if (param.isEmpty() || ctx.param(param) != null) return;
            ctx.paramIfAbsent(param, Value.of(value));
        };
    }

    static SimNode vec3(GraphNode node) {
        var properties = node.properties();
        var param = BlockProps.getString(properties, "param", "");
        var x = BlockProps.getFloat(properties, "x", 0f);
        var y = BlockProps.getFloat(properties, "y", 0f);
        var z = BlockProps.getFloat(properties, "z", 0f);
        return (buf, ctx) -> {
            if (param.isEmpty() || ctx.param(param) != null) return;
            ctx.paramIfAbsent(param, Value.of(new Vector3f(x, y, z)));
        };
    }

    static SimNode color(GraphNode node) {
        var properties = node.properties();
        var param = BlockProps.getString(properties, "param", "");
        var r = BlockProps.getFloat(properties, "r", 1f);
        var g = BlockProps.getFloat(properties, "g", 1f);
        var b = BlockProps.getFloat(properties, "b", 1f);
        var a = BlockProps.getFloat(properties, "a", 1f);
        return (buf, ctx) -> {
            if (param.isEmpty() || ctx.param(param) != null) return;
            ctx.paramIfAbsent(param, Value.color(r, g, b, a));
        };
    }

    static SimNode curve(GraphNode node) {
        var properties = node.properties();
        var param = BlockProps.getString(properties, "param", "");
        var curveId = BlockProps.getString(properties, "curve", "");
        return (buf, ctx) -> {
            if (param.isEmpty() || ctx.curve(param) != null) return;
            var source = ctx.curve(curveId);
            if (source != null) {
                ctx.curveIfAbsent(param, source);
            }
        };
    }

    static SimNode gradient(GraphNode node) {
        var properties = node.properties();
        var param = BlockProps.getString(properties, "param", "");
        var gradientId = BlockProps.getString(properties, "gradient", "");
        return (buf, ctx) -> {
            if (param.isEmpty() || ctx.gradient(param) != null) return;
            var source = ctx.gradient(gradientId);
            if (source != null) {
                ctx.gradientIfAbsent(param, source);
            }
        };
    }
}
