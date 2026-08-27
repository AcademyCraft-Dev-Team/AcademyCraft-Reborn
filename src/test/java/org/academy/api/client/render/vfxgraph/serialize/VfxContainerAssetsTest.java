package org.academy.api.client.render.vfxgraph.serialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.validate.VfxGraphValidator;
import org.junit.jupiter.api.Test;

/**
 * 打包的主 VFX 资产均为容器 schema（kind:"vfx"），解码 + 校验通过。
 */
class VfxContainerAssetsTest {
    @Test
    void packagedAssetsAreContainerSchemaAndValidate() {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
        var codec = new JsonVfxGraphCodec(metadata);
        var validator = new VfxGraphValidator(metadata);

        for (String name : new String[]{"demo_burst", "demo_fountain", "demo_ribbon", "minimal_burst", "demo_fire",
                "plasma_cannon_charge", "plasma_cannon_focus", "plasma_cannon_projectile", "plasma_cannon_impact", "entity_smoke",
                "distortion_ripple", "platinum_execution", "aeromanip_mist_burst", "aeromanip_mist_field",
                "aeromanip_mist_ring", "aeromanip_mist_stream", "aeromanip_mist_blade", "aeromanip_mist_vortex"}) {
            var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + name + ".json");
            assertTrue(stream != null, "asset should exist: " + name);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("vfx", json.get("kind").getAsString(), "asset must be container schema: " + name);

            var system = codec.decode(json);
            var issues = validator.validate(system);
            assertTrue(issues.isEmpty(), "asset should validate: " + name + " -> " + issues);
            assertTrue(system.contexts().size() >= 2, "asset should have contexts: " + name);
        }
    }
}
