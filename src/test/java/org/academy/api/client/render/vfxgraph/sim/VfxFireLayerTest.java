package org.academy.api.client.render.vfxgraph.sim;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.junit.jupiter.api.Test;

class VfxFireLayerTest {
    @Test
    void fireLayersHaveIndependentVelocities() {
        var metadata = new SimpleNodeRegistry();
        var blocks = new VfxBlockRegistry();
        var ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
        var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/demo_fire.json");
        var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        var system = new JsonVfxGraphCodec(metadata).decode(json);
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, system.parameters());
        // 跑 1 秒：4 层 spawn，每层应有不同 vy（core≈1.2 / flame≈0.9 / ember≈1.6 / smoke≈0.45）
        for (int f = 0; f < 60; f++) sim.step(1f / 60f);
        var buf = sim.buffer();
        System.out.println("count=" + buf.count());
        // 收集每粒子 vy（经过浮力/湍流扰动，但初始 vy 应分层）
        // 验证至少存在两类明显不同的 vy 初始量级（>1.0 与 <1.0 并存）
        float minVy = Float.MAX_VALUE, maxVy = -Float.MAX_VALUE;
        for (int i = 0; i < buf.count(); i++) {
            float v = buf.velocityY(i);
            minVy = Math.min(minVy, v);
            maxVy = Math.max(maxVy, v);
        }
        System.out.println("vy range: " + minVy + " .. " + maxVy);
        // 存在高速层(ember 1.6)与低速层(smoke 0.45)：vy 至少跨 1.0 以上
        org.junit.jupiter.api.Assertions.assertTrue(maxVy - minVy > 1.0f,
                "fire layers should have distinct velocities, range=" + (maxVy - minVy));
    }
}
