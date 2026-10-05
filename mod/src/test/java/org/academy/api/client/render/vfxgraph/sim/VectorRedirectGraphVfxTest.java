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

class VectorRedirectGraphVfxTest {
    private VfxSystemSimulator simulator(String asset) throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + asset + ".json")) {
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
    void arcBoltIsSinglePathSpanningLengthWithLiveWidth() throws Exception {
        var sim = simulator("vector_redirect_arc");
        sim.setLiveParam("length", Value.of(10f));
        sim.setLiveParam("width", Value.of(0.012f));
        sim.setLiveParam("spread", Value.of(0.27f));
        at(sim, 0.1f);
        assertEquals(2, sim.arcBuffer().count());
        var arc = sim.arcBuffer().arc(0);
        assertEquals(10f, arc.y(arc.size() - 1), 0.001f);
        assertEquals(0.012f * 2.5f, arc.width(arc.size() / 2), 0.012f * 2.5f * 0.05f);
        assertEquals(0.012f * 0.7f, sim.arcBuffer().arc(1).width(sim.arcBuffer().arc(1).size() / 2),
                0.012f * 0.7f * 0.05f);
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var path = sim.arcBuffer().arc(i);
            for (int p = 0; p < path.size(); p++) {
                assertTrue(Float.isFinite(path.x(p)) && Float.isFinite(path.y(p)) && Float.isFinite(path.z(p)));
            }
        }
        float x = arc.x(arc.size() / 2);
        sim.setLiveParam("seed", Value.of(143f));
        at(sim, 0.1f);
        assertNotEquals(x, sim.arcBuffer().arc(0).x(sim.arcBuffer().arc(0).size() / 2));
    }

    @Test
    void energyBeamShrinksOverLifetimeAndClears() throws Exception {
        var sim = simulator("vector_redirect_beam");
        sim.setLiveParam("length", Value.of(24f));
        sim.setLiveParam("width_scale", Value.of(2f));
        at(sim, 0.001f);
        assertEquals(1, sim.arcBuffer().count());
        assertEquals(24f, sim.arcBuffer().arc(0).z(sim.arcBuffer().arc(0).size() - 1), 0.001f);
        assertEquals(0.09375f * 2f, sim.arcBuffer().arc(0).width(8), 0.09375f * 2f * 0.08f);
        at(sim, 0.175f);
        assertEquals(0.09375f * 2f * 0.5f, sim.arcBuffer().arc(0).width(8), 0.09375f * 2f * 0.5f * 0.08f);
        at(sim, 0.35f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void muzzleBallKeepsFixedLengthAndShrinksOverLifetime() throws Exception {
        var sim = simulator("vector_redirect_muzzle");
        sim.setLiveParam("width_scale", Value.of(1f));
        at(sim, 0.001f);
        assertEquals(1, sim.arcBuffer().count());
        var arc = sim.arcBuffer().arc(0);
        assertEquals(0.37f, arc.z(arc.size() - 1), 0.001f);
        assertEquals(0.185f, arc.width(arc.size() - 1), 0.185f * 0.08f);
        at(sim, 0.175f);
        assertEquals(0.185f * 0.5f, sim.arcBuffer().arc(0).width(sim.arcBuffer().arc(0).size() - 1),
                0.185f * 0.5f * 0.08f);
        at(sim, 0.35f);
        assertEquals(0, sim.arcBuffer().count());
    }
}
