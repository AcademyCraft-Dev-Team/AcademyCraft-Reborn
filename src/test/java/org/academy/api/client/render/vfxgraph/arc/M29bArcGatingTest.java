package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** M29b：弧数爆炸修复（帧周期门控 + 火花只从本帧新增弧 + 火花数上限）。 */
class M29bArcGatingTest {
    private VfxBlockRegistry blocks;
    private VfxOperatorRegistry ops;
    private SimpleNodeRegistry metadata;

    @BeforeEach
    void setUp() {
        blocks = new VfxBlockRegistry();
        ops = new VfxOperatorRegistry();
        metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
    }

    private static VfxBlock block(String id, String type, Map<String, String> props) {
        return new VfxBlock(id, type, props, List.of());
    }

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    /** frequency>0 + frame_period=N：只在 frame % N == 0 的帧 spawn 一批，稳态驻留有界（<30）。 */
    @Test
    void framePeriodicGatingCapsSpawnedArcs() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "1",
                                                "probability", "0.5", "frequency", "1",
                                                "frame_period", "3", "fps", "30", "lifetime", "0.4"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        // 跑 300 帧：帧周期 3（@30fps）→ 每 0.1s 一批 ~4 弧，lifetime 0.4s → 稳态 ≈ 4 × 4 = 16
        for (int i = 0; i < 300; i++) {
            sim.step(1f / 60f);
        }
        int arcs = sim.arcBuffer().count();
        assertTrue(arcs > 0, "gated surface arcs should still spawn");
        assertTrue(arcs < 30, "frame-periodic gating should keep steady-state arc count < 30, got " + arcs);
    }

    /** frequency<=0：保留 legacy 每帧 spawn（旧资产/测试兼容）。 */
    @Test
    void frequencyZeroKeepsLegacyEveryFrameSpawn() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);
        assertTrue(sim.arcBuffer().count() > 0, "frequency<=0 keeps legacy per-frame spawn");
    }

    /** arc_spark 只从本帧新增弧（fresh）派生火花；且每弧火花数有上限 → 火花总量有界。 */
    @Test
    void sparkOnlyFromFreshArcsCapsGrowth() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "1",
                                                "probability", "0.5", "frequency", "1",
                                                "frame_period", "3", "fps", "30", "lifetime", "0.4")),
                                block("bK", "vfx.block.arc_spark",
                                        Map.of("probability", "1", "max_sparks", "3",
                                                "splash_speed", "1.3", "lifetime", "0.5"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        for (int i = 0; i < 300; i++) {
            sim.step(1f / 60f);
        }
        // 源弧稳态 ~16（见 framePeriodicGatingCapsSpawnedArcs）；火花只从本帧新增弧派生 + 每弧 ≤3 →
        // 火花稳态应远小于"每条弧每控制点每帧"的指数放大（旧 ~1000+）。给宽松上界。
        int sparks = 0;
        for (int a = 0; a < sim.arcBuffer().count(); a++) {
            if (!sim.arcBuffer().arc(a).hasSurface()) sparks++;
        }
        assertTrue(sparks < 100, "spark arcs should be bounded (fresh-only + per-arc cap), got " + sparks);
    }

    /** 火花弧（无表面）自身不被再派生（fresh 在下帧清除 + 无表面过滤），总量有界无指数增长。 */
    @Test
    void sparksDoNotRecursivelySpawnSparks() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "1",
                                                "probability", "1", "frequency", "1",
                                                "frame_period", "3", "fps", "30", "lifetime", "0.4")),
                                block("bK", "vfx.block.arc_spark",
                                        Map.of("probability", "1", "max_sparks", "3",
                                                "splash_speed", "1.3", "lifetime", "0.5"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        for (int i = 0; i < 300; i++) {
            sim.step(1f / 60f);
        }
        // 稳态后总弧数（源弧 + 火花）应远小于旧 ~1000+ 指数放大：宽松上界
        assertTrue(sim.arcBuffer().count() < 150,
                "total arc count (surface + sparks) should be bounded, got " + sim.arcBuffer().count());
    }
}