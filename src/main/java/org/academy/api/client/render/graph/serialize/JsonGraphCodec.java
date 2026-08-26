package org.academy.api.client.render.graph.serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.academy.api.client.render.graph.model.*;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

/**
 * 基于 Gson 的图编解码器（契约实现）。
 *
 * <p>节点端口不序列化（由 {@link org.academy.api.client.render.graph.registry.NodeType} 派生），节点仅存 id/type/properties/x/y，
 * 保证目录是端口规格的唯一事实源。解码时经 {@link #registry} 重建端口。</p>
 */
public final class JsonGraphCodec implements GraphCodec {
    private final NodeRegistry registry;
    private final List<GraphMigration> migrations;

    public JsonGraphCodec(NodeRegistry registry) {
        this(registry, List.of());
    }

    public JsonGraphCodec(NodeRegistry registry, List<GraphMigration> migrations) {
        this.registry = registry;
        this.migrations = migrations;
    }

    @Override
    public JsonObject encode(Graph graph) {
        var root = new JsonObject();
        root.addProperty(GraphSchemaVersion.VERSION_FIELD, GraphSchemaVersion.CURRENT);
        root.addProperty("id", graph.id());

        var params = new JsonArray();
        for (var p : graph.parameters()) {
            var o = new JsonObject();
            o.addProperty("id", p.id());
            o.addProperty("name", p.name());
            o.addProperty("type", p.type().name());
            o.add("default", encodeValue(p.defaultValue()));
            p.range().ifPresent(r -> {
                o.addProperty("min", r.min());
                o.addProperty("max", r.max());
            });
            params.add(o);
        }
        root.add("parameters", params);

        var nodes = new JsonArray();
        for (var n : graph.nodes()) {
            var o = new JsonObject();
            o.addProperty("id", n.id());
            o.addProperty("type", n.type());
            o.addProperty("x", n.x());
            o.addProperty("y", n.y());
            var props = new JsonObject();
            n.properties().forEach(props::addProperty);
            o.add("properties", props);
            nodes.add(o);
        }
        root.add("nodes", nodes);

        var edges = new JsonArray();
        for (var e : graph.edges()) {
            var o = new JsonObject();
            o.add("from", encodePortRef(e.from()));
            o.add("to", encodePortRef(e.to()));
            edges.add(o);
        }
        root.add("edges", edges);

        var outputs = new JsonArray();
        graph.outputs().forEach(outputs::add);
        root.add("outputs", outputs);

        return root;
    }

    @Override
    public Graph decode(JsonObject json) {
        var migrated = GraphMigrations.apply(json, migrations);
        var id = migrated.get("id").getAsString();

        var params = new ArrayList<GraphParameter>();
        for (var el : migrated.getAsJsonArray("parameters")) {
            params.add(decodeParameter(el.getAsJsonObject()));
        }

        var nodes = new ArrayList<GraphNode>();
        for (var el : migrated.getAsJsonArray("nodes")) {
            nodes.add(decodeNode(el.getAsJsonObject()));
        }

        var edges = new ArrayList<Edge>();
        for (var el : migrated.getAsJsonArray("edges")) {
            edges.add(decodeEdge(el.getAsJsonObject()));
        }

        var outputs = new ArrayList<String>();
        for (var el : migrated.getAsJsonArray("outputs")) {
            outputs.add(el.getAsString());
        }

        return new Graph(id, nodes, edges, params, outputs);
    }

