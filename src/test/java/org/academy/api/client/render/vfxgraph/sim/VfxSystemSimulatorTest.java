package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.*;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxSystemSimulatorTest {
    private VfxBlockRegistry blocks;

    @BeforeEach
    void setUp() {
        blocks = new VfxBlockRegistry();
        VfxBlocks.registerAll(new SimpleNodeRegistry(), blocks);
    }

    private static VfxBlock block(String id, String type, Map<String, String> props) {
        return new VfxBlock(id, type, props, List.of());
    }

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    /**
     * 单 spawn → 单 init → update 的最小流水线。
     */
    @Test
    void spawnInitUpdatePipelineProducesMovedParticles() {
        var system = new VfxSystem("demo",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("b_spawn", "vfx.block.spawn_rate",
                                        Map.of("rate", "10", "vy", "2", "lifetime", "100", "size", "0.5"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("b_init", "vfx.block.init_velocity", Map.of("vy", "2"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("b_upd", "vfx.block.update_velocity", Map.of()),
                                block("b_age", "vfx.block.update_age", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        for (var i = 0; i < 3; i++) {
            sim.step(0.1f);
        }
        var buffer = sim.buffer();
        assertEquals(3, buffer.count());
        // 每帧 spawn 1 个，速度 2 → 位置 0.2
        assertEquals(0.6f, buffer.positionY(0), 1e-5f);
    }

    /**
     * 核心：两个独立 spawn→init 链路，init 只处理自己上游 spawn 的批次（互不干扰）。
     */
    @Test
    void multiSpawnInitsAreIndependent() {
        var system = new VfxSystem("multi",
                List.of(
                        // 链路 A：红色高速
                        ctx("spawnA", VfxContextType.SPAWN,
                                block("bA", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("initA", VfxContextType.INITIALIZE,
                                block("bIA", "vfx.block.init_velocity", Map.of("vx", "1", "vy", "0", "vz", "0")),
                                block("cIA", "vfx.block.init_color", Map.of("color", "1,0,0,1"))),
                        // 链路 B：绿色低速
                        ctx("spawnB", VfxContextType.SPAWN,
                                block("bB", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("initB", VfxContextType.INITIALIZE,
                                block("bIB", "vfx.block.init_velocity", Map.of("vx", "3", "vy", "0", "vz", "0")),
                                block("cIB", "vfx.block.init_color", Map.of("color", "0,1,0,1"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bUpd", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(),
                List.of(
                        new VfxFlowEdge("spawnA", "initA"), new VfxFlowEdge("initA", "update"),
                        new VfxFlowEdge("spawnB", "initB"), new VfxFlowEdge("initB", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(2, buffer.count());
        // 两个独立 spawn context 的先后不保证，按颜色断言：红色（链路 A，vx=1 → x=0.1）、绿色（链路 B，vx=3 → x=0.3）
        var red = -1;
        var green = -1;
        for (var i = 0; i < buffer.count(); i++) {
            if (buffer.colorR(i) > 0.5f) red = i;
            else green = i;
        }
        assertTrue(red >= 0 && green >= 0, "one red and one green particle expected");
        assertEquals(0.1f, buffer.positionX(red), 1e-5f);
        assertEquals(0.3f, buffer.positionX(green), 1e-5f);
    }

    /**
     * 暂停（dt=0）：spawn 不产粒子，init 空跑，已有粒子不受影响。
     */
    @Test
    void pauseFreezesSimulation() {
        var system = new VfxSystem("pause",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bI", "vfx.block.init_velocity", Map.of("vx", "5"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bU", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(1, buffer.count());
        var x = buffer.positionX(0);
        // dt=0 多帧：无新粒子、位置不变
        for (var i = 0; i < 10; i++) {
            sim.step(0f);
        }
        assertEquals(1, buffer.count());
        assertEquals(x, buffer.positionX(0), 1e-6f);
    }

    /**
     * gravity 阶段在 update 内按序应用。
     */
    @Test
    void appliesGravityInUpdatePhase() {
        var system = new VfxSystem("grav",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bI", "vfx.block.init_velocity", Map.of("vy", "0"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bG", "vfx.block.update_gravity", Map.of("gravity", "-10")),
                                block("bU", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        sim.step(0.1f);
        sim.step(0.1f);
        assertEquals(-2.0f, sim.buffer().velocityY(0), 1e-5f);
    }

    @Test
    void updateLiveAppliesBoundVisualAttributes() {
        var parameters = List.of(
                new org.academy.api.client.render.graph.model.GraphParameter(
                        "live_size", "Live Size", org.academy.api.client.render.graph.type.ValueType.FLOAT,
                        org.academy.api.client.render.graph.type.Value.of(1f), java.util.Optional.empty()),
                new org.academy.api.client.render.graph.model.GraphParameter(
                        "live_alpha", "Live Alpha", org.academy.api.client.render.graph.type.ValueType.FLOAT,
                        org.academy.api.client.render.graph.type.Value.of(1f), java.util.Optional.empty()),
                new org.academy.api.client.render.graph.model.GraphParameter(
                        "live_frame", "Live Frame", org.academy.api.client.render.graph.type.ValueType.FLOAT,
                        org.academy.api.client.render.graph.type.Value.of(0f), java.util.Optional.empty()));
        var system = new VfxSystem("live-attributes",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_burst",
                                        Map.of("count", "1", "lifetime", "100", "layer", "smoke"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bL", "vfx.block.update_live", Map.of(
                                        "layer", "smoke",
                                        "size_param", "live_size",
                                        "alpha_param", "live_alpha",
                                        "rotation_param", "live_frame")))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "update")),
                List.of(),
                parameters,
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, parameters);
        sim.setLiveParam("live_size", org.academy.api.client.render.graph.type.Value.of(2.5f));
        sim.setLiveParam("live_alpha", org.academy.api.client.render.graph.type.Value.of(0.4f));
        sim.setLiveParam("live_frame", org.academy.api.client.render.graph.type.Value.of(3f));
        sim.step(1f / 60f);

        assertEquals(1, sim.buffer().count());
        assertEquals(2.5f, sim.buffer().size(0), 1e-6f);
        assertEquals(0.4f, sim.buffer().alpha(0), 1e-6f);
        assertEquals(3f, sim.buffer().rotation(0), 1e-6f);
    }

    /** 无 flow 的 init context：收到空批次，不处理任何粒子（不应误伤已有粒子）。 */
    @Test
    void initWithoutUpstreamSpawnDoesNothing() {
        var system = new VfxSystem("orphan-init",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bI", "vfx.block.init_velocity", Map.of("vx", "99"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bU", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(),
                // spawn → update 直连，init 无上游 spawn → init 空跑
                List.of(new VfxFlowEdge("spawn", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(1, buffer.count());
        // init 未作用 → 保持 spawn 默认 vx=0
        assertEquals(0f, buffer.velocityX(0), 1e-6f);
    }

    @Test
    void missingBlockTypeThrows() {
        var system = new VfxSystem("bad",
                List.of(ctx("spawn", VfxContextType.SPAWN,
                        block("b", "vfx.block.missing", Map.of()))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        try {
            new VfxSystemSimulator(system, blocks, 1L, List.of());
            throw new AssertionError("expected exception");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("missing"));
        }
    }

    /**
     * 多个 spawn context 喂同一个 init：批次并集被 init 一次性处理。
     */
    @Test
    void multipleSpawnsFeedSingleInit() {
        var system = new VfxSystem("fan-in",
                List.of(
                        ctx("spawnA", VfxContextType.SPAWN,
                                block("bA", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("spawnB", VfxContextType.SPAWN,
                                block("bB", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bI", "vfx.block.init_velocity", Map.of("vx", "5", "vy", "0", "vz", "0"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bU", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(),
                List.of(
                        new VfxFlowEdge("spawnA", "init"),
                        new VfxFlowEdge("spawnB", "init"),
                        new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        // 两个 spawn context 各产 1 粒子，init 拿到并集（2 个）都设 vx=5 → x=0.5
        assertEquals(2, buffer.count());
        for (var i = 0; i < buffer.count(); i++) {
            assertEquals(0.5f, buffer.positionX(i), 1e-5f);
        }
    }

    /**
     * M28b 回归：loop 重启经 setTime 延续时间戳——编辑后粒子为 0 不再导致 t 冻结。
     */
    @Test
    void setTimeContinuesAcrossRestart() {
        var system = new VfxSystem("loop-time",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_burst", Map.of("count", "5", "lifetime", "0.3"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bA", "vfx.block.update_age", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "update")),
                List.of(),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, 42L, List.of());
        // 播完：burst 5 粒 + lifetime 0.3 → 跑 1s 后粒子为 0
        for (var i = 0; i < 60; i++) sim.step(1f / 60f);
        assertEquals(0, sim.buffer().count());
        assertTrue(sim.time() > 0f);

        // 模拟编辑器 loop 重启：新建模拟器 + setTime 延续原时间
        var continued = sim.time();
        var sim2 = new VfxSystemSimulator(system, blocks, 42L, List.of());
        sim2.setTime(continued);
        sim2.step(1f / 60f);
        // 时间持续增加，不归零（UI 的 t 不再冻结）
        assertTrue(sim2.time() > continued, "loop restart must continue time");
    }
}
