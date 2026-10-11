package org.academy.api.client.render.vfxgraph.serialize;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.PlanarSheetEmitter;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AeromanipBladeAssetTest {
    @Test
    void continuousSurfaceHasAConstantBudgetAndFadesWithoutAccumulatingParticles() throws Exception {
        var simulator = simulator();
        at(simulator, 0.12f);
        var buffer = simulator.buffer();
        assertEquals(PlanarSheetEmitter.SURFACE_COUNT, buffer.count());
        assertTrue(buffer.alpha(0) > 0.2f);
        assertEquals(12, buffer.positionY(0));
        assertEquals(5, buffer.size(0) * buffer.velocityZ(0) * 2, 0.0001);
        for (int frame = 0; frame < 1000; frame++) at(simulator, frame / 120f);
        assertEquals(4, buffer.count());
        assertEquals(0, simulator.arcBuffer().count());
        for (int i = 0; i < buffer.count(); i++) assertEquals(0, buffer.alpha(i));
        at(simulator, 0.12f);
        assertTrue(buffer.alpha(0) > 0.2f, "Analytic animation supports editor time rewind");
    }

    @Test
    void dimensionsAndEdgeFinsMatchInstantAndChargedAttackPlanes() throws Exception {
        for (float width : new float[]{5, 8}) {
            var simulator = simulator();
            simulator.setLiveParam("length", Value.of(38.4f));
            simulator.setLiveParam("width", Value.of(width));
            simulator.setLiveParam("sheet_roll", Value.of(1.2f));
            simulator.setLiveParam("edge_roll", Value.of(-0.4f));
            at(simulator, 0.12f);
            var buffer = simulator.buffer();
            assertEquals(38.4, buffer.positionY(0) * 2, 0.0001);
            assertEquals(width, buffer.size(0) * buffer.velocityZ(0) * 2, 0.0001);
            assertEquals(1.2, buffer.rotation(0), 0.0001);
            for (int i = 1; i < buffer.count(); i++) {
                assertEquals((i - 2) * width * 0.42f, buffer.positionX(i), 0.0001);
                assertEquals(0.12, buffer.size(i) * buffer.velocityX(i) * 2, 0.0001);
                assertEquals(-0.4, buffer.rotation(i), 0.0001);
                assertTrue(buffer.colorB(i) - buffer.colorR(i) < 0.03, "Wind stays neutral pale gray");
            }
        }
    }

    @Test
    void continuousSurfaceUsesOneOutputAndPackagedShaders() throws Exception {
        var graph = graph();
        assertEquals(1, graph.outputs().size());
        var output = graph.contexts().stream().flatMap(context -> context.blocks().stream())
                .filter(block -> block.type().equals("vfx.block.output_quad")).findFirst().orElseThrow();
        assertShaderExists(output.properties().get("vertex"), ".vsh");
        assertShaderExists(output.properties().get("shader"), ".fsh");
        assertEquals("translucent", output.properties().get("blend"));
    }

    private org.academy.api.client.render.vfxgraph.model.VfxSystem graph() throws Exception {
        var metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, new VfxBlockRegistry());
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/aeromanip_mist_blade.json")) {
            assertNotNull(stream);
            return new JsonVfxGraphCodec(metadata).decode(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }

    private VfxSystemSimulator simulator() throws Exception {
        var graph = graph();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(new SimpleNodeRegistry(), blocks);
        return new VfxSystemSimulator(graph, blocks, 42, graph.parameters());
    }

    private void assertShaderExists(String id, String extension) {
        var separator = id.indexOf(':');
        var resource = "/assets/" + id.substring(0, separator) + "/shaders/" + id.substring(separator + 1) + extension;
        assertNotNull(getClass().getResourceAsStream(resource), resource);
    }

    private static void at(VfxSystemSimulator simulator, float time) {
        simulator.setLiveParam("time", Value.of(time));
        simulator.step(0);
    }
}
