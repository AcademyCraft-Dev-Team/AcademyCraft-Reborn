package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RailgunShotVfxTest {
    private VfxSystemSimulator simulator() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/railgun_shot.json")) {
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

    private static float radius(VfxSystemSimulator sim) {
        return sim.arcBuffer().arc(0).width(32);
    }

    @Test
    void beamExpandsThenSettlesAtMediumRadiusBeforeFading() throws Exception {
        var sim = simulator();
        at(sim, 0.001f);
        float small = radius(sim);
        at(sim, 0.077f);
        float peak = radius(sim);
        at(sim, 0.30f);
        float medium = radius(sim);
        assertTrue(peak > small * 4, "Ignition must be visibly thinner than the expansion peak");
        assertTrue(medium > small * 2 && medium < peak * 0.65f);
        at(sim, 0.65f);
        assertEquals(medium, radius(sim), medium * 0.08f, "Keep the medium beam through the hold");
        at(sim, 1.15f);
        assertTrue(sim.arcBuffer().count() >= 4, "The added 10-tick hold must remain visible beyond the old lifetime");
        assertTrue(sim.arcBuffer().arc(1).a() > 0.5f);
        at(sim, 1.6f);
        assertEquals(0, sim.arcBuffer().count(), "Every layer must have cleared");
    }

    @Test
    void changingAmmoAndReflectedSegmentLengthUpdatesAllGeometryWithoutOldPaths() throws Exception {
        var sim = simulator();
        at(sim, 0.18f);
        float initialRadius = radius(sim);
        sim.setLiveParam("width_scale", Value.of(2.5f));
        sim.setLiveParam("length", Value.of(3.25f));
        at(sim, 0.18f);
        assertEquals(initialRadius * 2.5f, radius(sim), 0.0001f);
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            for (int p = 0; p < arc.size(); p++) {
                assertTrue(arc.z(p) >= 0 && arc.z(p) <= 3.2501f, "No visual may extend past its resolved segment");
                assertTrue(Float.isFinite(arc.x(p)) && Float.isFinite(arc.y(p)) && Float.isFinite(arc.z(p)));
            }
        }
        sim.setLiveParam("length", Value.of(0f));
        at(sim, 0.18f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void fixedTimeIsStableAcrossFrameRatesAndScrubbingAndDoesNotAccumulate() throws Exception {
        var sim = simulator();
        at(sim, 0.17f);
        int count = sim.arcBuffer().count();
        float x = sim.arcBuffer().arc(4).x(9);
        assertTrue(count > 20 && count < 100);
        for (int i = 0; i < 200; i++) {
            sim.step(i % 2 == 0 ? 1f / 30 : 1f / 144);
            assertEquals(count, sim.arcBuffer().count());
            assertEquals(x, sim.arcBuffer().arc(4).x(9), 0.00001f);
        }
        at(sim, 1.7f);
        assertEquals(0, sim.arcBuffer().count());
        at(sim, 0.17f);
        assertEquals(count, sim.arcBuffer().count());
        assertEquals(x, sim.arcBuffer().arc(4).x(9), 0.00001f);
        sim.setLiveParam("seed", Value.of(143f));
        at(sim, 0.17f);
        assertNotEquals(x, sim.arcBuffer().arc(4).x(9));
    }

    @Test
    void muzzleViewKeepsTheHandTraceNarrowAndRestoresFullWidthAtDistance() throws Exception {
        var sim = simulator();
        sim.setLiveParam("length", Value.of(512f));
        sim.setLiveParam("width_scale", Value.of(6f));
        at(sim, 0.077f);
        float worldMuzzle = sim.arcBuffer().arc(0).width(4);
        float worldFar = radius(sim);
        sim.setLiveParam("view_near_origin", Value.of(1f));
        at(sim, 0.077f);
        assertTrue(sim.arcBuffer().arc(0).width(4) < worldMuzzle * 0.2f,
                "The nearby heavy-ammo corona must remain at least five times narrower than its world profile");
        assertEquals(worldFar, radius(sim), worldFar * 0.04f);
        assertEquals(512, sim.arcBuffer().arc(0).z(64), 0.0001f);
    }

    @Test
    void firstPersonProjectedWidthNeverBalloonsBetweenHandAndEndpoint() throws Exception {
        var sim = simulator();
        sim.setLiveParam("view_near_origin", Value.of(1f));
        for (float width : new float[]{1, 1.5f, 2, 2.5f, 6, 12}) {
            sim.setLiveParam("width_scale", Value.of(width));
            for (float length : new float[]{50, 74, 512}) {
                sim.setLiveParam("length", Value.of(length));
                for (float time : new float[]{0.077f, 0.3f, 1.15f}) {
                    at(sim, time);
                    for (int layer = 0; layer < 4; layer++) {
                        var beam = sim.arcBuffer().arc(layer);
                        float previous = Float.POSITIVE_INFINITY;
                        for (int p = 0; p < beam.size(); p++) {
                            // Actual skill emits half a block in front of the camera.
                            float projected = beam.width(p) / (beam.z(p) + 0.5f);
                            assertTrue(projected <= previous + 0.00001f,
                                    "First-person corona must not widen again in the middle of the view");
                            previous = projected;
                        }
                    }
                }
            }
        }
    }

    @Test
    void cylindricalTerminalKeepsFullRadiusAndHasAFlatCapForEveryBeamShell() throws Exception {
        var sim = simulator();
        at(sim, 0.3f);
        for (int i = 0; i < 4; i++) {
            var beam = sim.arcBuffer().arc(i);
            assertTrue(beam.endCap());
            assertEquals(beam.width(32), beam.width(64), beam.width(32) * 0.08f,
                    "The terminal must not converge into a needle");
            var mesh = CurveToMeshBuilder.build(
                    beam, 24, beam.r(), beam.g(), beam.b(), beam.a(), 1);
            var vertices = mesh.vertexBuffer();
            for (int p = beam.size() * 24; p < mesh.vertexCount(); p++) {
                assertEquals(50, vertices.getFloat(p * 48 + 8), 0.0001f);
                assertEquals(1, vertices.getFloat(p * 48 + 20), 0.0001f);
            }
        }
    }

    @Test
    void allAmmoDimensionsAndExtendedRangesStayIndependentAndBounded() throws Exception {
        var sim = simulator();
        float[] lengths = {50, 58, 66, 74, 512};
        float[] widths = {1, 1.5f, 2, 2.5f, 12};
        for (float length : lengths) {
            for (float width : widths) {
                sim.setLiveParam("length", Value.of(length));
                sim.setLiveParam("width_scale", Value.of(width));
                at(sim, 0.077f);
                var beam = sim.arcBuffer().arc(0);
                assertEquals(length, beam.z(beam.size() - 1), 0.001f);
                assertEquals(0.832f * 1.95f * width, radius(sim), width * 0.06f);
                assertTrue(sim.arcBuffer().count() < 120, "Geometry budget must not grow with beam length");
                for (int i = 0; i < sim.arcBuffer().count(); i++) {
                    var arc = sim.arcBuffer().arc(i);
                    assertTrue(arc.size() <= 129);
                    for (int p = 0; p < arc.size(); p++) {
                        assertTrue(arc.z(p) >= 0 && arc.z(p) <= length + 0.001f);
                    }
                }
            }
        }
    }

    @Test
    void longRangeKeepsNearbyDischargeAndExtendsCoverageToTheEndpoint() throws Exception {
        var sim = simulator();
        at(sim, 0.17f);
        var launch = sim.arcBuffer().arc(4);
        float start = launch.z(0);
        float end = launch.z(launch.size() - 1);
        assertTrue(start < 1 && end < 26);
        sim.setLiveParam("length", Value.of(512f));
        at(sim, 0.17f);
        launch = sim.arcBuffer().arc(4);
        assertEquals(start, launch.z(0), 0.00001f);
        assertEquals(end, launch.z(launch.size() - 1), 0.00001f);
        float farthestDischarge = 0;
        for (int i = 4; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (arc.b() != 1f) continue;
            for (int p = 0; p < arc.size(); p++) farthestDischarge = Math.max(farthestDischarge, arc.z(p));
        }
        assertEquals(512f, farthestDischarge, 0.001f);
    }

    @Test
    void distantViewRetainsBeamAndReturnSegmentDoesNotReplayMuzzleSparks() throws Exception {
        var sim = simulator();
        at(sim, 0.17f);
        int count = sim.arcBuffer().count();
        sim.setLiveParam("muzzle", Value.of(0f));
        at(sim, 0.17f);
        assertTrue(sim.arcBuffer().count() < count - 20);
        sim.setLiveParam("detail", Value.of(0f));
        at(sim, 0.17f);
        assertEquals(4, sim.arcBuffer().count());
        sim.setLiveParam("width_scale", Value.of(0f));
        at(sim, 0.17f);
        assertEquals(0, sim.arcBuffer().count());
    }
}
