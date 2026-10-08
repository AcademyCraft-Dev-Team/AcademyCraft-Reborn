package org.academy.api.client.render.post;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DirectionalFieldStyleTest {
    @Test void graphDefaultsAndLiveEditsControlSceneOutputWithoutRebuildingTheEffect() {
        var graph = new Graph("field", List.of(), List.of(), List.of(
                parameter("field_surface_strength", 0.12f), parameter("field_air_strength", 0.08f),
                parameter("field_edge_width", 0.4f), parameter("field_scatter_count", 24)), List.of());
        var effect = new GraphEffect(graph, new VfxNodeRegistry());
        assertEquals(new DirectionalFieldStyle(0.12f, 0.08f, 0.4f, 24), DirectionalFieldStyle.fromGraph(effect));
        effect.setLiveParam("field_surface_strength", Value.of(0.21f));
        effect.setLiveParam("field_scatter_count", Value.of(48));
        assertEquals(0.21f, DirectionalFieldStyle.fromGraph(effect).surfaceStrength());
        assertEquals(48, DirectionalFieldStyle.fromGraph(effect).scatterSamples());
        effect.setLiveParam("field_air_strength", Value.of(Float.NaN));
        assertEquals(0.10f, DirectionalFieldStyle.fromGraph(effect).airStrength());
    }

    @Test void unsafeGraphEditsAreBoundedAndPhaseMixRemainsWhiteAtControlledBrightness() {
        var style = new DirectionalFieldStyle(12, -2, Float.POSITIVE_INFINITY, 500);
        assertEquals(0.24f, style.surfaceStrength());
        assertEquals(0, style.airStrength());
        assertEquals(0.22f, style.edgeWidth());
        assertEquals(48, style.scatterSamples());
        var alpha = DirectionalFieldStyle.DEFAULT.phase(1, 0);
        var beta = DirectionalFieldStyle.DEFAULT.phase(0, 1);
        assertTrue(alpha.airStrength() > beta.airStrength());
        assertTrue(beta.surfaceStrength() > alpha.surfaceStrength());
        assertEquals(DirectionalFieldStyle.DEFAULT.phase(0.5f, 0.5f),
                DirectionalFieldStyle.DEFAULT.phase(Float.NaN, Float.NaN));
    }

    private static GraphParameter parameter(String id, float value) {
        return new GraphParameter(id, id, ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
