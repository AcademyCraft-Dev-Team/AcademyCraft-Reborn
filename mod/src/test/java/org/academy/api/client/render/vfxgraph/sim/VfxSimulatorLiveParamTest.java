package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxSimulatorLiveParamTest {
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
    void paramAttributeReadsLiveValueEachStep() {
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point")),
                node("vel", "vfx.init_velocity", Map.of("vx", "0", "vy", "1", "vz", "0", "random", "0", "param", "dir")),
                node("integ", "vfx.update_velocity", Map.of())
        ), vfxRegistry, 42L);

        // 无存活参数：vy 烘焙值 1 → 粒子 0 出生后 vy=1
        sim.step(0.1f);
        assertEquals(0.1f, sim.buffer().positionY(0), 1e-5f);

        // 存活参数绑定 dir → (0,5,0)（不重建模拟器）：新粒子用 5
        sim.setLiveParam("dir", Value.of(new Vector3f(0f, 5f, 0f)));
        sim.step(0.1f);
        // 粒子 0 出生时 vy=1，第二帧仍以 vy=1 积分：0.1 + 0.1
        assertEquals(0.2f, sim.buffer().positionY(0), 1e-5f);
        // 粒子 1 本帧出生，vy=5
        assertEquals(0.5f, sim.buffer().positionY(1), 1e-5f);
    }

    @Test
    void paramFallbackUsesBakedValueWhenNotBound() {
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point")),
                node("grav", "vfx.update_gravity", Map.of("gravity", "-10"))
        ), vfxRegistry, 42L);

        sim.step(0.1f);
        sim.step(0.1f);
        assertEquals(-2.0f, sim.buffer().velocityY(0), 1e-5f);
    }

    @Test
    void paramFloatNodeSeedsDefaultValue() {
        var sim = new VfxSimulator(List.of(
                node("p", "vfx.param_float", Map.of("param", "rate", "value", "25")),
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point", "param", "rate"))
        ), vfxRegistry, 42L);

        sim.step(0.1f);
        // param_float 节点先注入 rate=25（无外部绑定时），spawn 用 25
        assertEquals(2, sim.buffer().count());
    }

    @Test
    void externalBindingOverridesParamNodeSeed() {
        var sim = new VfxSimulator(List.of(
                node("p", "vfx.param_float", Map.of("param", "rate", "value", "25")),
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point", "param", "rate"))
        ), vfxRegistry, 42L);

        sim.setLiveParam("rate", Value.of(0f));
        sim.step(0.1f);
        assertEquals(0, sim.buffer().count());
    }

    @Test
    void liveColorParamDrivesInitColor() {
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point")),
                node("col", "vfx.init_color", Map.of("color", "1,1,1,1", "param", "tint"))
        ), vfxRegistry, 42L);

        sim.setLiveParam("tint", Value.color(0.2f, 0.4f, 0.6f, 0.8f));
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(0.2f, buffer.colorR(0), 1e-5f);
        assertEquals(0.4f, buffer.colorG(0), 1e-5f);
        assertEquals(0.6f, buffer.colorB(0), 1e-5f);
        assertEquals(0.8f, buffer.alpha(0), 1e-5f);
    }

    @Test
    void liveVec3ParamDrivesInitVelocity() {
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point")),
                node("vel", "vfx.init_velocity", Map.of("vx", "0", "vy", "1", "vz", "0", "random", "0", "param", "dir")),
                node("integ", "vfx.update_velocity", Map.of())
        ), vfxRegistry, 42L);

        sim.setLiveParam("dir", Value.of(new Vector3f(1f, 2f, 3f)));
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(0.1f, buffer.positionX(0), 1e-5f);
        assertEquals(0.2f, buffer.positionY(0), 1e-5f);
        assertEquals(0.3f, buffer.positionZ(0), 1e-5f);
    }

    @Test
    void nodeCatalogHas45Nodes() {
        var metadata = new SimpleNodeRegistry();
        VfxNodes.registerAll(metadata, vfxRegistry);
        assertTrue(metadata.all().size() >= 45, "VFX node catalog should be >= 45, got " + metadata.all().size());
    }
}
