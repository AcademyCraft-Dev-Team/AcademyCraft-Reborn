package org.academy.api.client.render.vfxgraph;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphEffectParameterTest {
    @Test void analyticOutputsReadBlackboardDefaultsAndCurrentLiveOverrides() {
        var graph = new Graph("parameters", List.of(), List.of(),
                List.of(parameter("width", Value.of(0.7f)), parameter("samples", Value.of(12)),
                        parameter("clock", new Value.TimeVal(0.5f))), List.of());
        var effect = new GraphEffect(graph, new VfxNodeRegistry());
        assertEquals(0.7f, effect.floatParameter("width", 0));
        assertEquals(12, effect.floatParameter("samples", 0));
        assertEquals(0.5f, effect.floatParameter("clock", 0));
        effect.setLiveParam("width", Value.of(0.2f));
        assertEquals(0.2f, effect.floatParameter("width", 0));
        effect.setLiveParam("width", Value.of(Float.NaN));
        assertEquals(0.9f, effect.floatParameter("width", 0.9f));
        assertEquals(0.9f, effect.floatParameter("absent", 0.9f));
    }

    private static GraphParameter parameter(String id, Value value) {
        return new GraphParameter(id, id, value.type(), value, Optional.empty());
    }
}
