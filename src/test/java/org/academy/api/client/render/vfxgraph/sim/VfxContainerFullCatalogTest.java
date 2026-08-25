package org.academy.api.client.render.vfxgraph.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M27 全量迁移验证：42 块 + 全部算子注册、复杂容器系统全链路模拟（spawn→init→update→collision→over-life→output）。
 */
class VfxContainerFullCatalogTest {
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

    @Test
    void fullBlockCatalogRegistered() {
        // M27：spawn 4 + init 8 + update 10 + collision 5 + over-life 4 + orient 4 + output 7 = 42 块；
        // M22：arc 3 发射块（spawn 类）+ output_arc 1 = 46 块；
        // M29：arc_surface 重写 + arc_contact/arc_spark 新增 = 48 块
        String[] expected = {
                "vfx.block.spawn_rate", "vfx.block.spawn_burst", "vfx.block.spawn_periodic", "vfx.block.spawn_distance",
                "vfx.block.init_position", "vfx.block.init_velocity", "vfx.block.init_color", "vfx.block.init_size",
                "vfx.block.init_rotation", "vfx.block.init_lifetime", "vfx.block.init_mass", "vfx.block.init_randomize",
                "vfx.block.update_velocity", "vfx.block.update_gravity", "vfx.block.update_force", "vfx.block.update_noise",
                "vfx.block.update_turbulence", "vfx.block.update_vortex", "vfx.block.update_drag", "vfx.block.update_damping",
                "vfx.block.update_age", "vfx.block.update_fade",
                "vfx.block.collision_ground", "vfx.block.collision_plane", "vfx.block.collision_sphere",
                "vfx.block.bounds", "vfx.block.kill",
                "vfx.block.life_color", "vfx.block.life_alpha", "vfx.block.life_size", "vfx.block.life_velocity",
                "vfx.block.orient_face_camera", "vfx.block.orient_velocity", "vfx.block.orient_fixed", "vfx.block.orient_spin",
                "vfx.block.output_point", "vfx.block.output_quad", "vfx.block.output_quad_additive", "vfx.block.output_quad_glow",
                "vfx.block.output_mesh", "vfx.block.output_line", "vfx.block.output_ribbon",
                "vfx.block.arc_bolt", "vfx.block.arc_orbit", "vfx.block.arc_surface", "vfx.block.output_arc",
                "vfx.block.arc_contact", "vfx.block.arc_spark"
        };
        for (String id : expected) {
            assertNotNull(blocks.find(id), "block should be registered: " + id);
        }
        assertEquals(48, blocks.find("vfx.block.spawn_rate") != null ? countBlocks() : 0);
        assertEquals(48, countBlocks());
    }

    @Test
    void allOperatorsRegistered() {
        String[] expected = {
                "vfx.op.attr_position", "vfx.op.attr_velocity", "vfx.op.attr_size", "vfx.op.attr_color",
                "vfx.op.attr_alpha", "vfx.op.attr_age", "vfx.op.attr_lifetime", "vfx.op.attr_rotation",
                "vfx.op.attr_mass", "vfx.op.attr_seed", "vfx.op.attr_layer",
                "vfx.op.constant", "vfx.op.param_float", "vfx.op.param_vec3", "vfx.op.param_color",
                "vfx.op.add", "vfx.op.sub", "vfx.op.mul", "vfx.op.div",
                "vfx.op.curve", "vfx.op.gradient", "vfx.op.param_curve", "vfx.op.param_gradient"
        };
        for (String id : expected) {
            assertNotNull(ops.find(id), "operator should be registered: " + id);
        }
    }

