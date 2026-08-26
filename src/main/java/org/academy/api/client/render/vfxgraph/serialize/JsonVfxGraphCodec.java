package org.academy.api.client.render.vfxgraph.serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.serialize.JsonGraphCodec;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.*;

import java.util.*;

public final class JsonVfxGraphCodec implements VfxGraphCodec {
    private final NodeRegistry registry;

    public JsonVfxGraphCodec(NodeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public JsonObject encode(VfxSystem system) {
        var root = new JsonObject();
        root.addProperty(VfxGraphSchemaVersion.VERSION_FIELD, VfxGraphSchemaVersion.CURRENT);
        root.addProperty(VfxGraphSchemaVersion.KIND_FIELD, "vfx");
        root.addProperty("id", system.id());

        root.add("parameters", encodeParameters(system.parameters()));

        var contexts = new JsonArray();
        for (var ctx : system.contexts()) {
            var o = new JsonObject();
            o.addProperty("id", ctx.id());
            o.addProperty("type", ctx.type().name());
            o.addProperty("name", ctx.name());
            o.addProperty("x", ctx.x());
            o.addProperty("y", ctx.y());
            var blocks = new JsonArray();
            for (var block : ctx.blocks()) {
                var b = new JsonObject();
                b.addProperty("id", block.id());
                b.addProperty("type", block.type());
                b.add("properties", encodeProperties(block.properties()));
                blocks.add(b);
            }
            o.add("blocks", blocks);
            contexts.add(o);
        }
        root.add("contexts", contexts);

        var operators = new JsonArray();
        for (var op : system.operators()) {
            var o = new JsonObject();
            o.addProperty("id", op.id());
            o.addProperty("type", op.type());
            o.add("properties", encodeProperties(op.properties()));
            o.addProperty("x", op.x());
            o.addProperty("y", op.y());
            operators.add(o);
        }
        root.add("operators", operators);

        var flow = new JsonArray();
        for (var edge : system.flowEdges()) {
            var o = new JsonObject();
            o.addProperty("from", edge.fromContextId());
            o.addProperty("to", edge.toContextId());
            flow.add(o);
        }
        root.add("flow", flow);

        var blockFlows = new JsonArray();
        for (var edge : system.blockFlows()) {
            var o = new JsonObject();
            o.addProperty("from", edge.fromBlockId());
            o.addProperty("to", edge.toBlockId());
            blockFlows.add(o);
        }
        root.add("blockFlows", blockFlows);

        var dataEdges = new JsonArray();
        for (var edge : system.dataEdges()) {
            var o = new JsonObject();
            o.add("from", encodePortRef(edge.from()));
            o.add("to", encodePortRef(edge.to()));
            dataEdges.add(o);
        }
        root.add("dataEdges", dataEdges);

        var outputs = new JsonArray();
        system.outputs().forEach(outputs::add);
        root.add("outputs", outputs);

        return root;
    }

    @Override
    public VfxSystem decode(JsonObject json) {
        var id = json.get("id").getAsString();
        var parameters = decodeParameters(json.getAsJsonArray("parameters"));

        var contexts = new ArrayList<VfxContext>();
        for (var el : json.getAsJsonArray("contexts")) {
            contexts.add(decodeContext(el.getAsJsonObject()));
        }

        var operators = new ArrayList<VfxOperatorNode>();
        if (json.has("operators")) {
            for (var el : json.getAsJsonArray("operators")) {
                operators.add(decodeOperator(el.getAsJsonObject()));
            }
        }

        var flowEdges = new ArrayList<VfxFlowEdge>();
        if (json.has("flow")) {
            for (var el : json.getAsJsonArray("flow")) {
                var o = el.getAsJsonObject();
                flowEdges.add(new VfxFlowEdge(o.get("from").getAsString(), o.get("to").getAsString()));
            }
        }

        var blockFlows = new ArrayList<VfxBlockFlowEdge>();
        if (json.has("blockFlows")) {
            for (var el : json.getAsJsonArray("blockFlows")) {
                var o = el.getAsJsonObject();
                blockFlows.add(new VfxBlockFlowEdge(o.get("from").getAsString(), o.get("to").getAsString()));
            }
        }

        var dataEdges = new ArrayList<VfxDataEdge>();
        if (json.has("dataEdges")) {
            for (var el : json.getAsJsonArray("dataEdges")) {
                dataEdges.add(decodeDataEdge(el.getAsJsonObject()));
            }
        }

        var outputs = new ArrayList<String>();
        for (var el : json.getAsJsonArray("outputs")) {
            outputs.add(el.getAsString());
        }

        return new VfxSystem(id, contexts, operators, flowEdges, blockFlows, dataEdges, parameters, outputs);
    }

    private VfxContext decodeContext(JsonObject o) {
        var id = o.get("id").getAsString();
        var type = VfxContextType.valueOf(o.get("type").getAsString());
        var name = o.has("name") ? o.get("name").getAsString() : "";
        var x = o.get("x").getAsFloat();
        var y = o.get("y").getAsFloat();
        var blocks = new ArrayList<VfxBlock>();
        for (var el : o.getAsJsonArray("blocks")) {
            blocks.add(decodeBlock(el.getAsJsonObject()));
        }
        return new VfxContext(id, type, name, blocks, x, y);
    }

    private VfxBlock decodeBlock(JsonObject o) {
        var id = o.get("id").getAsString();
        var typeId = o.get("type").getAsString();
        return new VfxBlock(id, typeId, decodeProperties(o.getAsJsonObject("properties")), derivePorts(typeId));
    }

    private VfxOperatorNode decodeOperator(JsonObject o) {
        var id = o.get("id").getAsString();
        var typeId = o.get("type").getAsString();
        var x = o.get("x").getAsFloat();
        var y = o.get("y").getAsFloat();
        return new VfxOperatorNode(id, typeId, decodeProperties(o.getAsJsonObject("properties")), derivePorts(typeId), x, y);
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

    private static VfxDataEdge decodeDataEdge(JsonObject o) {
        return new VfxDataEdge(decodePortRef(o.getAsJsonObject("from")), decodePortRef(o.getAsJsonObject("to")));
    }

    private static JsonArray encodeParameters(List<GraphParameter> parameters) {
        var arr = new JsonArray();
        for (var p : parameters) {
            var o = new JsonObject();
            o.addProperty("id", p.id());
            o.addProperty("name", p.name());
            o.addProperty("type", p.type().name());
            o.add("default", JsonGraphCodec.encodeValue(p.defaultValue()));
            p.range().ifPresent(r -> {
                o.addProperty("min", r.min());
                o.addProperty("max", r.max());
            });
            arr.add(o);
        }
        return arr;
    }

    private static List<GraphParameter> decodeParameters(JsonArray arr) {
        var out = new ArrayList<GraphParameter>();
        for (var el : arr) {
            var o = el.getAsJsonObject();
            Optional<GraphParameter.Range> range = Optional.empty();
            if (o.has("min") && o.has("max")) {
                range = Optional.of(new GraphParameter.Range(o.get("min").getAsDouble(), o.get("max").getAsDouble()));
            }
            out.add(new GraphParameter(
                    o.get("id").getAsString(),
                    o.get("name").getAsString(),
                    ValueType.valueOf(o.get("type").getAsString()),
                    JsonGraphCodec.decodeValue(o.getAsJsonObject("default")),
                    range));
        }
        return out;
    }

    private static JsonObject encodeProperties(Map<String, String> properties) {
        var o = new JsonObject();
        properties.forEach(o::addProperty);
        return o;
    }

    private static Map<String, String> decodeProperties(JsonObject o) {
        Map<String, String> props = new LinkedHashMap<>();
        for (var entry : o.entrySet()) {
            props.put(entry.getKey(), entry.getValue().getAsString());
        }
        return props;
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
}
