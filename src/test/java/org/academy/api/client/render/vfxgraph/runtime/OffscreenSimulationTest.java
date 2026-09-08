package org.academy.api.client.render.vfxgraph.runtime;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OffscreenSimulationTest {
    private static ActiveEffect effect() {
        var registry = new VfxNodeRegistry();
        VfxNodes.registerAll(new SimpleNodeRegistry(), registry);
        var graph = new Graph("offscreen", List.of(new GraphNode("out", "vfx.output_quad",
                Map.of(), List.of(), 0, 0)), List.of(), List.of(), List.of("out"));
        return new ActiveEffect("test", graph, registry, new Vector3f());
    }

    @Test
    void offscreenWorkIsReducedWithoutLosingAccumulatedTime() {
        var hidden = effect();
        var visible = effect();
        int hiddenSteps = 0, visibleSteps = 0;
        float hiddenTime = 0;
        for (int frame = 0; frame < 120; frame++) {
            float dt = hidden.simulationStep(1f / 60, false);
            if (dt > 0) hiddenSteps++;
            hiddenTime += dt;
            if (visible.simulationStep(1f / 60, true) > 0) visibleSteps++;
        }
        assertEquals(120, visibleSteps);
        assertTrue(hiddenSteps >= 9 && hiddenSteps <= 10);
        hiddenTime += hidden.simulationStep(1f / 60, true);
        assertEquals(121f / 60, hiddenTime, 0.0001f);
    }

    @Test
    void invisibleEffectsStillRetireWhenStopped() {
        var hidden = effect();
        assertEquals(0, hidden.simulationStep(1f / 60, false));
        hidden.stop();
        assertTrue(hidden.updateState());
    }
}
