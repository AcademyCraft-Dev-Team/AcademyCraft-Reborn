package org.academy.api.client.render.vfxgraph.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxBlockFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M28b 块级批次 flow：同一 INITIALIZE context 内多个 init 块，经 blockFlow 精确配对到各自 spawn 块。
 * 核心场景：demo_fire 的 4 个 spawn（core/flame/ember/smoke）同放一个 SPAWN context + 4 个 init_velocity
 * 同放一个 INITIALIZE context，用块级 flow 指定每组配对——各层速度独立，且不需要拆多个 context。
 */
class VfxBlockFlowTest {
    private VfxBlockRegistry blocks;
    private VfxOperatorRegistry ops;

    @BeforeEach
    void setUp() {
        blocks = new VfxBlockRegistry();
        ops = new VfxOperatorRegistry();
        VfxBlocks.registerAll(new SimpleNodeRegistry(), blocks);
        VfxOperators.registerAll(new SimpleNodeRegistry(), ops);
    }

    private static VfxBlock block(String id, String type, Map<String, String> props) {
        return new VfxBlock(id, type, props, List.of());
    }

    @Test
    void blockFlowPairsSpawnToInitIndependently() {
        var system = new VfxSystem("layers",
                List.of(
                        // 单个 SPAWN context：两个 spawn（高速 A / 低速 B）
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(
                                        block("sA", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100")),
                                        block("sB", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))
                                ), 0f, 0f),
                        // 单个 INITIALIZE context：两个 init_velocity（A→vx=3 / B→vx=1）
                        new VfxContext("init", VfxContextType.INITIALIZE, "",
                                List.of(
                                        block("iA", "vfx.block.init_velocity", Map.of("vx", "3", "vy", "0", "vz", "0")),
                                        block("iB", "vfx.block.init_velocity", Map.of("vx", "1", "vy", "0", "vz", "0"))
                                ), 300f, 0f),
                        new VfxContext("update", VfxContextType.UPDATE, "",
                                List.of(block("u", "vfx.block.update_velocity", Map.of())), 600f, 0f)
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                // 块级 flow：sA→iA, sB→iB
                List.of(new VfxBlockFlowEdge("sA", "iA"), new VfxBlockFlowEdge("sB", "iB")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        // 每 frame 两个 spawn 各产 1 粒子 → 2 粒子：A 的 vx=3（x=0.3），B 的 vx=1（x=0.1）
        assertEquals(2, buffer.count());
        float x0 = buffer.positionX(0);
        float x1 = buffer.positionX(1);
        assertTrue(Math.max(x0, x1) - Math.min(x0, x1) > 0.1f, "A/B must have distinct velocities: " + x0 + "," + x1);
        // 精确值：0.3 与 0.1（顺序不定）
        assertTrue((Math.abs(x0 - 0.3f) < 1e-4f && Math.abs(x1 - 0.1f) < 1e-4f)
                || (Math.abs(x0 - 0.1f) < 1e-4f && Math.abs(x1 - 0.3f) < 1e-4f),
                "expected {0.3, 0.1} but got {" + x0 + "," + x1 + "}");
    }

    /** 无块级 flow 时回退 context 级：init 处理整个上游 SPAWN context 的批次。 */
    @Test
    void fallsBackToContextFlowWithoutBlockFlow() {
        var system = new VfxSystem("fallback",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(block("s", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))), 0f, 0f),
                        new VfxContext("init", VfxContextType.INITIALIZE, "",
                                List.of(block("i", "vfx.block.init_velocity", Map.of("vx", "5", "vy", "0", "vz", "0"))), 0f, 0f),
                        new VfxContext("update", VfxContextType.UPDATE, "",
                                List.of(block("u", "vfx.block.update_velocity", Map.of())), 0f, 0f)
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        assertEquals(1, sim.buffer().count());
        assertEquals(0.5f, sim.buffer().positionX(0), 1e-5f);
    }

    /** 块级 flow 只影响自己的 init：未配对的 init 块收到空批次（不误伤其它 spawn 粒子）。 */
    @Test
    void unpairedInitReceivesNothing() {
        var system = new VfxSystem("unpaired",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(block("sA", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100")),
                                        block("sB", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))), 0f, 0f),
                        new VfxContext("init", VfxContextType.INITIALIZE, "",
                                List.of(block("iA", "vfx.block.init_velocity", Map.of("vx", "3", "vy", "0", "vz", "0")),
                                        block("iU", "vfx.block.init_velocity", Map.of("vx", "99", "vy", "0", "vz", "0"))), 0f, 0f),
                        new VfxContext("update", VfxContextType.UPDATE, "",
                                List.of(block("u", "vfx.block.update_velocity", Map.of())), 0f, 0f)
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(new VfxBlockFlowEdge("sA", "iA")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(2, buffer.count());
        // iA 只处理 sA 的批次（vx=3 → x=0.3）；iU 无块级上游 → 空批次不处理；sB 的粒子保持 spawn 默认 vx=0（x=0）
        float x0 = buffer.positionX(0);
        float x1 = buffer.positionX(1);
        // 其中一个 x≈0.3（sA 经 iA），另一个 x≈0（sB 未配 init）
        assertTrue((Math.abs(x0 - 0.3f) < 1e-4f && Math.abs(x1) < 1e-4f)
                || (Math.abs(x0) < 1e-4f && Math.abs(x1 - 0.3f) < 1e-4f),
                "expected {0.3, 0.0} but got {" + x0 + "," + x1 + "}");
    }
}
