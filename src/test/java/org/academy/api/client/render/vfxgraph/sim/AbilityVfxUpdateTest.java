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

class AbilityVfxUpdateTest {
    @Test void matterSheetsStayBoundedFollowSurfacesAndNeverProduceArcs() throws Exception {
        for (var name : new String[]{"darkmatter_interference", "darkmatter_light_contact", "darkmatter_repair", "darkmatter_disassemble"}) {
            var sim = load(name);
            sim.setLiveParam("progress", Value.of(0.4f));
            float[] offset = {2};
            sim.setSurfaceSampler("surface", (i, u, v, p, n) -> { p.set(offset[0], i * 0.02f, 3); n.set(1, 0, 0); return true; });
            for (int i = 0; i < 200; i++) at(sim, i / 20f);
            assertEquals(0, sim.arcBuffer().count());
            int count = sim.buffer().count();
            assertTrue(count > 0 && count <= 64);
            for (int i = 0; i < count; i++) {
                assertEquals(sim.buffer().colorR(i), sim.buffer().colorG(i));
                assertTrue(sim.buffer().alpha(i) >= 0 && sim.buffer().alpha(i) <= 0.75f);
            }
            if (!name.endsWith("interference")) {
                float x = sim.buffer().positionX(0);
                offset[0] += 3;
                at(sim, 10);
                assertEquals(x + 3, sim.buffer().positionX(0), 0.00001);
                sim.setLiveParam("progress", Value.of(1f));
                at(sim, 11);
                for (int i = 0; i < count; i++) assertEquals(0, sim.buffer().alpha(i));
            }
            sim.buffer().kill(0);
            at(sim, 12);
            assertEquals(count, sim.buffer().count());
        }
    }

    @Test void nearCameraMediumAndFirstPersonReticleRemainClear() {
        var camera = new org.joml.Vector3f();
        var forward = new org.joml.Vector3f(0, 0, 1);
        assertEquals(0, org.academy.api.client.render.vfxgraph.nodes.MaterialSheetEmitter.mediaVisibility(
                new org.joml.Vector3f(0, 0, 0.6f), camera, forward, false));
        assertEquals(0, org.academy.api.client.render.vfxgraph.nodes.MaterialSheetEmitter.mediaVisibility(
                new org.joml.Vector3f(0, 0, 4), camera, forward, true));
        assertEquals(1, org.academy.api.client.render.vfxgraph.nodes.MaterialSheetEmitter.mediaVisibility(
                new org.joml.Vector3f(0, 0, 4), camera, forward, false));
        assertEquals(1, org.academy.api.client.render.vfxgraph.nodes.MaterialSheetEmitter.mediaVisibility(
                new org.joml.Vector3f(3, 0, 4), camera, forward, true));
    }

    @Test void featherDoesNotFadeMidFlightAndDissolveSmokeExpandsThenClears() throws Exception {
        var feather = load("darkmatter_feather");
        for (float time : new float[]{0, 0.5f, 1.9f}) {
            at(feather, time);
            assertEquals(2, feather.buffer().count());
            assertEquals(0, feather.arcBuffer().count());
            assertTrue(feather.buffer().alpha(0) > 0.5f, "A live projectile must remain readable");
        }
        var dissolve = load("darkmatter_disassemble");
        dissolve.setLiveParam("progress", Value.of(0.2f));
        at(dissolve, 0.13f);
        float earlySmokeSize = size(dissolve, "cloud");
        dissolve.setLiveParam("progress", Value.of(0.7f));
        at(dissolve, 0.45f);
        assertTrue(size(dissolve, "cloud") > earlySmokeSize, "Smoke expands as surface fragments dissolve");
        dissolve.setLiveParam("progress", Value.of(1f));
        at(dissolve, 0.65f);
        for (int i = 0; i < dissolve.buffer().count(); i++) assertEquals(0, dissolve.buffer().alpha(i));
    }

    private static float size(VfxSystemSimulator sim, String layer) {
        for (int i = 0; i < sim.buffer().count(); i++) {
            if (sim.buffer().layer(i) == ParticleBuffer.layerByte(layer)) return sim.buffer().size(i);
        }
        throw new AssertionError("Missing " + layer);
    }

    private VfxSystemSimulator load(String name) throws Exception {
        var metadata = new SimpleNodeRegistry(); var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + name + ".json")) {
            assertNotNull(stream);
            var graph = new JsonVfxGraphCodec(metadata).decode(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            return new VfxSystemSimulator(graph, blocks, 42, graph.parameters());
        }
    }
    private static void at(VfxSystemSimulator sim, float time) { sim.setLiveParam("time", Value.of(time)); sim.step(0); }
}
