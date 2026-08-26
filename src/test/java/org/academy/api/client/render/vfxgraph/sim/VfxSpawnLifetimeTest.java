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
 * Bug 修复回归测试：
 * 1. spawn_burst/periodic/distance 默认 lifetime（不再首帧即灭 / 无 update_age 时粒子存活）
 * 2. ParticleBuffer.spawn 重置关键字段（swap-remove 槽位复用不残留旧 lifetime）
 */
class VfxSpawnLifetimeTest {
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
    void burstParticlesSurviveWithoutInitLifetime() {
        // Bug 修复：spawn_burst 默认 lifetime=1，加 update_age 后粒子不应首帧即灭
        var sim = new VfxSimulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "10", "shape", "point")),
                node("age", "vfx.update_age", Map.of())
        ), vfxRegistry, 42L, List.of());
        sim.step(0.1f);
        sim.step(0.1f);
        // lifetime=1, age 累计 0.2 → 仍存活
        assertEquals(10, sim.buffer().count());
    }

    @Test
    void periodicParticlesHaveDefaultLifetime() {
        var sim = new VfxSimulator(List.of(
                node("p", "vfx.spawn_periodic", Map.of("count", "3", "interval", "0.5", "shape", "point")),
                node("age", "vfx.update_age", Map.of())
        ), vfxRegistry, 42L, List.of());
        sim.step(0.5f);
        sim.step(0.5f);
        assertTrue(sim.buffer().count() > 0);
    }

    @Test
    void spawnResetsLifetimeOnSlotReuse() {
        // Bug 修复：swap-remove 后槽位复用不得继承旧 lifetime
        var buffer = new ParticleBuffer();
        // 粒子 0：lifetime=10
        int a = buffer.spawn();
        buffer.setLifetime(a, 10f);
        // 粒子 1：lifetime=0（默认）
        buffer.spawn();
        // 杀掉粒子 0（swap-remove：粒子 1 移入槽 0）
        buffer.kill(0);
        // 新 spawn：回到槽 0 或槽 1，lifetime 必须为 0 而非残留的 10
        int b = buffer.spawn();
        assertEquals(0f, buffer.lifetime(b), 1e-5f);
        assertEquals(0f, buffer.age(b), 1e-5f);
    }

    @Test
    void cylinderShapeIsWiredNotFallingBackToPoint() {
        // Bug 修复：shape="cylinder" 不再静默落回 point（粒子 XZ 应非原点）
        var sim = new VfxSimulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "100", "shape", "cylinder",
                        "radius", "2", "cone_height", "3"))
        ), vfxRegistry, 42L, List.of());
        sim.step(0.1f);
        boolean offOrigin = false;
        for (int i = 0; i < sim.buffer().count(); i++) {
            float xz = sim.buffer().positionX(i) * sim.buffer().positionX(i)
                    + sim.buffer().positionZ(i) * sim.buffer().positionZ(i);
            if (xz > 0.01f) {
                offOrigin = true;
                break;
            }
        }
        assertTrue(offOrigin, "cylinder shape should sample off-origin, not point");
    }

    @Test
    void torusShapeIsWired() {
        var sim = new VfxSimulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "100", "shape", "torus",
                        "radius", "2", "half_x", "0.3"))
        ), vfxRegistry, 42L, List.of());
        sim.step(0.1f);
        boolean offOrigin = false;
        for (int i = 0; i < sim.buffer().count(); i++) {
            float xz = sim.buffer().positionX(i) * sim.buffer().positionX(i)
                    + sim.buffer().positionZ(i) * sim.buffer().positionZ(i);
            if (xz > 0.01f) {
                offOrigin = true;
                break;
            }
        }
        assertTrue(offOrigin, "torus shape should sample off-origin, not point");
    }

    @Test
    void circleEdgeShapeIsWired() {
        var sim = new VfxSimulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "100", "shape", "circle_edge", "radius", "2"))
        ), vfxRegistry, 42L, List.of());
        sim.step(0.1f);
        boolean offOrigin = false;
        for (int i = 0; i < sim.buffer().count(); i++) {
            float xz = sim.buffer().positionX(i) * sim.buffer().positionX(i)
                    + sim.buffer().positionZ(i) * sim.buffer().positionZ(i);
            if (xz > 0.01f) {
                offOrigin = true;
                break;
            }
        }
        assertTrue(offOrigin, "circle_edge shape should sample off-origin, not point");
    }
}
