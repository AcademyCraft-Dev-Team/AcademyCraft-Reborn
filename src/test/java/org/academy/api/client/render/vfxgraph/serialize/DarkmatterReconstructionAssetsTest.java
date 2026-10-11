package org.academy.api.client.render.vfxgraph.serialize;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.academy.api.client.render.vfxgraph.validate.VfxGraphValidator;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterReconstructionAssetsTest {
    @Test
    void reconstructionGraphsUseOneDedicatedDepthTestedSurfacePass() throws Exception {
        var metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, new VfxBlockRegistry());
        var codec = new JsonVfxGraphCodec(metadata);
        for (var name : new String[]{"disassemble", "repair"}) {
            var graph = graph(name, codec);
            assertTrue(new VfxGraphValidator(metadata).validate(graph).isEmpty(), name);
            var blocks = graph.contexts().stream().flatMap(context -> context.blocks().stream()).toList();
            assertEquals(2, blocks.size(), "One emitter and one output avoid overlapping translucent passes");
            var emitter = blocks.stream().filter(block -> block.type().equals("vfx.block.surface_laminae"))
                    .findFirst().orElseThrow();
            assertEquals(name, emitter.properties().get("mode"));
            assertEquals("surface", emitter.properties().get("surface"));
            float opacity = Float.parseFloat(emitter.properties().get("opacity"));
            assertTrue(opacity >= .9f && opacity <= 1f, "The continuous body stays solid white");
            assertFalse(blocks.stream().anyMatch(block -> block.type().equals("vfx.block.material_sheets")));
            assertEquals(1, graph.outputs().size());
            var output = blocks.stream().filter(block -> graph.outputs().contains(block.id())).findFirst().orElseThrow();
            assertEquals("vfx.block.output_material_surface", output.type());
            assertEquals("academy:core/vfxgraph_surface_lamina", output.properties().get("vertex"));
            assertEquals("academy:core/vfxgraph_surface_lamina", output.properties().get("shader"));
            assertEquals("lamina", output.properties().get("layer"));
            assertEquals("translucent", output.properties().get("blend"));
            assertEquals("true", output.properties().get("soft_depth"));
            assertEquals("true", output.properties().get("cull_zero_alpha"));
        }
        for (var extension : new String[]{"vsh", "fsh"}) {
            try (var shader = getClass().getResourceAsStream("/assets/academy/shaders/core/vfxgraph_surface_lamina." + extension)) {
                assertNotNull(shader, extension);
            }
        }
    }

    @Test
    void editorRoundTripRetainsTypedDetailAndIndependentRepairProgress() throws Exception {
        var metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, new VfxBlockRegistry());
        var codec = new JsonVfxGraphCodec(metadata);
        for (var name : new String[]{"disassemble", "repair"}) {
            var graph = graph(name, codec);
            var decoded = codec.decode(codec.encode(graph));
            assertEquals(graph, decoded);
            var detail = decoded.parameters().stream().filter(parameter -> parameter.id().equals("surface_detail"))
                    .findFirst().orElseThrow();
            assertEquals(ValueType.INT, detail.type());
            assertEquals(0, detail.defaultValue().asInt());
            var patches = decoded.parameters().stream().filter(parameter -> parameter.id().equals("surface_patches"))
                    .findFirst().orElseThrow();
            assertEquals(ValueType.FLOAT, patches.type());
            assertEquals(name.equals("repair") ? 6f : 1f, patches.defaultValue().asFloat());
            for (int part = 0; part < 6; part++) {
                String id = "repair_progress_" + part;
                var progress = decoded.parameters().stream().filter(parameter -> parameter.id().equals(id)).findFirst();
                assertEquals(name.equals("repair"), progress.isPresent(), id);
                if (progress.isPresent()) assertEquals(Value.of(-1f), progress.get().defaultValue());
            }
        }
    }

    @Test
    void repeatedFramesAndLodChangesDoNotAccumulateSurfaceSlots() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        var codec = new JsonVfxGraphCodec(metadata);
        for (var name : new String[]{"disassemble", "repair"}) {
            var graph = graph(name, codec);
            var simulator = new VfxSystemSimulator(graph, blocks, 42, graph.parameters());
            simulator.setLiveParam("progress", Value.of(.35f));
            simulator.step(0);
            int budget = simulator.buffer().count();
            assertTrue(budget > 0 && budget <= 60, name + " exceeds the fixed surface capacity");
            for (int frame = 0; frame < 180; frame++) {
                simulator.setLiveParam("time", Value.of(frame / 20f));
                simulator.setLiveParam("progress", Value.of((frame % 13) / 13f));
                simulator.setLiveParam("lod", Value.of(frame % 2 == 0 ? 1f : .25f));
                simulator.setLiveParam("surface_patches", Value.of(frame % 3 == 0 ? 6f : 1f));
                simulator.step(0);
                assertEquals(budget, simulator.buffer().count(), name + " allocates more slots on refresh");
                for (int i = 0; i < budget; i++) {
                    assertTrue(Float.isFinite(simulator.buffer().positionX(i)));
                    assertTrue(Float.isFinite(simulator.buffer().positionY(i)));
                    assertTrue(Float.isFinite(simulator.buffer().positionZ(i)));
                }
            }
        }
    }

    private VfxSystem graph(String name, JsonVfxGraphCodec codec) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/darkmatter_" + name + ".json")) {
            assertNotNull(stream, name);
            return codec.decode(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }
}
