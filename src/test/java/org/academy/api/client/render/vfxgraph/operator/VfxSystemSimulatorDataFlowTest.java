package org.academy.api.client.render.vfxgraph.operator;

import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.*;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxSystemSimulatorDataFlowTest {
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

    private static VfxOperatorNode op(String id, String type, Map<String, String> props) {
        return new VfxOperatorNode(id, type, props, List.of(), 0f, 0f);
    }

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    private static VfxDataEdge edge(String from, String to, String toPort) {
        return new VfxDataEdge(new Edge.PortRef(from, "out"), new Edge.PortRef(to, toPort));
    }

    /**
     * attr-read → math → init 块端口写回链：vx 端口由 attr_seed×mul 驱动，逐粒子不同。
     */
    @Test
    void attributeDrivenMathWritesPerParticle() {
        var system = new VfxSystem("dataflow",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("b_spawn", "vfx.block.spawn_rate",
                                        Map.of("rate", "10", "lifetime", "100", "size", "0.5"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("b_init", "vfx.block.init_velocity", Map.of("vx", "0", "vy", "0", "vz", "0"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("b_upd", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(
                        op("o_seed", "vfx.op.attr_seed", Map.of()),
                        op("o_scale", "vfx.op.constant", Map.of("value", "2")),
                        op("o_mul", "vfx.op.mul", Map.of())),
                List.of(
                        new VfxFlowEdge("spawn", "init"),
                        new VfxFlowEdge("init", "update")),
                List.of(
                        edge("o_seed", "o_mul", "a"),
                        edge("o_scale", "o_mul", "b"),
                        edge("o_mul", "b_init", "vx")),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(1, buffer.count());
        // 粒子 seed 稳定值 S，vx = S*2
        var seed = buffer.seed(0);
        assertEquals(seed * 2f, buffer.velocityX(0), 1e-5f);
    }

    /**
     * 多粒子：每粒子 seed 不同 → 端口驱动的 vx 逐粒子不同（验证非折叠）。
     */
    @Test
    void attributeDrivenValueDiffersPerParticle() {
        var system = new VfxSystem("per-particle",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("b_spawn", "vfx.block.spawn_burst",
                                        Map.of("count", "4", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("b_init", "vfx.block.init_velocity", Map.of("vx", "0", "vy", "0", "vz", "0"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("b_upd", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(
                        op("o_seed", "vfx.op.attr_seed", Map.of()),
                        op("o_mul", "vfx.op.mul", Map.of("b", "1"))),
                List.of(
                        new VfxFlowEdge("spawn", "init"),
                        new VfxFlowEdge("init", "update")),
                List.of(
                        edge("o_seed", "o_mul", "a"),
                        edge("o_mul", "b_init", "vx")),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(4, buffer.count());
        var v0 = buffer.velocityX(0);
        var distinct = new HashSet<Float>();
        for (var i = 0; i < buffer.count(); i++) {
            distinct.add(buffer.velocityX(i));
            assertEquals(buffer.seed(i), buffer.velocityX(i), 1e-5f);
        }
        assertTrue(distinct.size() >= 2, "per-particle values should differ across seeds");
    }

    /**
     * 算子间连接 + 输入端口绑定：mul 的 b 来自 constant，a 来自 attr。
     */
    @Test
    void operatorChainWithConstantInput() {
        var system = new VfxSystem("chain",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("b_spawn", "vfx.block.spawn_rate",
                                        Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("b_init", "vfx.block.init_size", Map.of("size", "1"))),
                        ctx("update", VfxContextType.UPDATE)
                ),
                List.of(
                        op("o_seed", "vfx.op.attr_seed", Map.of()),
                        op("o_const", "vfx.op.constant", Map.of("value", "0.25")),
                        op("o_mul", "vfx.op.mul", Map.of())),
                List.of(
                        new VfxFlowEdge("spawn", "init"),
                        new VfxFlowEdge("init", "update")),
                List.of(
                        edge("o_seed", "o_mul", "a"),
                        edge("o_const", "o_mul", "b"),
                        edge("o_mul", "b_init", "size")),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(1, buffer.count());
        assertEquals(buffer.seed(0) * 0.25f, buffer.size(0), 1e-5f);
    }

    /**
     * 算子环检测。
     */
    @Test
    void operatorCycleThrows() {
        var system = new VfxSystem("cycle",
                List.of(ctx("spawn", VfxContextType.SPAWN,
                        block("b_spawn", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100")))),
                List.of(
                        op("a", "vfx.op.add", Map.of()),
                        op("b", "vfx.op.add", Map.of())),
                List.of(),
                List.of(
                        new VfxDataEdge(new Edge.PortRef("a", "out"), new Edge.PortRef("b", "a")),
                        new VfxDataEdge(new Edge.PortRef("b", "out"), new Edge.PortRef("a", "a"))),
                List.of(),
                List.of());
        try {
            new VfxSystemSimulator(system, blocks, ops, 1L, List.of());
            throw new AssertionError("expected exception");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("cycle"), e.getMessage());
        }
    }

    /**
     * param 算子：无存活参数注入时用兜底值。
     */
    @Test
    void paramOperatorFallsBackToProperty() {
        var system = new VfxSystem("param",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("b_spawn", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("b_init", "vfx.block.init_velocity", Map.of("vx", "0", "vy", "0", "vz", "0"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("b_upd", "vfx.block.update_velocity", Map.of()))
                ),
                List.of(
                        op("o_p", "vfx.op.param_float", Map.of("param", "speed", "value", "3"))),
                List.of(
                        new VfxFlowEdge("spawn", "init"),
                        new VfxFlowEdge("init", "update")),
                List.of(edge("o_p", "b_init", "vx")),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        // 无绑定 → param 兜底 3
        assertEquals(3f, sim.buffer().velocityX(0), 1e-5f);
    }

    /**
     * 算子端口驱动 init_color 的 color 端口（COLOR 类型）。
     */
    @Test
    void colorOperatorDrivesInitColor() {
        var system = new VfxSystem("color",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("b_spawn", "vfx.block.spawn_rate", Map.of("rate", "10", "lifetime", "100"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("b_init", "vfx.block.init_color", Map.of("color", "1,1,1,1")))
                ),
                List.of(
                        op("o_color", "vfx.op.param_color", Map.of("param", "tint", "r", "0.2", "g", "0.4", "b", "0.6", "a", "0.8"))),
                List.of(new VfxFlowEdge("spawn", "init")),
                List.of(edge("o_color", "b_init", "color")),
                List.of(),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(1, buffer.count());
        assertEquals(0.2f, buffer.colorR(0), 1e-5f);
        assertEquals(0.4f, buffer.colorG(0), 1e-5f);
        assertEquals(0.6f, buffer.colorB(0), 1e-5f);
        assertEquals(0.8f, buffer.alpha(0), 1e-5f);
    }

    /**
     * 元数据端口派生：attr 算子声明输出端口、math 声明 a/b/out。
     */
    @Test
    void operatorMetadataDeclaresPorts() {
        assertTrue(metadata.find("vfx.op.attr_position").ports().stream()
                .anyMatch(p -> p.id().equals("out") && p.direction() == PortDirection.OUTPUT
                        && p.type() == ValueType.VEC3));
        assertTrue(metadata.find("vfx.op.mul").ports().stream()
                .anyMatch(p -> p.id().equals("a") && p.direction() == PortDirection.INPUT));
        assertTrue(metadata.find("vfx.op.mul").ports().stream()
                .anyMatch(p -> p.id().equals("out") && p.direction() == PortDirection.OUTPUT));
        assertTrue(metadata.find("vfx.op.constant").ports().stream()
                .anyMatch(p -> p.id().equals("out") && p.type() == ValueType.FLOAT));
    }
}
