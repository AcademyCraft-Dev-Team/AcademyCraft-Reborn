package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.*;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M28-02 容器执行器 CPU 性能门禁（headless）：10k 粒子场景的模拟帧耗时（对标 VfxSimulatorPerfTest）。
 *
 * <p>阈值取宽松预算避免 CI 抖动；重点记录跑分（System.nanoTime 输出到测试日志），
 * 断言只拦极端回归。</p>
 */
class VfxSystemSimulatorPerfTest {
    private static final long SINGLE_STEP_BUDGET_NS = 50_000_000L; // 50ms（宽松）
    private static final long BURST_TOTAL_BUDGET_NS = 500_000_000L; // 500ms

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

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    @Test
    void tenThousandParticlesSteadyStateStep() {
        var system = new VfxSystem("perf",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_burst",
                                        Map.of("count", "10000", "shape", "point"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bL", "vfx.block.init_lifetime", Map.of("lifetime", "100"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bG", "vfx.block.update_gravity", Map.of("gravity", "-9.8")),
                                block("bU", "vfx.block.update_velocity", Map.of()),
                                block("bA", "vfx.block.update_age", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.016f);
        assertEquals(10000, sim.buffer().count());

        long worst = 0;
        for (int i = 0; i < 60; i++) {
            long start = System.nanoTime();
            sim.step(0.016f);
            long elapsed = System.nanoTime() - start;
            worst = Math.max(worst, elapsed);
        }
        System.out.println("[perf] container 10k particle steady-state worst step: " + worst / 1_000_000.0 + " ms");
        assertTrue(worst < SINGLE_STEP_BUDGET_NS, "container 10k steady-state step exceeded budget: " + worst + " ns");
    }

    @Test
    void tenThousandParticlesBurstSpawnAndChurn() {
        var system = new VfxSystem("perf-churn",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_rate",
                                        Map.of("rate", "4000", "lifetime", "2.5", "shape", "point"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bG", "vfx.block.update_gravity", Map.of("gravity", "-9.8")),
                                block("bU", "vfx.block.update_velocity", Map.of()),
                                block("bA", "vfx.block.update_age", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        long start = System.nanoTime();
        for (int i = 0; i < 600; i++) {
            sim.step(0.016f);
        }
        long total = System.nanoTime() - start;
        System.out.println("[perf] container 10k particle churn 600 steps total: " + total / 1_000_000.0 + " ms");
        assertTrue(total < BURST_TOTAL_BUDGET_NS, "container 10k churn total exceeded budget: " + total + " ns");
    }
}
