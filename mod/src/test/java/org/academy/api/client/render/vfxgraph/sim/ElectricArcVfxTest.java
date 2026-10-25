package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.ArcCurveBuilder;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.render.RenderSpec;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ElectricArcVfxTest {
    @Test
    void novaRingSharesArcPaletteAndHasBoundedGeometryWithoutAccumulation() throws Exception {
        var sim = simulator("lightning_nova");
        for (int frame = 0; frame < 1000; frame++) {
            at(sim, (frame % 50) / 100f);
            assertEquals(8, sim.arcBuffer().count());
            var rim = sim.arcBuffer().arc(0);
            assertTrue(rim.size() <= 129);
            assertEquals(rim.x(0), rim.x(rim.size() - 1));
            assertEquals(rim.y(0), rim.y(rim.size() - 1));
            assertEquals(rim.z(0), rim.z(rim.size() - 1));
            assertEquals(0.12f, rim.r());
            assertEquals(0.78f, sim.arcBuffer().arc(1).r());
            float expected = 8 + (frame % 50) / 100f * 1.6f;
            for (int i = 0; i < sim.arcBuffer().count(); i++) {
                var arc = sim.arcBuffer().arc(i);
                assertTrue(arc.size() <= 129);
                for (int p = 0; p < arc.size(); p++) {
                    assertEquals(expected, Math.hypot(arc.x(p), arc.z(p)), 0.18);
                    assertTrue(Float.isFinite(arc.width(p)));
                }
            }
        }
        at(sim, 0.5f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void novaPulsesJoinContinuouslyAndEchoContracts() throws Exception {
        var first = simulator("lightning_nova");
        var second = simulator("lightning_nova");
        first.setLiveParam("duration", Value.of(1f));
        at(first, 0.5f);
        second.setLiveParam("radius", Value.of(8.8f));
        second.setLiveParam("phase", Value.of(0.5f));
        at(second, 0);
        var a = first.arcBuffer().arc(0);
        var b = second.arcBuffer().arc(0);
        assertEquals(a.size(), b.size());
        for (int p = 0; p < a.size(); p++) {
            assertEquals(a.x(p), b.x(p), 0.0001);
            assertEquals(a.y(p), b.y(p), 0.0001);
            assertEquals(a.z(p), b.z(p), 0.0001);
        }
        second.setLiveParam("radial_speed", Value.of(-1.6f));
        at(second, 0.4f);
        var echo = second.arcBuffer().arc(0);
        assertEquals(8.16, Math.hypot(echo.x(0), echo.z(0)), 0.18);
        second.setLiveParam("radius", Value.of(0.1f));
        at(second, 0.4f);
        assertEquals(0, second.arcBuffer().count());
    }

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
        sim.setArcSource("paths", (out, _) -> {
            for (int i = 0; i < 40; i++) {
                ArcCurveBuilder.blueWhiteLine(out, 2, 3, 4, 2, 25, 4,
                        0.01f, 8, 1f, 1f, i, 0, 0, 0, 0, 0);
            }
        });
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
        sim.setArcSource("paths", (out, _) -> {
        });
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
        for (var asset : new String[]{"vector_redirect_arc"}) {
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
    void electricTreesGrowForwardDeltasFromASingleRoot() throws Exception {
        for (var asset : new String[]{"arc_generate", "thunder_lance"}) {
            var sim = simulator(asset);
            for (float length : new float[]{16, 20, 48}) {
                sim.setLiveParam("length", Value.of(length));
                at(sim, 0.25f);
                assertTrue(sim.arcBuffer().count() > 0);
                assertTrue(sim.arcBuffer().count() <= 128, "the delta must stay bounded");
                float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                int maxGeneration = 0;
                for (int i = 0; i < sim.arcBuffer().count(); i++) {
                    var arc = sim.arcBuffer().arc(i);
                    assertTrue(arc.size() <= 32);
                    for (int p = 0; p < arc.size(); p++) {
                        float y = arc.y(p);
                        assertTrue(y >= -0.001f && y <= length + 0.05f, "tree must advance forward from the root");
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        maxGeneration = Math.max(maxGeneration, (int) arc.generation(p));
                    }
                }
                assertTrue(minY <= 0.05f, "branches must share the single root point");
                assertTrue(maxY >= length * 0.6f, "the fan must reach well forward");
                assertTrue(maxGeneration >= 1, "the delta must fork as it advances");
            }
            sim.setLiveParam("length", Value.of(16f));
            at(sim, 1.25f);
            assertEquals(0, sim.arcBuffer().count());
        }
    }

    @Test
    void blockPropertiesWinOverParameterDefaultsUnlessExplicitlyBound() {
        var length = new GraphParameter("length", "length", ValueType.FLOAT, Value.of(10f), Optional.empty());
        var system = new VfxSystem("block-value",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "", List.of(
                                new VfxBlock("tree", "vfx.block.electric_tree",
                                        Map.of("length", "20", "width", "0.008", "duration", "1", "growth_time", "0"),
                                        List.of())), 0, 0),
                        new VfxContext("out", VfxContextType.OUTPUT, "", List.of(
                                new VfxBlock("out", "vfx.block.output_arc", Map.of(), List.of())), 0, 0)
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "out")),
                List.of(),
                List.of(),
                List.of(length),
                List.of("out"));
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        var sim = new VfxSystemSimulator(system, blocks, 42, system.parameters());

        at(sim, 0.1f);
        var fromProperty = maxY(sim);
        assertTrue(fromProperty > 10.1f, "the block property must beat the parameter default");
        assertTrue(fromProperty <= 20.05f);

        sim.setLiveParam("length", Value.of(30f));
        at(sim, 0.1f);
        var fromBinding = maxY(sim);
        assertTrue(fromBinding > 20.1f, "an explicit binding must beat the block property");
        assertTrue(fromBinding <= 30.05f);
    }

    private static float maxY(VfxSystemSimulator sim) {
        var max = -Float.MAX_VALUE;
        for (int i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            for (int p = 0; p < arc.size(); p++) {
                max = Math.max(max, arc.y(p));
            }
        }
        return max;
    }

    @Test
    void sharedShellAndCoreStayCoincidentAndDisappearWithoutAccumulation() throws Exception {
        var sim = simulator("vector_redirect_arc");
        sim.setLiveParam("duration", Value.of(0.75f));
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
