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

class IronSandVfxTest {
    @Test void denseDefenseRemainsAtTheFeetOnATwoBlockRingWithoutAccumulation() throws Exception {
        var sim = load("defense");
        for (int frame = 0; frame < 150; frame++) {
            at(sim, frame / 30f);
            assertEquals(2800, sim.buffer().count());
            assertEquals(16, sim.arcBuffer().count());
            for (int i = 0; i < sim.buffer().count(); i++) {
                var b = sim.buffer();
                assertEquals(2, Math.hypot(b.positionX(i), b.positionZ(i)), 0.21);
                assertTrue(b.positionY(i) >= 0.05 && b.positionY(i) <= 0.37);
            }
        }
        sim.setLiveParam("detail", Value.of(0.35f));
        at(sim, 8);
        assertEquals(980, sim.buffer().count());
        sim.setLiveParam("opacity", Value.of(0f));
        at(sim, 9);
        assertEquals(0, sim.buffer().count());
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test void guardRisesOnlyOnTheSourceSideAndReturnsToTheRing() throws Exception {
        var sim = load("guard");
        at(sim, 0);
        assertTrue(maxHeight(sim) < 0.4);
        at(sim, 0.12f);
        assertTrue(maxHeight(sim) > 2);
        for (int i = 0; i < sim.buffer().count(); i++) assertTrue(sim.buffer().positionZ(i) > 1);
        at(sim, 0.60f);
        assertTrue(maxHeight(sim) < 0.5);
        at(sim, 0.66f);
        assertEquals(0, sim.buffer().count());
        assertEquals(0, sim.arcBuffer().count());
        at(sim, 0.12f);
        assertEquals(3200, sim.buffer().count(), "Editor seek/reset must restore the effect");
    }

    @Test void whipAndCloudUseLiveAbilityRangesAndShareTheDischargePalette() throws Exception {
        for (var name : new String[]{"whip", "cloud"}) {
            var sim = load(name);
            sim.setLiveParam("radius", Value.of(20f));
            at(sim, 0.16f);
            assertTrue(sim.buffer().count() >= 2600);
            double farthest = 0;
            for (int i = 0; i < sim.buffer().count(); i++) {
                farthest = Math.max(farthest, Math.hypot(sim.buffer().positionX(i), sim.buffer().positionZ(i)));
            }
            assertTrue(farthest > 19 && farthest < 21);
            assertEquals(0.12f, sim.arcBuffer().arc(0).r());
            assertEquals(0.78f, sim.arcBuffer().arc(1).r());
        }
        var bolt = load("intercept");
        at(bolt, 0.06f);
        var arc = bolt.arcBuffer().arc(0);
        assertEquals(1.8, arc.y(0), 0.00001);
        assertEquals(-1.2, arc.y(arc.size() - 1), 0.00001);
        at(bolt, 0.02f);
        arc = bolt.arcBuffer().arc(0);
        assertTrue(arc.y(arc.size() - 1) > 0, "The growing tip must start above the projectile");
        at(bolt, 0.25f);
        assertEquals(0, bolt.arcBuffer().count());
    }

    @Test void overheadAttackTiltsTheRaisedSandAcrossTheHead() throws Exception {
        var sim = load("guard");
        sim.setLiveParam("source_elevation", Value.of(1f));
        at(sim, 0.12f);
        for (int i = 0; i < sim.buffer().count(); i++) {
            assertTrue(sim.buffer().positionY(i) > 2);
            assertTrue(Math.abs(sim.buffer().positionZ(i)) < 1.4);
        }
        at(sim, 0);
        assertTrue(maxHeight(sim) < 0.4, "The shield must still start at the ground ring");
    }

    private static float maxHeight(VfxSystemSimulator sim) {
        float y = 0;
        for (int i = 0; i < sim.buffer().count(); i++) y = Math.max(y, sim.buffer().positionY(i));
        return y;
    }

    private VfxSystemSimulator load(String name) throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/iron_sand_" + name + ".json")) {
            assertNotNull(stream);
            var system = new JsonVfxGraphCodec(metadata).decode(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            return new VfxSystemSimulator(system, blocks, 42, system.parameters());
        }
    }

    private static void at(VfxSystemSimulator sim, float time) {
        sim.setLiveParam("time", Value.of(time));
        sim.step(0);
    }
}
