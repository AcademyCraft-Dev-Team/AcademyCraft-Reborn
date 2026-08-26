package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M16-02 CPU 性能门禁（headless）：10k 粒子场景的模拟帧耗时。
 *
 * <p>阈值取宽松预算避免 CI 抖动；重点记录跑分（System.nanoTime 输出到测试日志），
 * 断言只拦极端回归。</p>
 */
class VfxSimulatorPerfTest {
    private static final long SINGLE_STEP_BUDGET_NS = 50_000_000L; // 50ms（宽松）
    private static final long BURST_TOTAL_BUDGET_NS = 500_000_000L; // 500ms

    private VfxNodeRegistry vfxRegistry;

    @BeforeEach
    void setUp() {
        vfxRegistry = new VfxNodeRegistry();
        VfxNodes.registerAll(new SimpleNodeRegistry(), vfxRegistry);
    }

    private static GraphNode node(String id, String type, Map<String, String> props) {
        return new GraphNode(id, type, props, List.of(), 0f, 0f);
    }

    @Test
    void tenThousandParticlesSteadyStateStep() {
        // 10k 粒子稳态：burst 10000 + init_lifetime（100s 保持存活）+ 重力 + 集成 + 年龄
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_burst", Map.of("count", "10000", "shape", "point")),
                node("life", "vfx.init_lifetime", Map.of("lifetime", "100")),
                node("grav", "vfx.update_gravity", Map.of("gravity", "-9.8")),
                node("integ", "vfx.update_velocity", Map.of()),
                node("age", "vfx.update_age", Map.of())
        ), vfxRegistry, 42L);

        sim.step(0.016f);
        assertEquals(10000, sim.buffer().count());

        long worst = 0;
        for (int i = 0; i < 60; i++) {
            long start = System.nanoTime();
            sim.step(0.016f);
            long elapsed = System.nanoTime() - start;
            worst = Math.max(worst, elapsed);
        }
        System.out.println("[perf] 10k particle steady-state worst step: " + worst / 1_000_000.0 + " ms");
        assertTrue(worst < SINGLE_STEP_BUDGET_NS, "10k steady-state step exceeded budget: " + worst + " ns");
    }

    @Test
    void tenThousandParticlesBurstSpawnAndChurn() {
        // burst 10k + 短生命周期（快速 kill/swap-remove 压力），累计跑分
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "4000", "lifetime", "2.5", "shape", "point")),
                node("grav", "vfx.update_gravity", Map.of("gravity", "-9.8")),
                node("integ", "vfx.update_velocity", Map.of()),
                node("age", "vfx.update_age", Map.of())
        ), vfxRegistry, 42L);

        long start = System.nanoTime();
        for (int i = 0; i < 600; i++) {
            sim.step(0.016f);
        }
        long total = System.nanoTime() - start;
        System.out.println("[perf] 10k particle churn 600 steps total: " + total / 1_000_000.0 + " ms");
        assertTrue(total < BURST_TOTAL_BUDGET_NS, "10k churn total exceeded budget: " + total + " ns");
    }
}
