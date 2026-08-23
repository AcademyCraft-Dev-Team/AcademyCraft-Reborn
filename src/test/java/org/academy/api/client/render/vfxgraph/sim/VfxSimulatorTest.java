package org.academy.api.client.render.vfxgraph.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VfxSimulatorTest {
    private VfxNodeRegistry vfxRegistry;

    @BeforeEach
    void setUp() {
        vfxRegistry = new VfxNodeRegistry();
        VfxNodes.registerAll(new SimpleNodeRegistry(), vfxRegistry);
    }

    private static GraphNode node(String id, String type, Map<String, String> props) {
        return new GraphNode(id, type, props, List.of(), 0f, 0f);
    }

    private VfxSimulator simulator(List<GraphNode> nodes) {
        return new VfxSimulator(nodes, vfxRegistry, 42L);
    }

    @Test
    void spawnsAtRate() {
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point"))
        ));
        for (int i = 0; i < 5; i++) sim.step(0.1f);
        assertEquals(5, sim.buffer().count());
    }

    @Test
    void integratesVelocity() {
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "vy", "2", "shape", "point")),
                node("vel", "vfx.update_velocity", Map.of())
        ));
        for (int i = 0; i < 3; i++) sim.step(0.1f);
        assertEquals(0.6f, sim.buffer().positionY(0), 1e-5f);
    }

    @Test
    void appliesGravity() {
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point")),
                node("grav", "vfx.update_gravity", Map.of("gravity", "-10"))
        ));
        sim.step(0.1f);
        sim.step(0.1f);
        assertEquals(-2.0f, sim.buffer().velocityY(0), 1e-5f);
    }

    @Test
    void killsAtLifetime() {
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "0.3", "shape", "point")),
                node("age", "vfx.update_age", Map.of())
        ));
        for (int i = 0; i < 6; i++) sim.step(0.1f);
        // 稳态：最后 ~2 个粒子存活（生命周期 0.3s，每帧 0.1s）
        assertTrue(sim.buffer().count() <= 3);
        assertTrue(sim.buffer().count() >= 1);
    }

    @Test
    void fadesAlphaAndSizeOverLife() {
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "1", "size", "0.5", "shape", "point")),
                node("age", "vfx.update_age", Map.of()),
                node("fade", "vfx.update_fade", Map.of())
        ));
        sim.step(0.1f);
        var buffer = sim.buffer();
        // 粒子 0 出生后 age=0.1, t=0.1 → alpha=0.9, size=0.45
        assertEquals(0.9f, buffer.alpha(0), 1e-5f);
        assertEquals(0.45f, buffer.size(0), 1e-5f);
    }

    @Test
    void initDoesNotReapplyToOldParticlesWhenSpawnProducesNothing() {
        // 暂停/无新粒子帧：spawn 不产粒子，后续 init_randomize 不应改写已有粒子（否则暂停时抖动/消失）
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point")),
                node("rand", "vfx.init_randomize", Map.of("pos", "10", "vel", "10", "size", "10", "lifetime", "10"))
        ));
        sim.step(0.1f); // 产生 1 个粒子
        var buffer = sim.buffer();
        assertEquals(1, buffer.count());
        float x = buffer.positionX(0);
        float y = buffer.positionY(0);
        float z = buffer.positionZ(0);
        float sz = buffer.size(0);
        float life = buffer.lifetime(0);
        // dt=0 帧（暂停）：spawn 不产粒子，init 应空跑，粒子位置/尺寸/寿命保持不变
        sim.step(0f);
        assertEquals(x, buffer.positionX(0), 1e-6f);
        assertEquals(y, buffer.positionY(0), 1e-6f);
        assertEquals(z, buffer.positionZ(0), 1e-6f);
        assertEquals(sz, buffer.size(0), 1e-6f);
        assertEquals(life, buffer.lifetime(0), 1e-6f);
    }

    @Test
    void particlesSurviveLongPause() {
        // 长时间暂停（多帧 dt=0）：粒子既不移动也不死亡
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "2", "shape", "point")),
                node("vel", "vfx.update_velocity", Map.of()),
                node("age", "vfx.update_age", Map.of())
        ));
        for (int i = 0; i < 20; i++) sim.step(0.05f);
        int before = sim.buffer().count();
        assertTrue(before > 0);
        float y0 = sim.buffer().positionY(0);
        for (int i = 0; i < 120; i++) sim.step(0f); // 2 秒暂停
        assertEquals(before, sim.buffer().count());
        assertEquals(y0, sim.buffer().positionY(0), 1e-6f);
        // 恢复后继续正常模拟
        sim.step(0.05f);
        assertTrue(sim.buffer().positionY(0) >= y0);
    }
}
