package org.academy.api.client.render.vfxgraph.serialize;

import com.google.gson.JsonParser;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.academy.api.client.render.vfxgraph.validate.VfxGraphValidator;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterConceptAssetsTest {
    private static final String[] GRAPHS = {"generation", "elimination", "shaping", "phase_tuning", "feather",
            "creation_trail", "interference", "light_contact", "repair", "disassemble", "creation_summon",
            "creation_dismiss", "creation_contact", "cut", "wing_strike", "six_wings"};

    @Test void allNineSkillsHaveEditableValidatedGraphsAndPackagedShaders() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var operators = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, operators);
        var codec = new JsonVfxGraphCodec(metadata);
        var validator = new VfxGraphValidator(metadata);
        for (var name : GRAPHS) {
            var graph = graph(name, codec);
            assertTrue(validator.validate(graph).isEmpty(), name + ": " + validator.validate(graph));
            var parameters = new HashSet<String>();
            graph.parameters().forEach(parameter -> parameters.add(parameter.id()));
            for (var parameter : new String[]{"progress", "alpha", "beta", "gamma", "density", "flexibility", "edge_activity", "camera"})
                assertTrue(parameters.contains(parameter), name + " lacks " + parameter);
            for (var context : graph.contexts()) for (var output : context.blocks()) {
                if (!output.type().startsWith("vfx.block.output_")) continue;
                assertEquals("translucent", output.properties().get("blend"), name);
                assertEquals("true", output.properties().get("cull_zero_alpha"), name + " must declare its alpha contract");
                shader(output.properties().get("vertex"), ".vsh");
                shader(output.properties().get("shader"), ".fsh");
            }
        }
    }

    @Test void maintainedAndTerminalGraphsKeepAConstantBoundedParticleBudget() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        var codec = new JsonVfxGraphCodec(metadata);
        for (var name : GRAPHS) {
            var graph = graph(name, codec);
            var simulator = new VfxSystemSimulator(graph, blocks, 42, graph.parameters());
            simulator.setLiveParam("time", Value.of(.12f));
            simulator.setLiveParam("progress", Value.of(.4f));
            simulator.step(0);
            int budget = simulator.buffer().count();
            assertTrue(budget > 0 && budget <= 160, name + " has unbounded budget " + budget);
            for (int frame = 0; frame < 120; frame++) {
                simulator.setLiveParam("time", Value.of(frame / 20f));
                simulator.setLiveParam("progress", Value.of((frame % 13) / 13f));
                simulator.step(0);
                assertEquals(budget, simulator.buffer().count(), name + " accumulates on refresh");
            }
            for (int i = 0; i < budget; i++) {
                assertTrue(Float.isFinite(simulator.buffer().positionX(i)), name);
                assertTrue(Float.isFinite(simulator.buffer().positionY(i)), name);
                assertTrue(Float.isFinite(simulator.buffer().positionZ(i)), name);
            }
        }
    }

    @Test void sixWingsUseThreePairsAndCutUsesTheContinuousMaterialSurface() throws Exception {
        var metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, new VfxBlockRegistry());
        var codec = new JsonVfxGraphCodec(metadata);
        var wings = graph("six_wings", codec);
        var array = wings.contexts().stream().flatMap(context -> context.blocks().stream())
                .filter(block -> block.type().equals("vfx.block.feather_array")).findFirst().orElseThrow();
        assertEquals("3", array.properties().get("pairs"));
        var cut = graph("cut", codec);
        assertTrue(cut.contexts().stream().flatMap(context -> context.blocks().stream())
                .anyMatch(block -> block.type().equals("vfx.block.material_fan")));
        assertTrue(cut.contexts().stream().flatMap(context -> context.blocks().stream())
                .anyMatch(block -> block.type().equals("vfx.block.output_material_surface")));
    }

    private VfxSystem graph(String name, JsonVfxGraphCodec codec) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/darkmatter_" + name + ".json")) {
            assertNotNull(stream, name);
            return codec.decode(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }
    private void shader(String id, String extension) {
        int separator = id.indexOf(':');
        String resource = "/assets/" + id.substring(0, separator) + "/shaders/" + id.substring(separator + 1) + extension;
        assertNotNull(getClass().getResourceAsStream(resource), resource);
    }
}