    private GraphNode decodeNode(JsonObject o) {
        var id = o.get("id").getAsString();
        var typeId = o.get("type").getAsString();
        var x = o.get("x").getAsFloat();
        var y = o.get("y").getAsFloat();

        Map<String, String> props = new LinkedHashMap<>();
        var propsEl = o.get("properties");
        if (propsEl != null && propsEl.isJsonObject()) {
            for (var entry : propsEl.getAsJsonObject().entrySet()) {
                props.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return new GraphNode(id, typeId, props, derivePorts(typeId), x, y);
    }

    private List<Port> derivePorts(String typeId) {
        var type = registry.find(typeId);
        if (type == null) {
            return List.of();
        }
        var ports = new ArrayList<Port>(type.ports().size());
        for (var spec : type.ports()) {
            ports.add(new Port(spec.id(), spec.name(), spec.direction(), spec.type(), spec.defaultValue()));
        }
        return ports;
    }

    private GraphParameter decodeParameter(JsonObject o) {
        var id = o.get("id").getAsString();
        var name = o.get("name").getAsString();
        var type = ValueType.valueOf(o.get("type").getAsString());
        var defaultValue = decodeValue(o.getAsJsonObject("default"));
        Optional<GraphParameter.Range> range = Optional.empty();
        if (o.has("min") && o.has("max")) {
            range = Optional.of(new GraphParameter.Range(o.get("min").getAsDouble(), o.get("max").getAsDouble()));
        }
        return new GraphParameter(id, name, type, defaultValue, range);
    }

    private Edge decodeEdge(JsonObject o) {
        return new Edge(decodePortRef(o.getAsJsonObject("from")), decodePortRef(o.getAsJsonObject("to")));
    }

    private static JsonObject encodePortRef(Edge.PortRef ref) {
        var o = new JsonObject();
        o.addProperty("nodeId", ref.nodeId());
        o.addProperty("portId", ref.portId());
        return o;
    }

    private static Edge.PortRef decodePortRef(JsonObject o) {
        return new Edge.PortRef(o.get("nodeId").getAsString(), o.get("portId").getAsString());
    }

    /**
     * 共享值编码（供 VFX 容器图 codec 复用，M23）。
     */
    public static JsonObject encodeValue(Value v) {
        var o = new JsonObject();
        o.addProperty("type", v.type().name());
        switch (v.type()) {
            case FLOAT -> o.addProperty("value", v.asFloat());
            case INT -> o.addProperty("value", v.asInt());
            case BOOL -> o.addProperty("value", v.asBool());
            case VEC2 -> {
                var x = v.asVec2();
                o.addProperty("x", x.x);
                o.addProperty("y", x.y);
            }
            case VEC3 -> {
                var x = v.asVec3();
                o.addProperty("x", x.x);
                o.addProperty("y", x.y);
                o.addProperty("z", x.z);
            }
            case VEC4 -> {
                var x = v.asVec4();
                o.addProperty("x", x.x);
                o.addProperty("y", x.y);
                o.addProperty("z", x.z);
                o.addProperty("w", x.w);
            }
            case COLOR -> {
                var x = v.asColor();
                o.addProperty("r", x.x);
                o.addProperty("g", x.y);
                o.addProperty("b", x.z);
                o.addProperty("a", x.w);
            }
            case SAMPLER -> o.addProperty("path", v.asSampler());
            case TIME -> o.addProperty("value", v.asFloat());
            case CURVE -> o.add("curve", encodeCurve(v.asCurve()));
            case GRADIENT -> o.add("gradient", encodeGradient(v.asGradient()));
            case MESH -> o.addProperty("path", v.asMesh());
            case STRING -> o.addProperty("value", v.asString());
        }
        return o;
    }

    /**
     * 共享值解码（供 VFX 容器图 codec 复用，M23）。
     */
    public static Value decodeValue(JsonObject o) {
        var type = ValueType.valueOf(o.get("type").getAsString());
        return switch (type) {
            case FLOAT -> Value.of(o.get("value").getAsFloat());
            case INT -> Value.of(o.get("value").getAsInt());
            case BOOL -> Value.of(o.get("value").getAsBoolean());
            case VEC2 -> Value.of(new Vector2f(o.get("x").getAsFloat(), o.get("y").getAsFloat()));
            case VEC3 -> Value.of(new Vector3f(
                    o.get("x").getAsFloat(), o.get("y").getAsFloat(), o.get("z").getAsFloat()));
            case VEC4 -> Value.of(new Vector4f(
                    o.get("x").getAsFloat(), o.get("y").getAsFloat(),
                    o.get("z").getAsFloat(), o.get("w").getAsFloat()));
            case COLOR -> Value.color(
                    o.get("r").getAsFloat(), o.get("g").getAsFloat(),
                    o.get("b").getAsFloat(), o.get("a").getAsFloat());
            case SAMPLER -> Value.sampler(o.get("path").getAsString());
            case TIME -> Value.of(o.get("value").getAsFloat());
            case CURVE -> Value.curve(decodeCurve(o.getAsJsonArray("curve")));
            case GRADIENT -> Value.gradient(decodeGradient(o.getAsJsonArray("gradient")));
            case MESH -> Value.mesh(o.get("path").getAsString());
            case STRING -> Value.string(o.get("value").getAsString());
        };
    }

    private static JsonArray encodeCurve(Curve curve) {
        var arr = new JsonArray();
        for (var kf : curve.keyframes()) {
            var o = new JsonObject();
            o.addProperty("t", kf.time());
            o.addProperty("v", kf.value());
            o.addProperty("it", kf.inTangent());
            o.addProperty("ot", kf.outTangent());
            o.addProperty("i", kf.interpolation().name());
            arr.add(o);
        }
        return arr;
    }

    private static Curve decodeCurve(JsonArray arr) {
        var kfs = new ArrayList<Curve.Keyframe>();
        for (var el : arr) {
            var o = el.getAsJsonObject();
            var interpolation = o.has("i") ? Curve.Interpolation.valueOf(o.get("i").getAsString()) : Curve.Interpolation.LINEAR;
            kfs.add(new Curve.Keyframe(
                    o.get("t").getAsFloat(), o.get("v").getAsFloat(),
                    o.has("it") ? o.get("it").getAsFloat() : 0f,
                    o.has("ot") ? o.get("ot").getAsFloat() : 0f,
                    interpolation));
        }
        return new Curve(kfs);
    }

    private static JsonArray encodeGradient(Gradient gradient) {
        var arr = new JsonArray();
        for (var stop : gradient.stops()) {
            var o = new JsonObject();
            o.addProperty("p", stop.position());
            o.addProperty("r", stop.r());
            o.addProperty("g", stop.g());
            o.addProperty("b", stop.b());
            o.addProperty("a", stop.a());
            arr.add(o);
        }
        return arr;
    }

    private static Gradient decodeGradient(JsonArray arr) {
        var stops = new ArrayList<Gradient.ColorStop>();
        for (var el : arr) {
            var o = el.getAsJsonObject();
            stops.add(new Gradient.ColorStop(
                    o.get("p").getAsFloat(), o.get("r").getAsFloat(), o.get("g").getAsFloat(),
                    o.get("b").getAsFloat(), o.get("a").getAsFloat()));
        }
        return new Gradient(stops);
    }
}
