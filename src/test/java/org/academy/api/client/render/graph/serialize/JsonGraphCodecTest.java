package org.academy.api.client.render.graph.serialize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.academy.api.client.render.graph.GraphFixtures;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.junit.jupiter.api.Test;

class JsonGraphCodecTest {
    private final SimpleNodeRegistry registry = new SimpleNodeRegistry();

    @Test
    void roundTripPreservesGraph() {
        registry.register(GraphFixtures.addType());
        registry.register(GraphFixtures.vec3AddType());
        var codec = new JsonGraphCodec(registry);

        var graph = new Graph(
                "academy:graph/test",
                List.of(
                        GraphFixtures.node(GraphFixtures.addType(), "n1", 10f, 20f),
                        GraphFixtures.node(GraphFixtures.addType(), "n2", 100f, 200f)
                ),
                List.of(
                        new Edge(new Edge.PortRef("n1", "out"), new Edge.PortRef("n2", "a"))
                ),
                List.of(
                        new GraphParameter("speed", "Speed", ValueType.FLOAT, Value.of(1f),
                                Optional.of(new GraphParameter.Range(0, 10)))
                ),
                List.of("n2")
        );

        var encoded = codec.encode(graph);
        var decoded = codec.decode(encoded);

        assertEquals(graph, decoded);
    }

    @Test
    void roundTripPreservesNodeProperties() {
        registry.register(GraphFixtures.addType());
        var codec = new JsonGraphCodec(registry);

        var graph = new Graph(
                "g",
                List.of(GraphFixtures.node(GraphFixtures.addType(), "n1", Map.of("scale", "2.0"), 0f, 0f)),
                List.of(),
                List.of(),
                List.of("n1")
        );

        assertEquals(graph, codec.decode(codec.encode(graph)));
    }

    @Test
    void roundTripPreservesCurveTangentsAndInterpolation() {
        var codec = new JsonGraphCodec(registry);
        var curve = new org.academy.api.client.render.graph.type.Curve(List.of(
                new org.academy.api.client.render.graph.type.Curve.Keyframe(
                        0f, 1f, 2f, 3f, org.academy.api.client.render.graph.type.Curve.Interpolation.BEZIER),
                org.academy.api.client.render.graph.type.Curve.Keyframe.step(1f, 5f)
        ));
        var graph = new Graph(
                "g",
                List.of(),
                List.of(),
                List.of(new GraphParameter("anim", "Anim", ValueType.CURVE, Value.curve(curve), Optional.empty())),
                List.of()
        );

        var decoded = codec.decode(codec.encode(graph));
        var back = decoded.parameters().get(0).defaultValue().asCurve();
        assertEquals(2, back.keyframes().size());
        assertEquals(0f, back.keyframes().get(0).time());
        assertEquals(2f, back.keyframes().get(0).inTangent());
        assertEquals(3f, back.keyframes().get(0).outTangent());
        assertEquals(org.academy.api.client.render.graph.type.Curve.Interpolation.BEZIER,
                back.keyframes().get(0).interpolation());
        assertEquals(org.academy.api.client.render.graph.type.Curve.Interpolation.STEP,
                back.keyframes().get(1).interpolation());
    }

    @Test
    void decodesLegacyCurveWithoutTangents() {
        var json = new JsonObject();
        json.addProperty(GraphSchemaVersion.VERSION_FIELD, GraphSchemaVersion.CURRENT);
        json.addProperty("id", "g");
        var params = new com.google.gson.JsonArray();
        var p = new JsonObject();
        p.addProperty("id", "c");
        p.addProperty("name", "C");
        p.addProperty("type", "CURVE");
        var def = new JsonObject();
        def.addProperty("type", "CURVE");
        var curveArr = new com.google.gson.JsonArray();
        var kf = new JsonObject();
        kf.addProperty("t", 0.25f);
        kf.addProperty("v", 4f);
        curveArr.add(kf);
        def.add("curve", curveArr);
        p.add("default", def);
        params.add(p);
        json.add("parameters", params);
        json.add("nodes", new com.google.gson.JsonArray());
        json.add("edges", new com.google.gson.JsonArray());
        json.add("outputs", new com.google.gson.JsonArray());

        var decoded = new JsonGraphCodec(registry).decode(json);
        var kfs = decoded.parameters().get(0).defaultValue().asCurve().keyframes();
        assertEquals(1, kfs.size());
        assertEquals(0f, kfs.get(0).inTangent());
        assertEquals(org.academy.api.client.render.graph.type.Curve.Interpolation.LINEAR, kfs.get(0).interpolation());
    }

    @Test
    void migrationChainBringsOldJsonToCurrentVersion() {
        var oldJson = new JsonObject();
        oldJson.addProperty("id", "g");

        var migration = new GraphMigration() {
            @Override
            public int fromVersion() {
                return 0;
            }

            @Override
            public JsonObject apply(JsonObject json) {
                json.addProperty(GraphSchemaVersion.VERSION_FIELD, 1);
                return json;
            }
        };

        var migrated = GraphMigrations.apply(oldJson, List.of(migration));
        assertEquals(GraphSchemaVersion.CURRENT, migrated.get(GraphSchemaVersion.VERSION_FIELD).getAsInt());
    }
}
