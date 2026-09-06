package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SkyDischargeVfxTest {
    private VfxSystemSimulator simulator(String name) throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + name + ".json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var system = new JsonVfxGraphCodec(metadata).decode(json);
            return new VfxSystemSimulator(system, blocks, 42, system.parameters());
        }
    }

    private org.academy.api.client.render.vfxgraph.arc.ArcCurve core(VfxSystemSimulator sim) {
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (arc.r() == 0.91f) return arc;
        }
        throw new AssertionError("No white discharge core");
    }

    private java.util.List<org.academy.api.client.render.vfxgraph.arc.ArcCurve> channelPaths(VfxSystemSimulator sim) {
        var paths = new java.util.ArrayList<org.academy.api.client.render.vfxgraph.arc.ArcCurve>();
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (arc.r() == 0.10f || arc.r() == 0.91f || arc.r() == 0.28f) paths.add(arc);
        }
        paths.sort(java.util.Comparator.comparingDouble(
                org.academy.api.client.render.vfxgraph.arc.ArcCurve::r).thenComparingLong(
                org.academy.api.client.render.vfxgraph.arc.ArcCurve::seed));
        return paths;
    }

    @Test
    void spineHasDepthPinnedEndsAndSeededMotion() {
        var a = new Vector3f();
        var b = new Vector3f();
        SkyDischargeGeometry.sample(0, 0.5f, 42, 40, 4.4f, a);
        assertEquals(new Vector3f(), a);
        SkyDischargeGeometry.sample(1, 0.5f, 42, 40, 4.4f, a);
        assertEquals(40, a.y, 0.00001);
        assertEquals(0, a.x, 0.00001);
        assertEquals(0, a.z, 0.00001);
        SkyDischargeGeometry.sample(0.43f, 0.5f, 42, 40, 4.4f, a);
        SkyDischargeGeometry.sample(0.43f, 0.5f, 42, 40, 4.4f, b);
        assertEquals(a, b);
        float extentX = 0;
        float extentZ = 0;
        var probe = new Vector3f();
        for (int i = 1; i < 20; i++) {
            SkyDischargeGeometry.sample(i / 20f, 0.5f, 42, 40, 4.4f, probe);
            extentX = Math.max(extentX, Math.abs(probe.x));
            extentZ = Math.max(extentZ, Math.abs(probe.z));
        }
        assertTrue(extentX > 0.5f && extentZ > 0.5f);
        SkyDischargeGeometry.sample(0.43f, 0.8f, 42, 40, 4.4f, b);
        assertTrue(a.distance(b) > 0.35, "Large channel bends must reform, not just micro-jitter");
    }

    @Test
    void bothAssetsSustainReplaceAndExpireWithoutGhostParticles() throws Exception {
        for (var name : new String[]{"sky_strike_thunderclap", "sky_strike_storm"}) {
            var sim = simulator(name);
            sim.setLiveParam("time", Value.of(0.3f));
            for (int frame = 0; frame < 400; frame++) {
                sim.step(frame % 2 == 0 ? 1f / 30 : 1f / 144);
                assertTrue(sim.arcBuffer().count() >= 15 && sim.arcBuffer().count() <= 120);
                assertTrue(sim.buffer().count() >= 20 && sim.buffer().count() <= 80);
                for (int i = 0; i < sim.arcBuffer().count(); i++) {
                    var arc = sim.arcBuffer().arc(i);
                    for (int p = 0; p < arc.size(); p++) {
                        assertTrue(Float.isFinite(arc.x(p)) && Float.isFinite(arc.y(p)) && Float.isFinite(arc.z(p)));
                        assertTrue(arc.width(p) >= 0);
                    }
                }
            }
            float columnEnd = name.endsWith("storm") ? 0.6f : 0.8f;
            sim.setLiveParam("time", Value.of(columnEnd - 0.03f));
            sim.step(0);
            assertFalse(channelPaths(sim).isEmpty());
            sim.setLiveParam("time", Value.of(columnEnd));
            sim.step(0);
            assertTrue(channelPaths(sim).isEmpty(), "The column must end on its configured deadline");
            assertTrue(sim.arcBuffer().count() > 0, "Surface attachment must outlive the column");
            sim.setLiveParam("time", Value.of(4f));
            sim.step(0);
            assertEquals(0, sim.arcBuffer().count());
            assertEquals(0, sim.buffer().count());
        }
    }

    @Test
    void lowDetailKeepsVolumetricCoreAndLiveEditsReplaceGeometryImmediately() throws Exception {
        var sim = simulator("sky_strike_thunderclap");
        sim.setLiveParam("time", Value.of(0.3f));
        sim.setLiveParam("detail", Value.of(0f));
        sim.step(0);
        assertEquals(2, sim.arcBuffer().count());
        var core = core(sim);
        assertEquals(72, core.y(core.size() - 1), 0.001);
        sim.setLiveParam("height", Value.of(64f));
        sim.step(0);
        core = core(sim);
        assertEquals(64, core.y(core.size() - 1), 0.001);
        sim.setLiveParam("opacity", Value.of(0f));
        sim.step(0);
        assertEquals(0, sim.buffer().count());
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void channelAndForksSettleAfterTwoTenthsWhileCurrentRemainsVisible() throws Exception {
        for (var name : new String[]{"sky_strike_thunderclap", "sky_strike_storm"}) {
            var sim = simulator(name);
            sim.setLiveParam("time", Value.of(0.20f));
            sim.step(0);
            var snapshots = new java.util.ArrayList<float[]>();
            // Identify the channel independently of other emitters' buffer ordering.
            var before = channelPaths(sim);
            for (int i = 0; i < before.size(); i++) {
                var arc = before.get(i);
                var points = new float[arc.size() * 3];
                for (int p = 0; p < arc.size(); p++) {
                    points[p * 3] = arc.x(p);
                    points[p * 3 + 1] = arc.y(p);
                    points[p * 3 + 2] = arc.z(p);
                }
                snapshots.add(points);
            }
            for (float time : new float[]{0.31f, 0.45f}) {
                sim.setLiveParam("time", Value.of(time));
                sim.step(0);
                assertTrue(sim.arcBuffer().count() > 4, "Settling must not stop the discharge");
                var after = channelPaths(sim);
                assertEquals(snapshots.size(), after.size());
                for (int i = 0; i < after.size(); i++) {
                    var arc = after.get(i);
                    var points = snapshots.get(i);
                    assertEquals(points.length, arc.size() * 3);
                    for (int p = 0; p < arc.size(); p++) {
                        assertEquals(points[p * 3], arc.x(p));
                        assertEquals(points[p * 3 + 1], arc.y(p));
                        assertEquals(points[p * 3 + 2], arc.z(p));
                    }
                }
            }
        }
    }

    @Test
    void coreRemainsSlightlyWiderAtGroundThroughoutSustainAndDecay() throws Exception {
        for (var name : new String[]{"sky_strike_thunderclap", "sky_strike_storm"}) {
            var sim = simulator(name);
            for (float t : new float[]{0.07f, 0.3f, name.endsWith("storm") ? 0.53f : 0.73f}) {
                sim.setLiveParam("time", Value.of(t));
                sim.step(0);
                var core = core(sim);
                float ratio = core.width(0) / core.width(core.size() - 1);
                assertTrue(ratio > 1.15f && ratio < 1.36f, "Taper should be subtle and ground-heavy");
            }
        }
        assertTrue(SkyDischargeGeometry.impactPulse(0.065f) > SkyDischargeGeometry.impactPulse(0.5f));
    }

    @Test
    void attachedTracesFollowSurfacesAndOutliveTheColumnWithoutRadialOrigins() throws Exception {
        var sim = simulator("sky_strike_thunderclap");
        sim.setSurfaceProjector("ground", (x, y, z, out) -> out.set(x, x * 0.25f - z * 0.1f + 0.08f, z));
        sim.setLiveParam("time", Value.of(2f));
        sim.step(0);
        assertTrue(sim.arcBuffer().count() >= 20);
        int ground = 0;
        int cloud = 0;
        int awayFromOrigin = 0;
        var lobes = new org.joml.Vector4f[44];
        for (int i = 0; i < lobes.length; i++) {
            lobes[i] = org.academy.api.client.render.vfxgraph.shape.StormCloudShape.lobe(
                    i, 44, 2f, 42, 72, 20, new org.joml.Vector4f());
        }
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (arc.size() == 0) continue;
            boolean onGround = arc.y(0) < 20;
            if (onGround) ground++; else cloud++;
            if (Math.hypot(arc.x(0), arc.z(0)) > 1) awayFromOrigin++;
            for (int p = 0; p < arc.size(); p++) {
                if (onGround) {
                    assertEquals(arc.x(p) * 0.25f - arc.z(p) * 0.1f + 0.08f, arc.y(p), 0.0001f);
                } else {
                    assertEquals(org.academy.api.client.render.vfxgraph.shape.StormCloudShape.underside(
                            arc.x(p), arc.z(p), lobes) - 0.07f, arc.y(p), 0.0001f);
                }
            }
        }
        assertTrue(ground > 5 && cloud > 5);
        assertTrue(awayFromOrigin > (ground + cloud) * 0.8f, "Patches must not share a radial origin");
        sim.setLiveParam("time", Value.of(3.2f));
        sim.step(0);
        assertTrue(sim.arcBuffer().count() > 0);
        sim.setLiveParam("time", Value.of(3.4f));
        sim.step(0);
        assertEquals(0, sim.arcBuffer().count());
        assertEquals(0, sim.buffer().count());
    }

    @Test
    void cloudAndGlowLayersCannotRenderThroughEachOthersOutput() {
        assertNotEquals(ParticleBuffer.layerByte("sky_cloud"), ParticleBuffer.layerByte("sky_halo"));
        assertNotEquals(ParticleBuffer.layerByte("sky_cloud"), ParticleBuffer.layerByte("fire"));
    }

    @Test
    void editorLoopCanRestartAtContinuedTimeAndCurrentDoesNotBlinkOutDuringSustain() throws Exception {
        var sim = simulator("sky_strike_thunderclap");
        sim.setTime(200f);
        sim.step(0.1f);
        assertTrue(sim.buffer().count() > 0);
        for (int i = 0; i < 160; i++) {
            assertTrue(SkyDischargeGeometry.current(i / 100f, 1.65f, 0.5f) > 0.6f);
        }
        assertEquals(0, SkyDischargeGeometry.current(2.2f, 1.65f, 0.5f));
    }
}
