package org.academy.api.client.render.vfxgraph.serialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.academy.api.client.render.vfxgraph.validate.VfxGraphValidator;
import org.junit.jupiter.api.Test;

/** M28b：demo_fire 重写为块级 flow 紧凑结构（4 spawn + 4 init 配对），校验 + 模拟 + 各层速度独立。 */
class VfxFireBlockFlowAssetsTest {
    @Test
    void blockFlowFireValidatesAndSimulates() {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);

        var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/demo_fire.json");
        var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        var system = new JsonVfxGraphCodec(metadata).decode(json);

        // 校验通过
        var issues = new VfxGraphValidator(metadata).validate(system);
        assertTrue(issues.isEmpty(), "demo_fire should validate: " + issues);
        // 4 条块级 flow
        assertEquals(4, system.blockFlows().size());

        // 模拟：各层速度独立（spawn_core 1.2 / flame 0.9 / ember 1.6 / smoke 0.45）
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, system.parameters());
        for (int i = 0; i < 120; i++) sim.step(1f / 60f);
        var buf = sim.buffer();
        assertTrue(buf.count() > 0, "fire should spawn particles");
        float minVy = Float.MAX_VALUE, maxVy = -Float.MAX_VALUE;
        for (int i = 0; i < buf.count(); i++) {
            minVy = Math.min(minVy, buf.velocityY(i));
            maxVy = Math.max(maxVy, buf.velocityY(i));
        }
        assertTrue(maxVy - minVy > 0.5f, "fire layers should have distinct velocities: " + minVy + ".." + maxVy);
    }
}
