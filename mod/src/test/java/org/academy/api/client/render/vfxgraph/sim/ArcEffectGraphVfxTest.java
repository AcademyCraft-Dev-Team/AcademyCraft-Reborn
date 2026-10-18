package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ArcEffectGraphVfxTest {
    private VfxSystemSimulator simulator() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/arc_effect.json")) {
            assertNotNull(stream);
            var system = new JsonVfxGraphCodec(metadata).decode(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            return new VfxSystemSimulator(system, blocks, 42, system.parameters());
        }
    }

    private static void at(VfxSystemSimulator sim, float seconds) {
        sim.setLiveParam("time", Value.of(seconds));
        sim.step(0);
    }

    @Test
    void assetDecodesAndSimulatesPreviewPaths() throws Exception {
        var sim = simulator();
        at(sim, 0f);
        assertEquals(8, sim.arcBuffer().count());
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            assertTrue(arc.size() >= 2);
            for (int p = 0; p < arc.size(); p++) {
                assertTrue(Float.isFinite(arc.x(p)) && Float.isFinite(arc.y(p)) && Float.isFinite(arc.z(p)));
            }
        }
    }

    @Test
    void previewGeometryVariesWithTime() throws Exception {
        var sim = simulator();
        at(sim, 0f);
        var first = sim.arcBuffer().arc(0);
        float x = first.x(first.size() - 1);
        float y = first.y(first.size() - 1);
        at(sim, 0.1f);
        var rotated = sim.arcBuffer().arc(0);
        assertTrue(Math.abs(rotated.x(rotated.size() - 1) - x) > 1.0e-4f
                        || Math.abs(rotated.y(rotated.size() - 1) - y) > 1.0e-4f,
                "The editor preview must rotate with the presentation clock");
    }

    @Test
    void liveParametersAreAcceptedWithoutRebuild() throws Exception {
        var sim = simulator();
        at(sim, 0f);
        assertEquals(8, sim.arcBuffer().count());
        sim.setLiveParam("opacity", Value.of(0.5f));
        sim.setLiveParam("width", Value.of(0.012f));
        sim.setLiveParam("flicker_rate", Value.of(20f));
        sim.setLiveParam("max_paths", Value.of(32f));
        at(sim, 0.05f);
        assertEquals(8, sim.arcBuffer().count());
    }
}