    /** 全链路：burst 球面发射 → init 速度/尺寸/随机 → 积分 + 重力 + 地面碰撞 → 生命周期 → 输出。 */
    @Test
    void fullPipelineSimulates() {
        var system = new VfxSystem("full",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_burst",
                                        Map.of("count", "20", "lifetime", "5", "size", "0.3", "shape", "sphere", "radius", "1.5"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bV", "vfx.block.init_velocity", Map.of("vy", "8", "random", "2")),
                                block("bZ", "vfx.block.init_size", Map.of("size", "0.3")),
                                block("bR", "vfx.block.init_randomize", Map.of("size", "0.2", "vel", "0.3"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bG", "vfx.block.update_gravity", Map.of("gravity", "-20")),
                                block("bU", "vfx.block.update_velocity", Map.of()),
                                block("bC", "vfx.block.collision_ground", Map.of("bounce", "0.6", "kill", "false")),
                                block("bA", "vfx.block.update_age", Map.of()),
                                block("bF", "vfx.block.update_fade", Map.of())),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_quad", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update"), new VfxFlowEdge("update", "out")),
                List.of(),
                List.of(),
                List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        // 每帧 1/60s，跑 3 秒
        for (int i = 0; i < 180; i++) {
            sim.step(1f / 60f);
        }
        var buffer = sim.buffer();
        // 粒子在重力+反弹下应保留在地面附近且 y>=0
        assertTrue(buffer.count() > 0);
        for (int i = 0; i < buffer.count(); i++) {
            assertTrue(buffer.positionY(i) >= -1e-4f, "particle must stay above ground");
            assertTrue(buffer.alpha(i) >= 0f, "alpha must not go negative");
        }
        // 粒子位置在有限范围内（积分稳定，无 NaN）
        for (int i = 0; i < buffer.count(); i++) {
            assertTrue(Float.isFinite(buffer.positionX(i)));
            assertTrue(Float.isFinite(buffer.positionY(i)));
            assertTrue(Float.isFinite(buffer.positionZ(i)));
        }
    }

    /** over-life 引用黑板曲线/渐变参数：批次 init 后 alpha/size 随寿命曲线变化。 */
    @Test
    void overLifeCurveReferenced() {
        var curveParam = new org.academy.api.client.render.graph.model.GraphParameter(
                "life_curve", "Life Curve", org.academy.api.client.render.graph.type.ValueType.CURVE,
                org.academy.api.client.render.graph.type.Value.curve(new org.academy.api.client.render.graph.type.Curve(List.of(
                        new org.academy.api.client.render.graph.type.Curve.Keyframe(0f, 1f, 0f, 0f, org.academy.api.client.render.graph.type.Curve.Interpolation.LINEAR),
                        new org.academy.api.client.render.graph.type.Curve.Keyframe(1f, 0f, 0f, 0f, org.academy.api.client.render.graph.type.Curve.Interpolation.LINEAR)))),
                java.util.Optional.empty());
        var gradientParam = new org.academy.api.client.render.graph.model.GraphParameter(
                "fire_grad", "Gradient", org.academy.api.client.render.graph.type.ValueType.GRADIENT,
                org.academy.api.client.render.graph.type.Value.gradient(new org.academy.api.client.render.graph.type.Gradient(List.of(
                        new org.academy.api.client.render.graph.type.Gradient.ColorStop(0f, 1f, 0.5f, 0.1f, 1f),
                        new org.academy.api.client.render.graph.type.Gradient.ColorStop(1f, 0.1f, 0.05f, 0f, 0f)))),
                java.util.Optional.empty());

        var system = new VfxSystem("life",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.spawn_burst", Map.of("count", "10", "lifetime", "1", "size", "1"))),
                        ctx("init", VfxContextType.INITIALIZE,
                                block("bI", "vfx.block.init_size", Map.of("size", "1"))),
                        ctx("update", VfxContextType.UPDATE,
                                block("bA", "vfx.block.update_age", Map.of()),
                                block("bCol", "vfx.block.life_color", Map.of("gradient", "fire_grad")),
                                block("bAl", "vfx.block.life_alpha", Map.of("curve", "life_curve")),
                                block("bSz", "vfx.block.life_size", Map.of("curve", "life_curve")))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "init"), new VfxFlowEdge("init", "update")),
                List.of(),
                List.of(curveParam, gradientParam),
                List.of());

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of(curveParam, gradientParam));
        // 跑 0.5s（寿命 1s → t=0.5 → alpha≈0.5, size≈0.5）
        for (int i = 0; i < 30; i++) {
            sim.step(1f / 60f);
        }
        var buffer = sim.buffer();
        assertTrue(buffer.count() > 0);
        // alpha = startAlpha(1) * curve(0.5) ≈ 0.5
        assertEquals(0.5f, buffer.alpha(0), 0.05f);
        // size = startSize(1) * curve(0.5) ≈ 0.5
        assertEquals(0.5f, buffer.size(0), 0.05f);
        // 颜色沿渐变：t≈0.5 → 橙偏暗
        assertTrue(buffer.colorR(0) > 0.4f);
        assertTrue(buffer.colorB(0) < 0.1f);
    }

    /** arc 块 origin_x/y/z：曲线应以发射器 origin 为基点（此前硬编码 (0,0,0)，移动发射器对 arc 无效）。 */
    @Test
    void arcBoltRespectsOrigin() {
        var system = new VfxSystem("arcOrigin",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bB", "vfx.block.arc_bolt",
                                        Map.of("from_x", "2", "from_y", "3", "from_z", "4",
                                                "to_x", "2", "to_y", "5", "to_z", "4",
                                                "probability", "1", "branch_depth", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "out")),
                List.of(),
                List.of(),
                List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);

        assertEquals(1, sim.arcBuffer().count());
        var arc = sim.arcBuffer().arc(0);
        assertTrue(arc.size() > 0);
        // 主弧 from=(2,3,4) to=(2,5,4)，曲线落在 from/to 之间（噪声会轻微漂移 x/z，故不精确断言 x/z）
        for (int i = 0; i < arc.size(); i++) {
            float dy = arc.y(i);
            assertTrue(dy >= 2.9f && dy <= 5.6f, "arc point y out of range: " + dy);
        }
    }

    private int countBlocks() {
        int n = 0;
        for (var type : metadata.all()) {
            if (type.id().startsWith("vfx.block.")) n++;
        }
        return n;
    }
}
