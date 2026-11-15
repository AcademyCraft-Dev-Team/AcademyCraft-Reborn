package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.VfxSeriesTestRegistration;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.curve.TubeCurve;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BlackWingsVfxTest {
    private VfxSystemSimulator simulator() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxSeriesTestRegistration.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/black_wings.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var system = new JsonVfxGraphCodec(metadata).decode(json);
            return new VfxSystemSimulator(system, blocks, 42L, system.parameters());
        }
    }

    @Test
    void continuousJetsStayBoundedAndCurvedDuringLongPlayback() throws Exception {
        var sim = simulator();
        sim.step(1f / 60);
        var count = sim.arcBuffer().count();
        assertEquals(110, count);
        var original = core(sim, false).y(60);
        for (var i = 0; i < 900; i++) {
            sim.step(i % 2 == 0 ? 1f / 30 : 1f / 144);
            assertEquals(count, sim.arcBuffer().count(), "must replace previous samples rather than accumulate ghost jets");
        }
        assertEquals(0, sim.buffer().count(), "ink shreds share bounded curve geometry");
        var right = core(sim, false);
        var left = core(sim, true);
        assertEquals(0.18f, right.x(0), 0.001f);
        assertEquals(-0.18f, left.x(0), 0.001f);
        assertTrue(right.x(right.size() - 1) > 5f);
        assertTrue(right.z(right.size() - 1) < -1.5f);
        assertNotEquals(original, right.y(60), 0.02f, "the storm spine must undulate");
        assertNotEquals(left.y(60), right.y(60), 0.02f, "left/right phases must differ");
        for (var i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            for (var p = 0; p < arc.size(); p++) {
                assertTrue(Float.isFinite(arc.x(p)) && Float.isFinite(arc.y(p)) && Float.isFinite(arc.z(p)));
                assertTrue(arc.width(p) >= 0 && arc.width(p) < 1f);
            }
        }
    }

    @Test
    void liveSweepMovesOnlySelectedWingAndCollapseLeavesNoOldGeometry() throws Exception {
        var sim = simulator();
        sim.step(0f);
        var rightX = core(sim, false).x(70);
        var leftZ = core(sim, true).z(70);
        sim.setLiveParam("sweep_left", Value.of(125f));
        sim.step(0f);
        assertEquals(rightX, core(sim, false).x(70), 0.0001f);
        assertNotEquals(leftZ, core(sim, true).z(70), 0.5f);
        sim.setLiveParam("radial_scale", Value.of(0f));
        sim.step(0f);
        assertEquals(0, sim.arcBuffer().count());
        sim.setLiveParam("radial_scale", Value.of(1f));
        sim.setLiveParam("sweep_left", Value.of(0f));
        sim.step(0f);
        assertEquals(110, sim.arcBuffer().count());
        assertEquals(leftZ, core(sim, true).z(70), 0.0001f);
    }

    @Test
    void openingsBreakCoreMeshWhileVioletPulsesTravelOutward() throws Exception {
        var sim = simulator();
        sim.step(0f);
        var core = core(sim, false);
        var disconnectedEdges = 0;
        for (var p = 1; p < core.size(); p++) {
            if (core.segment(p) != core.segment(p - 1)) disconnectedEdges++;
        }
        assertTrue(disconnectedEdges >= 6 && disconnectedEdges <= 12,
                "local holes must remove triangles across the core, not merely darken them");
        assertEquals(0, core.segment(0), "keep the scapula root connected");
        var pulse = curve(sim, 1000, false);
        assertTrue(pulse.b() > 0.8f && pulse.r() > 0.4f && pulse.g() < 0.3f);
        var pulseStart = pulse.x(0);
        sim.setTime(0.1f);
        sim.step(0f);
        assertNotEquals(pulseStart, curve(sim, 1000, false).x(0), 0.005f,
                "highlight must travel along the jet rather than stay on a static ridge");
        // Every third outer filament remains continuous across the smaller hollow core.
        var bridge = curve(sim, 4, false);
        for (var p = 1; p < bridge.size(); p++) assertEquals(bridge.segment(p - 1), bridge.segment(p));
    }

    @Test
    void fineHighlightsStayNarrowAndSmallOpeningsShiftDuringPlayback() throws Exception {
        var sim = simulator();
        var firstOpening = -1;
        var furthestOpening = -1;
        for (var frame = 0; frame < 120; frame++) {
            sim.setTime(frame / 20f);
            sim.step(0f);
            for (var left : new boolean[]{false, true}) {
                for (var h = 0; h < (left ? 14 : 10); h++) {
                    var pulse = curve(sim, 1000 + h, left);
                    for (var p = 0; p < pulse.size(); p++) {
                        assertTrue(pulse.width(p) >= 0f && pulse.width(p) <= 0.01201f,
                                "purple must remain a fine trace even at the broad funnel tip");
                    }
                }
                var spine = core(sim, left);
                var gapPoints = 0;
                var opening = -1;
                for (var p = 0; p < spine.size(); p++) {
                    if (spine.segment(p) == 0) continue;
                    gapPoints++;
                    if (opening < 0) opening = p;
                }
                assertTrue(gapPoints >= 6 && gapPoints <= 10, "retain small local openings at all phases");
                if (!left) {
                    if (firstOpening < 0) firstOpening = opening;
                    furthestOpening = Math.max(furthestOpening, Math.abs(opening - firstOpening));
                }
            }
        }
        assertTrue(furthestOpening >= 6, "openings must visibly drift along the wing");
    }

    @Test
    void replacingRightWingCannotPaintBlackOverLeftHighlights() throws Exception {
        var sim = simulator();
        for (var frame = 0; frame < 90; frame++) {
            sim.step(1f / 60f);
            for (var left : new boolean[]{true, false}) {
                var highlightSeen = false;
                for (var i = 0; i < sim.arcBuffer().count(); i++) {
                    var arc = sim.arcBuffer().arc(i);
                    if ((arc.x(0) < 0f) != left) continue;
                    if (arc.seed() >= 1000) highlightSeen = true;
                    if (arc.seed() >= 1 && arc.seed() <= 19) {
                        assertFalse(highlightSeen, "opaque ink after highlight hides one wing in translucent rendering");
                    }
                }
                assertTrue(highlightSeen);
            }
        }
    }

    private TubeCurve core(VfxSystemSimulator sim, boolean left) {
        return curve(sim, 1, left);
    }

    private TubeCurve curve(VfxSystemSimulator sim, long seed, boolean left) {
        for (var i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (arc.seed() == seed && (arc.x(0) < 0) == left) return arc;
        }
        throw new AssertionError("missing vortex core");
    }
}
