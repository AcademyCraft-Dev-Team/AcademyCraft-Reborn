package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.nodes.CloudVortexEmitter;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AbilityVfxUpdateTest {
    @Test void blastLeavesAClearEyeEvenAfterAccountingForCloudBillboardSize() throws Exception {
        var sim = load("vector_blast");
        sim.setLiveParam("length", Value.of(72f));
        sim.setLiveParam("width", Value.of(1.2f));
        for (int i = 0; i < 100; i++) {
            at(sim, 0.18f);
            assertEquals(0, sim.arcBuffer().count());
            assertEquals(1024, sim.buffer().count());
        }
        var cloud = sim.buffer();
        int[] slices = new int[12];
        float rootRadius = 0, headRadius = 0;
        for (int p = 0; p < cloud.count(); p++) {
            float u = cloud.positionY(p) / 72;
            assertTrue(u >= 0 && u < 1);
            int slice = Math.min(11, (int) (u * 12));
            slices[slice]++;
            float radius = (float) Math.hypot(cloud.positionX(p), cloud.positionZ(p));
            float envelope = 1.2f * (0.35f + 8.15f * (float) Math.pow(u, 1.45));
            assertTrue(radius - cloud.size(p) * CloudVortexEmitter.CLOUD_BOUNDS_SCALE >= envelope * 0.38f,
                    "Cloud edges must stay outside the eye, including their billboard footprint");
            if (u < 0.2f) rootRadius = Math.max(rootRadius, radius);
            if (u > 0.8f) headRadius = Math.max(headRadius, radius);
            assertTrue(cloud.alpha(p) >= 0 && cloud.alpha(p) <= 0.5f);
        }
        for (int slice = 0; slice < 12; slice++) {
            assertTrue(slices[slice] >= 80, "Every length segment contains clouds");
        }
        assertTrue(headRadius > rootRadius * 2, "Funnel broadens toward the head");
        at(sim, 0.7f);
        assertEquals(0, cloud.count());
    }

    @Test void firstPersonReducesOnlyOpacityAndSwitchingBackRestoresThirdPerson() throws Exception {
        var sim = load("vector_blast");
        at(sim, 0.18f);
        var cloud = sim.buffer();
        float[] alpha = new float[cloud.count()], x = new float[cloud.count()], size = new float[cloud.count()];
        for (int i = 0; i < cloud.count(); i++) {
            alpha[i] = cloud.alpha(i); x[i] = cloud.positionX(i); size[i] = cloud.size(i);
        }
        sim.setLiveParam("view_first_person", Value.of(1f));
        at(sim, 0.18f);
        for (int i = 0; i < cloud.count(); i++) {
            assertEquals(alpha[i] * 0.22f, cloud.alpha(i), 0.000001);
            assertEquals(x[i], cloud.positionX(i)); assertEquals(size[i], cloud.size(i));
        }
        sim.setLiveParam("view_first_person", Value.of(0f));
        at(sim, 0.18f);
        for (int i = 0; i < cloud.count(); i++) assertEquals(alpha[i], cloud.alpha(i));
    }

    @Test void blastCloudsFlowAndRewindDeterministicallyWithoutTouchingOtherParticles() throws Exception {
        var sim = load("vector_blast");
        var buffer = sim.buffer();
        int foreign = buffer.spawn();
        buffer.setLifetime(foreign, 100);
        buffer.setPosition(foreign, 999, 0, 0);
        at(sim, 0.18f);
        float x = buffer.positionX(100), y = buffer.positionY(100), z = buffer.positionZ(100);
        float size = buffer.size(100);
        at(sim, 0.3f);
        assertNotEquals(y, buffer.positionY(100));
        assertTrue(Math.hypot(x - buffer.positionX(100), z - buffer.positionZ(100)) > 0.1);
        at(sim, 0.18f);
        assertEquals(x, buffer.positionX(100)); assertEquals(y, buffer.positionY(100));
        sim.setLiveParam("width", Value.of(2f));
        at(sim, 0.18f);
        assertEquals(x * 2, buffer.positionX(100)); assertEquals(y, buffer.positionY(100));
        assertEquals(size * 2, buffer.size(100));
        // Simulate another block's swap-removal, then check that the cloud budget is restored.
        buffer.kill(100);
        at(sim, 0.18f);
        assertEquals(1025, buffer.count());
        at(sim, 0.7f);
        assertEquals(1, buffer.count());
        assertEquals(999, buffer.positionX(0));
        at(sim, 0.18f);
        assertEquals(1025, buffer.count());
        assertEquals(x * 2, buffer.positionX(100));
    }

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

    @Test void projectileAndFocusHaveIdenticalCoreAndHaloAtEveryReleaseSize() throws Exception {
        for (float scale : new float[]{0.4f, 0.62f, 0.8f, 1f}) {
            var focus = load("plasma_cannon_focus");
            var projectile = load("plasma_cannon_projectile");
            focus.setLiveParam("formation_progress", Value.of(scale));
            projectile.setLiveParam("launch_scale", Value.of(scale));
            focus.step(0); projectile.step(0);
            for (String layer : new String[]{"plasma_core", "plasma_halo"}) {
                assertEquals(size(focus, layer), size(projectile, layer), 0.0001);
            }
        }
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
