package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElectricArcVfxTest {
    @Test
    void shieldCurrentsStayOnTheBodyAndInterceptionExpires() throws Exception {
        var sim = simulator("electromagnetic_shield");
        at(sim, 0.12f);
        assertEquals(12, sim.arcBuffer().count());
        for (int i = 0; i < 12; i++) {
            var arc = sim.arcBuffer().arc(i);
            assertEquals("electricity", arc.layer());
            for (int p = 0; p < arc.size(); p++) {
                assertEquals(0.85, Math.hypot(arc.x(p), arc.z(p)), 0.06);
                assertTrue(arc.y(p) > 0 && arc.y(p) < 1.9);
            }
        }
        sim.setLiveParam("impact", Value.of(1f));
        at(sim, 0.12f);
        assertEquals(4, sim.arcBuffer().count());
        var ring = sim.arcBuffer().arc(0);
        assertEquals(ring.x(0), ring.x(ring.size() - 1), 0.0001);
        assertEquals(ring.z(0), ring.z(ring.size() - 1), 0.0001);
        at(sim, 0.25f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void boundPathsReplacePreviewPreserveEndpointsAndClearWithoutAccumulating() throws Exception {
        var sim = simulator("magnetic_weapon");
        var path = new org.academy.api.common.arc.ArcPath(new org.academy.api.common.arc.path.LinePath(
                new org.joml.Vector3f(2, 3, 4), new org.joml.Vector3f(2, 25, 4)), List.of(), 8, List.of());
        sim.setArcSource("paths", _ -> java.util.Collections.nCopies(40, path));
        for (int frame = 0; frame < 100; frame++) {
            at(sim, frame * 0.016f);
            assertEquals(32, sim.arcBuffer().count());
            var arc = sim.arcBuffer().arc(0);
            assertTrue(arc.size() <= 128);
            assertEquals(3, arc.y(0));
            assertEquals(25, arc.y(arc.size() - 1));
            assertEquals(0.12f, arc.r());
            assertEquals(0.78f, sim.arcBuffer().arc(1).r());
        }
        sim.setArcSource("paths", _ -> List.of());
        at(sim, 2);
        assertEquals(0, sim.arcBuffer().count(), "An inactive blade must not show the editor demo");
    }

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

    private static void at(VfxSystemSimulator sim, float time) {
        sim.setLiveParam("time", Value.of(time));
        sim.step(0);
    }

    @Test
    void boltsConnectActualEndpointsAtAllRangesAndKeepAnIndependentWidthBudget() throws Exception {
        for (var asset : new String[]{"arc_generate", "thunder_lance"}) {
            var sim = simulator(asset);
            for (float length : new float[]{0.02f, 16, 20, 32, 48, 72, 512}) {
                sim.setLiveParam("length", Value.of(length));
                at(sim, 0.12f);
                var core = sim.arcBuffer().arc(1);
                assertEquals(0, core.y(0), 0.0001f);
                assertEquals(length, core.y(core.size() - 1), 0.0001f);
                assertEquals(0, core.x(core.size() - 1), 0.0001f);
                assertEquals(0, core.z(core.size() - 1), 0.0001f);
                assertTrue(sim.arcBuffer().count() <= 26);
                for (int i = 0; i < sim.arcBuffer().count(); i++) {
                    var arc = sim.arcBuffer().arc(i);
                    assertTrue(arc.size() <= 97);
                    for (int p = 0; p < arc.size(); p++) {
                        assertTrue(arc.y(p) >= 0 && arc.y(p) <= length + 0.001f);
                        assertTrue(Float.isFinite(arc.width(p)));
                    }
                }
            }
            sim.setLiveParam("length", Value.of(0f));
            at(sim, 0.12f);
            assertEquals(0, sim.arcBuffer().count());
        }
    }

    @Test
    void sharedShellAndCoreStayCoincidentAndDisappearWithoutAccumulation() throws Exception {
        var sim = simulator("thunder_lance");
        at(sim, 0.12f);
        int count = sim.arcBuffer().count();
        var outer = sim.arcBuffer().arc(0);
        var core = sim.arcBuffer().arc(1);
        assertEquals(0.12f, outer.r());
        assertEquals(0.78f, core.r());
        for (int p = 0; p < outer.size(); p++) {
            assertEquals(outer.x(p), core.x(p));
            assertEquals(outer.y(p), core.y(p));
            assertEquals(outer.width(p) * 0.7f / 2.5f, core.width(p), 0.00001f);
        }
        for (int frame = 0; frame < 200; frame++) {
            sim.step(frame % 2 == 0 ? 1f / 30 : 1f / 144);
            assertEquals(count, sim.arcBuffer().count());
        }
        at(sim, 0.6f);
        assertEquals(count, sim.arcBuffer().count());
        at(sim, 0.75f);
        assertEquals(0, sim.arcBuffer().count());
        at(sim, 0.12f);
        assertEquals(count, sim.arcBuffer().count());
    }

    @Test
    void chargeRingHasAContinuousRimHotSectionsAndBranchedFilaments() throws Exception {
        var sim = simulator("railgun_charge");
        at(sim, 8.4f);
        assertEquals(24, sim.arcBuffer().count());
        var loop = sim.arcBuffer().arc(0);
        assertEquals(loop.x(0), loop.x(loop.size() - 1), 0.00001f);
        assertEquals(loop.y(0), loop.y(loop.size() - 1), 0.00001f);
        assertTrue(sim.arcBuffer().arc(2).width(10) > loop.width(10) * 2);
        float minZ = 100, maxZ = -100;
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            for (int p = 0; p < arc.size(); p++) {
                minZ = Math.min(minZ, arc.z(p));
                maxZ = Math.max(maxZ, arc.z(p));
                assertTrue(Math.sqrt(arc.x(p) * arc.x(p) + arc.y(p) * arc.y(p) + arc.z(p) * arc.z(p)) < 0.75);
            }
        }
        assertTrue(maxZ - minZ > 0.2f);
        sim.setLiveParam("hint", Value.of(1f));
        at(sim, 8.4f);
        assertEquals(4, sim.arcBuffer().count());
        sim.setLiveParam("opacity", Value.of(0f));
        at(sim, 8.4f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void ringMotionDeformsContinuouslyAndCanBeFrozenForAnEditorPreview() throws Exception {
        var sim = simulator("railgun_charge");
        at(sim, 8.40f);
        var loop = sim.arcBuffer().arc(0);
        float y = loop.y(16), z = loop.z(16);
        at(sim, 8.42f);
        loop = sim.arcBuffer().arc(0);
        assertTrue(Math.abs(loop.y(16) - y) + Math.abs(loop.z(16) - z) > 0.001f);
        sim.setLiveParam("motion_speed", Value.of(0f));
        at(sim, 10);
        float[] frozen = new float[sim.arcBuffer().count()];
        for (int i = 0; i < frozen.length; i++) frozen[i] = sim.arcBuffer().arc(i).x(5);
        at(sim, 30);
        for (int i = 0; i < frozen.length; i++) assertEquals(frozen[i], sim.arcBuffer().arc(i).x(5));
    }

    @Test
    void independentArcOutputsCannotDrawAnotherLayersGeometryOrInheritPoolLayers() {
        var buffer = new ArcBuffer();
        var arc = buffer.add();
        arc.setLayer("electrical_attachment");
        var current = RenderSpec.fromOutputNode(new GraphNode("out", "vfx.block.output_arc",
                Map.of("layer", "sky_current"), List.of(), 0, 0));
        var attachment = RenderSpec.fromOutputNode(new GraphNode("out", "vfx.block.output_arc",
                Map.of("layer", "electrical_attachment"), List.of(), 0, 0));
        assertFalse(current.matchesArcLayer(arc.layer()));
        assertTrue(attachment.matchesArcLayer(arc.layer()));
        buffer.clear();
        assertEquals("", buffer.add().layer());
    }
}
