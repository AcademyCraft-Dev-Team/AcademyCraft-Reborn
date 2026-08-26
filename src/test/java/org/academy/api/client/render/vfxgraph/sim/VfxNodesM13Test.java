package org.academy.api.client.render.vfxgraph.sim;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxNodesM13Test {
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
        return new VfxSimulator(nodes, vfxRegistry, 42L, List.of());
    }

    @Test
    void burstSpawnsOnce() {
        var sim = simulator(List.of(node("b", "vfx.spawn_burst", Map.of("count", "3", "shape", "point"))));
        sim.step(0.1f);
        sim.step(0.1f);
        sim.step(0.1f);
        assertEquals(3, sim.buffer().count());
    }

    @Test
    void meshShapeSpawnsOnCubeSurface() {
        var sim = simulator(List.of(node("b", "vfx.spawn_burst",
                Map.of("count", "40", "shape", "mesh", "mesh_scale", "2"))));
        sim.step(0.1f);
        assertEquals(40, sim.buffer().count());
        float eps = 1e-3f;
        for (int i = 0; i < sim.buffer().count(); i++) {
            float x = sim.buffer().positionX(i);
            float y = sim.buffer().positionY(i);
            float z = sim.buffer().positionZ(i);
            assertTrue(x >= -eps && x <= 2f + eps && y >= -eps && y <= 2f + eps && z >= -eps && z <= 2f + eps,
                    "inside scaled cube");
            boolean onSurface = x < eps || x > 2f - eps || y < eps || y > 2f - eps || z < eps || z > 2f - eps;
            assertTrue(onSurface, "on cube surface: " + x + "," + y + "," + z);
        }
    }

    @Test
    void periodicBurstSpawnsEveryInterval() {
        var sim = simulator(List.of(node("p", "vfx.spawn_periodic", Map.of("count", "3", "interval", "0.5", "shape", "point"))));
        sim.step(0.5f);
        assertEquals(3, sim.buffer().count());
        sim.step(0.5f);
        assertEquals(6, sim.buffer().count());
    }

    @Test
    void initLifetimeAppliesOnlyToNewSpawns() {
        var sim = simulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "3", "shape", "point")),
                node("life", "vfx.init_lifetime", Map.of("lifetime", "2"))
        ));
        sim.step(0.1f);
        for (int i = 0; i < 3; i++) {
            assertEquals(2f, sim.buffer().lifetime(i));
        }
    }

    @Test
    void initVelocityRandomZeroIsExact() {
        var sim = simulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "1", "shape", "point")),
                node("vel", "vfx.init_velocity", Map.of("vx", "1", "vy", "2", "vz", "3", "random", "0"))
        ));
        sim.step(0.1f);
        assertEquals(1f, sim.buffer().velocityX(0));
        assertEquals(2f, sim.buffer().velocityY(0));
        assertEquals(3f, sim.buffer().velocityZ(0));
    }

    @Test
    void initPositionBoxContainsParticles() {
        var sim = simulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "50", "shape", "point")),
                node("pos", "vfx.init_position", Map.of("shape", "box", "half_x", "1", "half_y", "1", "half_z", "1"))
        ));
        sim.step(0.1f);
        var buffer = sim.buffer();
        for (int i = 0; i < buffer.count(); i++) {
            assertTrue(buffer.positionX(i) >= -1f && buffer.positionX(i) <= 1f);
            assertTrue(buffer.positionY(i) >= -1f && buffer.positionY(i) <= 1f);
            assertTrue(buffer.positionZ(i) >= -1f && buffer.positionZ(i) <= 1f);
        }
    }

    @Test
    void groundCollisionBounces() {
        var sim = simulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "1", "shape", "point")),
                node("g", "vfx.update_gravity", Map.of("gravity", "-10")),
                node("c", "vfx.collision_ground", Map.of("bounce", "0.5", "kill", "false"))
        ));
        sim.step(0.1f);
        assertEquals(0.5f, sim.buffer().velocityY(0), 1e-5f);
        assertEquals(0f, sim.buffer().positionY(0), 1e-5f);
    }

    @Test
    void boundsKillsOutside() {
        var sim = simulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "1", "shape", "point")),
                node("bd", "vfx.bounds", Map.of("min_x", "1"))
        ));
        sim.step(0.1f);
        assertEquals(0, sim.buffer().count());
    }

    @Test
    void spinAddsRotation() {
        var sim = simulator(List.of(
                node("b", "vfx.spawn_burst", Map.of("count", "1", "shape", "point")),
                node("spin", "vfx.orient_spin", Map.of("speed", "2"))
        ));
        sim.step(0.1f);
        assertEquals(0.2f, sim.buffer().rotation(0), 1e-5f);
    }

    @Test
    void alphaOverLifetimeUsesCurveParam() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 1f),
                new Curve.Keyframe(1f, 0f)
        ));
        var parameters = List.of(new GraphParameter("fade", "Fade", ValueType.CURVE, Value.curve(curve), Optional.empty()));
        var sim = new VfxSimulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "10", "shape", "point")),
                node("age", "vfx.update_age", Map.of()),
                node("alpha", "vfx.life_alpha", Map.of("curve", "fade"))
        ), vfxRegistry, 42L, parameters);

        sim.step(0.5f);
        // 粒子 0：age=0.5, t=0.05 → alpha=0.95
        assertEquals(0.95f, sim.buffer().alpha(0), 1e-4f);
    }

    @Test
    void outputLinePushesTrail() {
        var sim = simulator(List.of(
                node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "10", "shape", "point")),
                node("line", "vfx.output_line", Map.of())
        ));
        sim.step(0.1f);
        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(2, buffer.trailSize(0));
        assertTrue(buffer.trailX(0, 0) == 0f && buffer.trailX(0, 1) == 0f);
    }

    @Test
    void lifeAlphaLayerFilterAffectsOnlySmokeLayer() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 0.1f),
                new Curve.Keyframe(1f, 0.1f)
        ));
        var parameters = List.of(new GraphParameter("smk", "Smoke", ValueType.CURVE, Value.curve(curve), Optional.empty()));
        var sim = new VfxSimulator(List.of(
                node("fire", "vfx.spawn_burst", Map.of("count", "1", "shape", "point", "color", "1,1,1,0.8")),
                node("smoke", "vfx.spawn_burst", Map.of("count", "1", "shape", "point", "color", "0.5,0.5,0.5,0.8", "layer", "smoke")),
                node("age", "vfx.update_age", Map.of()),
                node("alpha", "vfx.life_alpha", Map.of("curve", "smk", "layer", "smoke"))
        ), vfxRegistry, 42L, parameters);

        sim.step(0.1f);
        var buffer = sim.buffer();
        assertEquals(2, buffer.count());
        int fireIdx = buffer.layer(0) == 0 ? 0 : 1;
        int smokeIdx = 1 - fireIdx;
        // fire 层不受 layer=smoke 的曲线影响；smoke 层 alpha = 0.8 * 0.1
        assertEquals(0.8f, buffer.alpha(fireIdx), 1e-5f);
        assertEquals(0.08f, buffer.alpha(smokeIdx), 1e-5f);
    }

    @Test
    void lifeSizeLayerFilterAffectsOnlySmokeLayer() {
        var curve = new Curve(List.of(
                new Curve.Keyframe(0f, 2f),
                new Curve.Keyframe(1f, 2f)
        ));
        var parameters = List.of(new GraphParameter("gr", "Grow", ValueType.CURVE, Value.curve(curve), Optional.empty()));
        var sim = new VfxSimulator(List.of(
                node("fire", "vfx.spawn_burst", Map.of("count", "1", "shape", "point", "size", "0.3")),
                node("smoke", "vfx.spawn_burst", Map.of("count", "1", "shape", "point", "size", "0.5", "layer", "smoke")),
                node("age", "vfx.update_age", Map.of()),
                node("size", "vfx.life_size", Map.of("curve", "gr", "layer", "smoke"))
        ), vfxRegistry, 42L, parameters);

        sim.step(0.1f);
        var buffer = sim.buffer();
        int fireIdx = buffer.layer(0) == 0 ? 0 : 1;
        int smokeIdx = 1 - fireIdx;
        assertEquals(0.3f, buffer.size(fireIdx), 1e-5f);
        assertEquals(1.0f, buffer.size(smokeIdx), 1e-5f);
    }
}
