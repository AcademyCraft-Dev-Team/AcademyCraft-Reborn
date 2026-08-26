package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.GradientSampler;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.*;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.*;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * VFX 容器块目录（M24–M27）：注册块元数据（核心 NodeRegistry）与块工厂（VfxBlockRegistry）。
 *
 * <p><b>阶段约定（容器执行器）</b>：spawn 块经 {@code SimContext.emitBatch} 记录本帧新粒子批次；
 * init 块只处理 {@code SimContext.incomingBatches}（由执行器按 flow 边注入）；
 * update/collision/over-life/orient 块处理全部存活粒子；output 块仅提供 RenderSpec（M21l 数据驱动）。</p>
 *
 * <p>M27 全量迁移：spawn 4 / init 8 / update 10 / collision 5 / over-life 4 / orient 4 / output 7 = 42 块
 * （param 系 5 算子见 {@code VfxOperators}），共 47 节点。块语义与 {@code VfxNodes} 对应节点一致，
 * 但用批次（emitBatch/incomingBatches）替代 {@code spawnStart} 单点耦合。</p>
 */
public final class VfxBlocks {
    private VfxBlocks() {
    }

    /**
     * spawn/init 块共享的基础属性。
     */
    private static final List<PropertySpec> PARTICLE_BASIC = List.of(
            prop("lifetime", ValueType.FLOAT, Value.of(1f)),
            prop("size", ValueType.FLOAT, Value.of(0.1f)),
            prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)),
            prop("vx", ValueType.FLOAT, Value.of(0f)),
            prop("vy", ValueType.FLOAT, Value.of(0f)),
            prop("vz", ValueType.FLOAT, Value.of(0f)),
            prop("layer", ValueType.STRING, Value.string("fire"))
    );

    /**
     * 发射形状属性（spawn/init_position 共用）。
     */
    private static final List<PropertySpec> SHAPE_PROPS = List.of(
            prop("shape", ValueType.STRING, Value.string("point")),
            prop("origin_x", ValueType.FLOAT, Value.of(0f)),
            prop("origin_y", ValueType.FLOAT, Value.of(0f)),
            prop("origin_z", ValueType.FLOAT, Value.of(0f)),
            prop("radius", ValueType.FLOAT, Value.of(1f)),
            prop("half_x", ValueType.FLOAT, Value.of(1f)),
            prop("half_y", ValueType.FLOAT, Value.of(1f)),
            prop("half_z", ValueType.FLOAT, Value.of(1f)),
            prop("cone_height", ValueType.FLOAT, Value.of(2f)),
            prop("mesh", ValueType.STRING, Value.string("")),
            prop("mesh_scale", ValueType.FLOAT, Value.of(1f))
    );

    /**
     * spawn 尾块：PARTICLE_BASIC + SHAPE_PROPS。
     */
    private static final List<PropertySpec> SPAWN_TAIL = Stream.concat(
            PARTICLE_BASIC.stream(), SHAPE_PROPS.stream()).toList();

    /**
     * output 块共享属性默认（数据驱动，M21l）：着色器/混合值**不写死具体 shader id**，
     * 一律空串中性默认，由图上显式指定（缺失时渲染层兜底 billboard/translucent）；
     * layer 过滤该输出负责渲染的粒子层（空串=全部，分层外观用多输出块表达）。
     */
    private static final List<PropertySpec> OUTPUT_PROPERTIES = List.of(
            prop("vertex", ValueType.STRING, Value.string("")),
            prop("shader", ValueType.STRING, Value.string("")),
            prop("blend", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    /**
     * arc_surface/arc_contact 共享属性（M29，Blender 表面电弧：布点 + 短弧 + 噪声 + 端点吸附）。
     */
    private static final List<PropertySpec> ARC_SURFACE_PROPS = List.of(
            prop("mesh", ValueType.STRING, Value.string("builtin:plane")),
            prop("density", ValueType.FLOAT, Value.of(3.8f)),
            prop("probability", ValueType.FLOAT, Value.of(0.5f)),
            prop("frequency", ValueType.FLOAT, Value.of(1f)),
            prop("frame_period", ValueType.INT, Value.of(3)),
            prop("fps", ValueType.FLOAT, Value.of(30f)),
            prop("height", ValueType.FLOAT, Value.of(1f)),
            prop("curve", ValueType.FLOAT, Value.of(0.78f)),
            prop("width", ValueType.FLOAT, Value.of(0.01f)),
            prop("segments", ValueType.INT, Value.of(12)),
            prop("lifetime", ValueType.FLOAT, Value.of(1f)),
            prop("color", ValueType.COLOR, Value.color(0.8f, 0.8f, 0.8f, 1f)),
            prop("emission", ValueType.FLOAT, Value.of(1f)),
            prop("noise_strength", ValueType.FLOAT, Value.of(0.5f)),
            prop("drift_speed", ValueType.FLOAT, Value.of(1.5f)),
            prop("origin_x", ValueType.FLOAT, Value.of(0f)),
            prop("origin_y", ValueType.FLOAT, Value.of(0f)),
            prop("origin_z", ValueType.FLOAT, Value.of(0f))
    );

    /**
     * arc_contact 专属属性：接触对象（MeshAssets id）+ 距离剔除阈值 + 接触对象位移。
     */
    private static final List<PropertySpec> ARC_CONTACT_PROPS = List.of(
            prop("contact_mesh", ValueType.STRING, Value.string("builtin:sphere")),
            prop("contact_range", ValueType.FLOAT, Value.of(4.1f)),
            prop("contact_origin_x", ValueType.FLOAT, Value.of(0f)),
            prop("contact_origin_y", ValueType.FLOAT, Value.of(4.3f)),
            prop("contact_origin_z", ValueType.FLOAT, Value.of(0f))
    );

    /**
     * arc_spark 粒子火花属性（M30，Blender 粒子系统：弧→点 + 溅射 + 重力 + 迷你管）。
     */
    private static final List<PropertySpec> ARC_SPARK_PROPS = List.of(
            prop("probability", ValueType.FLOAT, Value.of(0.48f)),
            prop("max_sparks", ValueType.INT, Value.of(10)),
            prop("splash_speed", ValueType.FLOAT, Value.of(1.23f)),
            prop("gravity", ValueType.FLOAT, Value.of(-0.9f)),
            prop("lifetime", ValueType.FLOAT, Value.of(0.5f)),
            prop("scale", ValueType.FLOAT, Value.of(0.83f)),
            prop("radius", ValueType.FLOAT, Value.of(0.005f)),
            prop("color", ValueType.COLOR, Value.color(0.23f, 0.35f, 0.69f, 1f)),
            prop("emission", ValueType.FLOAT, Value.of(1f))
    );

    /**
     * output_arc 的 ARC 观感参数（数据驱动，M22-Rev2）：Blender 式参数 + 火花参数。
     */
    private static final List<PropertySpec> ARC_OUTPUT_PROPERTIES = List.of(
            prop("sparks", ValueType.INT, Value.of(8)),
            prop("spark_speed", ValueType.FLOAT, Value.of(2.2f)),
            prop("spark_size", ValueType.FLOAT, Value.of(0.02f)),
            prop("spark_lifetime", ValueType.FLOAT, Value.of(1.0f)),
            prop("spark_gravity", ValueType.FLOAT, Value.of(-9.8f)),
            prop("spark_radius", ValueType.FLOAT, Value.of(0.005f)),
            prop("emission", ValueType.FLOAT, Value.of(1.0f)),
            prop("drift_speed", ValueType.FLOAT, Value.of(1.5f)),
            prop("noise_strength", ValueType.FLOAT, Value.of(0.27f)),
            prop("lifetime", ValueType.FLOAT, Value.of(1.0f)),
            prop("segments", ValueType.INT, Value.of(12)),
            prop("overall_scale", ValueType.FLOAT, Value.of(1.0f)),
            prop("branch_depth", ValueType.INT, Value.of(1)),
            prop("branch_count", ValueType.INT, Value.of(2)),
            prop("branch_angle", ValueType.FLOAT, Value.of(1.57f)),
            prop("branch_length_scale", ValueType.FLOAT, Value.of(0.3f)),
            prop("branch_width_scale", ValueType.FLOAT, Value.of(0.35f)),
            prop("branch_brightness_scale", ValueType.FLOAT, Value.of(0.6f))
    );

    /**
     * over-life curve 系共享（curve/layer）。
     */
    private static final List<PropertySpec> CURVE_LAYER = List.of(
            prop("curve", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    /**
     * noise/turbulence 共享（amplitude/frequency）。
     */
    private static final List<PropertySpec> NOISE = List.of(
            prop("amplitude", ValueType.FLOAT, Value.of(1f)),
            prop("frequency", ValueType.FLOAT, Value.of(1f))
    );

    /**
     * collision_ground/plane 共享尾块（bounce/kill）。
     */
    private static final List<PropertySpec> BOUNCE_KILL = List.of(
            prop("bounce", ValueType.FLOAT, Value.of(0.5f)),
            prop("kill", ValueType.BOOL, Value.of(false))
    );

    public static void registerAll(NodeRegistry metadata, VfxBlockRegistry blocks) {
        // ==================== spawn ====================

        metadata.register(typeWithPorts("vfx.block.spawn_rate", "spawn", "Spawn Rate",
                List.of(in("rate", "Rate", ValueType.FLOAT)),
                List.of(prop("rate", ValueType.FLOAT, Value.of(10f)), prop("param", ValueType.STRING, Value.string(""))),
                SPAWN_TAIL));
        blocks.register("vfx.block.spawn_rate", VfxBlocks::spawnRate);

        metadata.register(typeWithPorts("vfx.block.spawn_burst", "spawn", "Spawn Burst",
                List.of(),
                List.of(prop("count", ValueType.INT, Value.of(10))), SPAWN_TAIL));
        blocks.register("vfx.block.spawn_burst", VfxBlocks::spawnBurst);

        metadata.register(typeWithPorts("vfx.block.spawn_periodic", "spawn", "Spawn Periodic Burst",
                List.of(),
                List.of(prop("count", ValueType.INT, Value.of(5)), prop("interval", ValueType.FLOAT, Value.of(1f))),
                SPAWN_TAIL));
        blocks.register("vfx.block.spawn_periodic", VfxBlocks::spawnPeriodic);

        metadata.register(typeWithPorts("vfx.block.spawn_distance", "spawn", "Spawn By Distance",
                List.of(),
                List.of(prop("rate", ValueType.FLOAT, Value.of(5f)), prop("speed", ValueType.FLOAT, Value.of(1f))),
                SPAWN_TAIL));
        blocks.register("vfx.block.spawn_distance", VfxBlocks::spawnDistance);

        // ==================== init（只处理传入批次） ====================

        metadata.register(type("vfx.block.init_position", "init", "Set Position (Shape)", SHAPE_PROPS));
        blocks.register("vfx.block.init_position", VfxBlocks::initPosition);

        metadata.register(typeWithPorts("vfx.block.init_velocity", "init", "Set Velocity",
                List.of(in("vx", "X", ValueType.FLOAT), in("vy", "Y", ValueType.FLOAT), in("vz", "Z", ValueType.FLOAT)),
                List.of(
                        prop("vx", ValueType.FLOAT, Value.of(0f)),
                        prop("vy", ValueType.FLOAT, Value.of(1f)),
                        prop("vz", ValueType.FLOAT, Value.of(0f)),
                        prop("random", ValueType.FLOAT, Value.of(0f)),
                        prop("param", ValueType.STRING, Value.string(""))
                )));
        blocks.register("vfx.block.init_velocity", VfxBlocks::initVelocity);

        metadata.register(typeWithPorts("vfx.block.init_color", "init", "Set Color",
                List.of(in("color", "Color", ValueType.COLOR)),
                List.of(prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.init_color", VfxBlocks::initColor);

        metadata.register(typeWithPorts("vfx.block.init_size", "init", "Set Size",
                List.of(in("size", "Size", ValueType.FLOAT)),
                List.of(prop("size", ValueType.FLOAT, Value.of(0.1f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.init_size", VfxBlocks::initSize);

        metadata.register(type("vfx.block.init_rotation", "init", "Set Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.init_rotation", VfxBlocks::initRotation);

        metadata.register(type("vfx.block.init_lifetime", "init", "Set Lifetime",
                List.of(prop("lifetime", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.init_lifetime", VfxBlocks::initLifetime);

        metadata.register(type("vfx.block.init_mass", "init", "Set Mass",
                List.of(prop("mass", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.init_mass", VfxBlocks::initMass);

        metadata.register(type("vfx.block.init_randomize", "init", "Randomize",
                List.of(
                        prop("pos", ValueType.FLOAT, Value.of(0.1f)),
                        prop("vel", ValueType.FLOAT, Value.of(0.1f)),
                        prop("size", ValueType.FLOAT, Value.of(0.1f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.1f))
                )));
        blocks.register("vfx.block.init_randomize", VfxBlocks::initRandomize);

        // ==================== update（全部存活粒子） ====================

        metadata.register(type("vfx.block.update_velocity", "update", "Integrate Velocity", List.of()));
        blocks.register("vfx.block.update_velocity", VfxBlocks::updateVelocity);

        metadata.register(typeWithPorts("vfx.block.update_gravity", "update", "Gravity",
                List.of(in("gravity", "Gravity", ValueType.FLOAT)),
                List.of(prop("gravity", ValueType.FLOAT, Value.of(-9.8f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.update_gravity", VfxBlocks::updateGravity);

        metadata.register(type("vfx.block.update_force", "update", "Constant Force",
                List.of(prop("fx", ValueType.FLOAT, Value.of(0f)), prop("fy", ValueType.FLOAT, Value.of(0f)),
                        prop("fz", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.update_force", VfxBlocks::updateForce);

        metadata.register(type("vfx.block.update_noise", "update", "Noise Force", NOISE));
        blocks.register("vfx.block.update_noise", VfxBlocks::updateNoise);

        metadata.register(type("vfx.block.update_turbulence", "update", "Turbulence", NOISE));
        blocks.register("vfx.block.update_turbulence", VfxBlocks::updateTurbulence);

        metadata.register(type("vfx.block.update_vortex", "update", "Vortex",
                List.of(prop("cx", ValueType.FLOAT, Value.of(0f)), prop("cz", ValueType.FLOAT, Value.of(0f)),
                        prop("strength", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.update_vortex", VfxBlocks::updateVortex);

        metadata.register(type("vfx.block.update_drag", "update", "Drag",
                List.of(prop("drag", ValueType.FLOAT, Value.of(0.1f)))));
        blocks.register("vfx.block.update_drag", VfxBlocks::updateDrag);

        metadata.register(type("vfx.block.update_damping", "update", "Damping",
                List.of(prop("damping", ValueType.FLOAT, Value.of(0.5f)))));
        blocks.register("vfx.block.update_damping", VfxBlocks::updateDamping);

        metadata.register(type("vfx.block.update_age", "update", "Age", List.of()));
        blocks.register("vfx.block.update_age", VfxBlocks::updateAge);

        metadata.register(type("vfx.block.update_fade", "update", "Fade", List.of()));
        blocks.register("vfx.block.update_fade", VfxBlocks::updateFade);

        // ==================== collision / bounds ====================

        metadata.register(type("vfx.block.collision_ground", "collision", "Ground Collision", BOUNCE_KILL));
        blocks.register("vfx.block.collision_ground", VfxBlocks::collisionGround);

        metadata.register(type("vfx.block.collision_plane", "collision", "Plane Collision",
                List.of(prop("height", ValueType.FLOAT, Value.of(0f))), BOUNCE_KILL));
        blocks.register("vfx.block.collision_plane", VfxBlocks::collisionPlane);

        metadata.register(type("vfx.block.collision_sphere", "collision", "Sphere Collision",
                List.of(prop("cx", ValueType.FLOAT, Value.of(0f)), prop("cy", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)), prop("radius", ValueType.FLOAT, Value.of(1f)),
                        prop("bounce", ValueType.FLOAT, Value.of(0.5f)))));
        blocks.register("vfx.block.collision_sphere", VfxBlocks::collisionSphere);

        metadata.register(type("vfx.block.bounds", "collision", "Bounds (Kill Outside)",
                List.of(prop("min_x", ValueType.FLOAT, Value.of(-10f)), prop("min_y", ValueType.FLOAT, Value.of(-10f)),
                        prop("min_z", ValueType.FLOAT, Value.of(-10f)), prop("max_x", ValueType.FLOAT, Value.of(10f)),
                        prop("max_y", ValueType.FLOAT, Value.of(10f)), prop("max_z", ValueType.FLOAT, Value.of(10f)))));
        blocks.register("vfx.block.bounds", VfxBlocks::bounds);

        metadata.register(type("vfx.block.kill", "collision", "Kill After Time",
                List.of(prop("time", ValueType.FLOAT, Value.of(5f)))));
        blocks.register("vfx.block.kill", VfxBlocks::kill);

        // ==================== over-life（曲线/渐变） ====================

        metadata.register(type("vfx.block.life_color", "over-life", "Color Over Lifetime",
                List.of(prop("gradient", ValueType.STRING, Value.string("")), prop("layer", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.life_color", VfxBlocks::lifeColor);

        metadata.register(type("vfx.block.life_alpha", "over-life", "Alpha Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_alpha", VfxBlocks::lifeAlpha);

        metadata.register(type("vfx.block.life_size", "over-life", "Size Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_size", VfxBlocks::lifeSize);

        metadata.register(type("vfx.block.life_velocity", "over-life", "Velocity Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_velocity", VfxBlocks::lifeVelocity);

        // ==================== orient ====================

        metadata.register(type("vfx.block.orient_face_camera", "orient", "Face Camera", List.of()));
        blocks.register("vfx.block.orient_face_camera", (n, p) -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) buf.setRotation(i, 0f);
        });

        metadata.register(type("vfx.block.orient_velocity", "orient", "Align To Velocity",
                List.of(prop("offset", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.orient_velocity", VfxBlocks::orientVelocity);

        metadata.register(type("vfx.block.orient_fixed", "orient", "Fixed Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.orient_fixed", VfxBlocks::orientFixed);

        metadata.register(type("vfx.block.orient_spin", "orient", "Spin",
                List.of(prop("speed", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.orient_spin", VfxBlocks::orientSpin);

        // ==================== output（仅提供 RenderSpec，不碰缓冲） ====================

        metadata.register(type("vfx.block.output_point", "output", "Output Points", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_point", (n, p) -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.block.output_quad", "output", "Output Quad", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_quad", (n, p) -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.block.output_quad_additive", "output", "Output Quad / Additive", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_quad_additive", (n, p) -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.block.output_quad_glow", "output", "Output Quad / Additive Glow", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_quad_glow", (n, p) -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.block.output_mesh", "output", "Output Mesh", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_mesh", (n, p) -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.block.output_line", "output", "Output Line / Trail", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_line", (n, p) -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        });

        metadata.register(type("vfx.block.output_ribbon", "output", "Output Ribbon", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_ribbon", (n, p) -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        });

        // ==================== arc（M22，ADR-026：路径驱动，CPU 约束 spine + GPU 锯齿/辉光，无线程） ====================

        metadata.register(type("vfx.block.arc_bolt", "spawn", "Arc Bolt",
                List.of(
                        prop("origin_x", ValueType.FLOAT, Value.of(0f)),
                        prop("origin_y", ValueType.FLOAT, Value.of(0f)),
                        prop("origin_z", ValueType.FLOAT, Value.of(0f)),
                        prop("from_x", ValueType.FLOAT, Value.of(0f)),
                        prop("from_y", ValueType.FLOAT, Value.of(0f)),
                        prop("from_z", ValueType.FLOAT, Value.of(0f)),
                        prop("to_x", ValueType.FLOAT, Value.of(0f)),
                        prop("to_y", ValueType.FLOAT, Value.of(2f)),
                        prop("to_z", ValueType.FLOAT, Value.of(0f)),
                        prop("density", ValueType.FLOAT, Value.of(1f)),
                        prop("probability", ValueType.FLOAT, Value.of(0.02f)),
                        prop("frequency", ValueType.FLOAT, Value.of(30f)),
                        prop("interval", ValueType.FLOAT, Value.of(0.5f)),
                        prop("width", ValueType.FLOAT, Value.of(0.01f)),
                        prop("segments", ValueType.INT, Value.of(12)),
                        prop("lifetime", ValueType.FLOAT, Value.of(1f)),
                        prop("color", ValueType.COLOR, Value.color(0.8f, 0.8f, 0.8f, 1f)),
                        prop("emission", ValueType.FLOAT, Value.of(1f)),
                        prop("branch_depth", ValueType.INT, Value.of(1)),
                        prop("branch_count", ValueType.INT, Value.of(2)),
                        prop("branch_angle", ValueType.FLOAT, Value.of(1.57f)),
                        prop("branch_length_scale", ValueType.FLOAT, Value.of(0.3f)),
                        prop("branch_width_scale", ValueType.FLOAT, Value.of(0.35f)),
                        prop("branch_brightness_scale", ValueType.FLOAT, Value.of(0.6f))
                )));
        blocks.register("vfx.block.arc_bolt", VfxBlocks::arcBolt);

        metadata.register(type("vfx.block.arc_orbit", "spawn", "Arc Orbit",
                List.of(
                        prop("origin_x", ValueType.FLOAT, Value.of(0f)),
                        prop("origin_y", ValueType.FLOAT, Value.of(0f)),
                        prop("origin_z", ValueType.FLOAT, Value.of(0f)),
                        prop("radius", ValueType.FLOAT, Value.of(1.5f)),
                        prop("speed", ValueType.FLOAT, Value.of(1f)),
                        prop("width", ValueType.FLOAT, Value.of(0.01f)),
                        prop("segments", ValueType.INT, Value.of(12)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0f)),
                        prop("color", ValueType.COLOR, Value.color(0.8f, 0.8f, 0.8f, 1f)),
                        prop("emission", ValueType.FLOAT, Value.of(1f)),
                        prop("height", ValueType.FLOAT, Value.of(2f)),
                        prop("branch_depth", ValueType.INT, Value.of(0)),
                        prop("branch_count", ValueType.INT, Value.of(2)),
                        prop("branch_angle", ValueType.FLOAT, Value.of(1.57f)),
                        prop("branch_length_scale", ValueType.FLOAT, Value.of(0.3f)),
                        prop("branch_width_scale", ValueType.FLOAT, Value.of(0.35f)),
                        prop("branch_brightness_scale", ValueType.FLOAT, Value.of(0.6f))
                )));
        blocks.register("vfx.block.arc_orbit", VfxBlocks::arcOrbit);

        metadata.register(type("vfx.block.arc_surface", "spawn", "Arc Surface",
                ARC_SURFACE_PROPS));
        blocks.register("vfx.block.arc_surface", VfxBlocks::arcSurface);

        metadata.register(type("vfx.block.arc_contact", "spawn", "Arc Contact",
                Stream.concat(ARC_SURFACE_PROPS.stream(), ARC_CONTACT_PROPS.stream()).toList()));
        blocks.register("vfx.block.arc_contact", VfxBlocks::arcContact);

        metadata.register(type("vfx.block.arc_spark", "spawn", "Arc Spark",
                ARC_SPARK_PROPS));
        blocks.register("vfx.block.arc_spark", VfxBlocks::arcSpark);

        // output_arc：OUTPUT_PROPERTIES（vertex/shader/blend/layer）+ ARC 观感参数（数据驱动，M22g）
        metadata.register(type("vfx.block.output_arc", "output", "Output Arc",
                Stream.concat(OUTPUT_PROPERTIES.stream(), ARC_OUTPUT_PROPERTIES.stream()).toList()));
        blocks.register("vfx.block.output_arc", (n, p) -> (buf, ctx) -> {
        });
    }

    // ==================== spawn 块 ====================

    private static SimNode spawnRate(VfxBlock block, PortValueSource ports) {
        var lifetime = propFloat(block, "lifetime", 1f);
        var size = propFloat(block, "size", 0.1f);
        var color = propColor(block, "color");
        var vx = propFloat(block, "vx", 0f);
        var vy = propFloat(block, "vy", 0f);
        var vz = propFloat(block, "vz", 0f);
        var layer = layerOf(block);
        var rate = propFloat(block, "rate", 10f);
        var shape = buildShape(block);
        float[] acc = {0f};
        return (buf, ctx) -> {
            var r = portFloat(ports, "rate", -1, buf, ctx, rate);
            acc[0] += r * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            if (n == 0) return;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < n; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                buf.setPosition(i, p[0], p[1], p[2]);
                buf.setVelocity(i, vx, vy, vz);
                buf.setSize(i, size);
                buf.setColor(i, color[0], color[1], color[2], color[3]);
                buf.setLifetime(i, lifetime);
                buf.setAge(i, 0f);
                buf.setLayer(i, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    private static SimNode spawnBurst(VfxBlock block, PortValueSource ports) {
        var count = propInt(block, "count", 10);
        var lifetime = propFloat(block, "lifetime", 1f);
        var size = propFloat(block, "size", 0.1f);
        var color = propColor(block, "color");
        var vx = propFloat(block, "vx", 0f);
        var vy = propFloat(block, "vy", 0f);
        var vz = propFloat(block, "vz", 0f);
        var layer = layerOf(block);
        var shape = buildShape(block);
        boolean[] fired = {false};
        return (buf, ctx) -> {
            if (fired[0]) return;
            fired[0] = true;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                buf.setPosition(i, p[0], p[1], p[2]);
                buf.setVelocity(i, vx, vy, vz);
                buf.setSize(i, size);
                buf.setColor(i, color[0], color[1], color[2], color[3]);
                buf.setLifetime(i, lifetime);
                buf.setAge(i, 0f);
                buf.setLayer(i, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    private static SimNode spawnPeriodic(VfxBlock block, PortValueSource ports) {
        var count = propInt(block, "count", 5);
        var interval = propFloat(block, "interval", 1f);
        var lifetime = propFloat(block, "lifetime", 1f);
        var size = propFloat(block, "size", 0.1f);
        var color = propColor(block, "color");
        var vx = propFloat(block, "vx", 0f);
        var vy = propFloat(block, "vy", 0f);
        var vz = propFloat(block, "vz", 0f);
        var layer = layerOf(block);
        var shape = buildShape(block);
        float[] acc = {0f};
        return (buf, ctx) -> {
            acc[0] += ctx.dt();
            if (acc[0] < interval) return;
            acc[0] = 0f;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                buf.setPosition(i, p[0], p[1], p[2]);
                buf.setVelocity(i, vx, vy, vz);
                buf.setSize(i, size);
                buf.setColor(i, color[0], color[1], color[2], color[3]);
                buf.setLifetime(i, lifetime);
                buf.setAge(i, 0f);
                buf.setLayer(i, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    private static SimNode spawnDistance(VfxBlock block, PortValueSource ports) {
        var rate = propFloat(block, "rate", 5f);
        var speed = propFloat(block, "speed", 1f);
        var lifetime = propFloat(block, "lifetime", 1f);
        var size = propFloat(block, "size", 0.1f);
        var color = propColor(block, "color");
        var vx = propFloat(block, "vx", 0f);
        var vy = propFloat(block, "vy", 0f);
        var vz = propFloat(block, "vz", 0f);
        var layer = layerOf(block);
        var shape = buildShape(block);
        float[] acc = {0f};
        return (buf, ctx) -> {
            acc[0] += rate * speed * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            if (n == 0) return;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < n; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                buf.setPosition(i, p[0], p[1], p[2]);
                buf.setVelocity(i, vx, vy, vz);
                buf.setSize(i, size);
                buf.setColor(i, color[0], color[1], color[2], color[3]);
                buf.setLifetime(i, lifetime);
                buf.setAge(i, 0f);
                buf.setLayer(i, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    // ==================== init 块（只处理传入批次） ====================

    private static SimNode initPosition(VfxBlock block, PortValueSource ports) {
        var shape = buildShape(block);
        return (buf, ctx) -> {
            var p = new float[3];
            ctx.forEachIncoming(i -> {
                shape.sample(ctx.random(), p);
                buf.setPosition(i, p[0], p[1], p[2]);
            });
        };
    }

    private static SimNode initVelocity(VfxBlock block, PortValueSource ports) {
        var vx = propFloat(block, "vx", 0f);
        var vy = propFloat(block, "vy", 1f);
        var vz = propFloat(block, "vz", 0f);
        var random = propFloat(block, "random", 0f);
        var param = propString(block, "param", "");
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            var rvx = portFloat(ports, "vx", i, buf, ctx, vx);
            var rvy = portFloat(ports, "vy", i, buf, ctx, vy);
            var rvz = portFloat(ports, "vz", i, buf, ctx, vz);
            if (!param.isEmpty()) {
                rvx = ctx.paramVec3(param, 0, rvx);
                rvy = ctx.paramVec3(param, 1, rvy);
                rvz = ctx.paramVec3(param, 2, rvz);
            }
            var rx = random * (ctx.random().nextFloat() * 2f - 1f);
            var ry = random * (ctx.random().nextFloat() * 2f - 1f);
            var rz = random * (ctx.random().nextFloat() * 2f - 1f);
            buf.setVelocity(i, rvx + rx, rvy + ry, rvz + rz);
        });
    }

    private static SimNode initColor(VfxBlock block, PortValueSource ports) {
        var color = propColor(block, "color");
        var param = propString(block, "param", "");
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            var v = ports.eval("color", i, buf, ctx);
            float r = color[0], g = color[1], b = color[2], a = color[3];
            if (v != null && v.type() == ValueType.COLOR) {
                var c = v.asColor();
                r = c.x;
                g = c.y;
                b = c.z;
                a = c.w;
            } else if (!param.isEmpty()) {
                r = ctx.paramColor(param, 0, r);
                g = ctx.paramColor(param, 1, g);
                b = ctx.paramColor(param, 2, b);
                a = ctx.paramColor(param, 3, a);
            }
            buf.setColor(i, r, g, b, a);
        });
    }

    private static SimNode initSize(VfxBlock block, PortValueSource ports) {
        var size = propFloat(block, "size", 0.1f);
        var param = propString(block, "param", "");
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            var s = portFloat(ports, "size", i, buf, ctx, size);
            if (!param.isEmpty()) s = ctx.paramFloat(param, s);
            buf.setSize(i, s);
        });
    }

    private static SimNode initRotation(VfxBlock block, PortValueSource ports) {
        var rotation = propFloat(block, "rotation", 0f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> buf.setRotation(i, rotation));
    }

    private static SimNode initLifetime(VfxBlock block, PortValueSource ports) {
        var lifetime = propFloat(block, "lifetime", 1f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> buf.setLifetime(i, lifetime));
    }

    private static SimNode initMass(VfxBlock block, PortValueSource ports) {
        var mass = propFloat(block, "mass", 1f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> buf.setMass(i, mass));
    }

    private static SimNode initRandomize(VfxBlock block, PortValueSource ports) {
        var posAmp = propFloat(block, "pos", 0.1f);
        var velAmp = propFloat(block, "vel", 0.1f);
        var sizeAmp = propFloat(block, "size", 0.1f);
        var lifeAmp = propFloat(block, "lifetime", 0.1f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            buf.setPosition(i,
                    buf.positionX(i) + jitter(ctx, posAmp),
                    buf.positionY(i) + jitter(ctx, posAmp),
                    buf.positionZ(i) + jitter(ctx, posAmp));
            buf.setVelocity(i,
                    buf.velocityX(i) * (1f + jitter(ctx, velAmp)),
                    buf.velocityY(i) * (1f + jitter(ctx, velAmp)),
                    buf.velocityZ(i) * (1f + jitter(ctx, velAmp)));
            buf.setSizeScaled(i, buf.size(i) * (1f + jitter(ctx, sizeAmp)));
            buf.setLifetime(i, buf.lifetime(i) * (1f + jitter(ctx, lifeAmp)));
        });
    }

    // ==================== update 块 ====================

    private static SimNode updateVelocity(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                buf.setPosition(i,
                        buf.positionX(i) + buf.velocityX(i) * dt,
                        buf.positionY(i) + buf.velocityY(i) * dt,
                        buf.positionZ(i) + buf.velocityZ(i) * dt);
            }
        };
    }

    private static SimNode updateGravity(VfxBlock block, PortValueSource ports) {
        var g = propFloat(block, "gravity", -9.8f);
        var param = propString(block, "param", "");
        return (buf, ctx) -> {
            var rg = param.isEmpty() ? g : ctx.paramFloat(param, g);
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i, buf.velocityX(i), buf.velocityY(i) + rg * dt, buf.velocityZ(i));
            }
        };
    }

    private static SimNode updateForce(VfxBlock block, PortValueSource ports) {
        var fx = propFloat(block, "fx", 0f);
        var fy = propFloat(block, "fy", 0f);
        var fz = propFloat(block, "fz", 0f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                var m = buf.mass(i);
                var inv = m > 0f ? 1f / m : 1f;
                buf.setVelocity(i,
                        buf.velocityX(i) + fx * inv * dt,
                        buf.velocityY(i) + fy * inv * dt,
                        buf.velocityZ(i) + fz * inv * dt);
            }
        };
    }

    private static SimNode updateNoise(VfxBlock block, PortValueSource ports) {
        var amp = propFloat(block, "amplitude", 1f);
        var freq = propFloat(block, "frequency", 1f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            var t = ctx.time() * freq;
            for (var i = 0; i < buf.count(); i++) {
                var nx = hash(buf.positionX(i) * freq + 1.7f, buf.positionY(i) * freq, t) * 2f - 1f;
                var ny = hash(buf.positionY(i) * freq, t + 3.1f, buf.positionZ(i) * freq) * 2f - 1f;
                var nz = hash(t + 5.3f, buf.positionZ(i) * freq, buf.positionX(i) * freq) * 2f - 1f;
                buf.setVelocity(i,
                        buf.velocityX(i) + nx * amp * dt,
                        buf.velocityY(i) + ny * amp * dt,
                        buf.velocityZ(i) + nz * amp * dt);
            }
        };
    }

    private static SimNode updateTurbulence(VfxBlock block, PortValueSource ports) {
        var amp = propFloat(block, "amplitude", 1f);
        var freq = propFloat(block, "frequency", 1f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            var t = ctx.time() * freq;
            for (var i = 0; i < buf.count(); i++) {
                var n = hash(buf.positionX(i) * freq, buf.positionY(i) * freq, t) * 2f - 1f;
                var vx = buf.velocityX(i);
                var vy = buf.velocityY(i);
                var vz = buf.velocityZ(i);
                var len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len < 1e-5f) continue;
                vx /= len;
                vy /= len;
                vz /= len;
                var tx = vz;
                var tz = -vx;
                var tl = (float) Math.sqrt(tx * tx + tz * tz);
                if (tl < 1e-5f) {
                    tx = 1f;
                    tz = 0f;
                } else {
                    tx /= tl;
                    tz /= tl;
                }
                buf.setVelocity(i,
                        buf.velocityX(i) + tx * n * amp * dt,
                        buf.velocityY(i) + vy * n * amp * dt,
                        buf.velocityZ(i) + tz * n * amp * dt);
            }
        };
    }

    private static SimNode updateVortex(VfxBlock block, PortValueSource ports) {
        var cx = propFloat(block, "cx", 0f);
        var cz = propFloat(block, "cz", 0f);
        var strength = propFloat(block, "strength", 1f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                var dx = buf.positionX(i) - cx;
                var dz = buf.positionZ(i) - cz;
                var r2 = dx * dx + dz * dz;
                if (r2 < 1e-6f) continue;
                var inv = 1f / (float) Math.sqrt(r2);
                buf.setVelocity(i,
                        buf.velocityX(i) - dz * inv * strength * dt,
                        buf.velocityY(i),
                        buf.velocityZ(i) + dx * inv * strength * dt);
            }
        };
    }

    private static SimNode updateDrag(VfxBlock block, PortValueSource ports) {
        var drag = propFloat(block, "drag", 0.1f);
        return (buf, ctx) -> {
            var factor = (float) Math.exp(-drag * ctx.dt());
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i, buf.velocityX(i) * factor, buf.velocityY(i) * factor, buf.velocityZ(i) * factor);
            }
        };
    }

    private static SimNode updateDamping(VfxBlock block, PortValueSource ports) {
        var damping = propFloat(block, "damping", 0.5f);
        return (buf, ctx) -> {
            var factor = Math.max(0f, 1f - damping * ctx.dt());
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i, buf.velocityX(i) * factor, buf.velocityY(i) * factor, buf.velocityZ(i) * factor);
            }
        };
    }

    private static SimNode updateAge(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            var dt = ctx.dt();
            var i = 0;
            while (i < buf.count()) {
                var age = buf.age(i) + dt;
                buf.setAge(i, age);
                if (age >= buf.lifetime(i)) {
                    buf.kill(i);
                } else {
                    i++;
                }
            }
        };
    }

    private static SimNode updateFade(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var t = Math.min(1f, buf.age(i) / buf.lifetime(i));
                buf.setAlpha(i, buf.startAlpha(i) * (1f - t));
                buf.setSizeScaled(i, buf.startSize(i) * (1f - t));
            }
        };
    }

    // ==================== collision / bounds ====================

    private static SimNode collisionGround(VfxBlock block, PortValueSource ports) {
        var bounce = propFloat(block, "bounce", 0.5f);
        var kill = propBool(block, "kill", false);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                if (buf.positionY(i) <= 0f && buf.velocityY(i) < 0f) {
                    if (kill) {
                        buf.kill(i);
                        continue;
                    }
                    buf.setPosition(i, buf.positionX(i), 0f, buf.positionZ(i));
                    buf.setVelocity(i, buf.velocityX(i), -buf.velocityY(i) * bounce, buf.velocityZ(i));
                }
                i++;
            }
        };
    }

    private static SimNode collisionPlane(VfxBlock block, PortValueSource ports) {
        var height = propFloat(block, "height", 0f);
        var bounce = propFloat(block, "bounce", 0.5f);
        var kill = propBool(block, "kill", false);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                if (buf.positionY(i) <= height && buf.velocityY(i) < 0f) {
                    if (kill) {
                        buf.kill(i);
                        continue;
                    }
                    buf.setPosition(i, buf.positionX(i), height, buf.positionZ(i));
                    buf.setVelocity(i, buf.velocityX(i), -buf.velocityY(i) * bounce, buf.velocityZ(i));
                }
                i++;
            }
        };
    }

    private static SimNode collisionSphere(VfxBlock block, PortValueSource ports) {
        var cx = propFloat(block, "cx", 0f);
        var cy = propFloat(block, "cy", 0f);
        var cz = propFloat(block, "cz", 0f);
        var radius = propFloat(block, "radius", 1f);
        var bounce = propFloat(block, "bounce", 0.5f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var dx = buf.positionX(i) - cx;
                var dy = buf.positionY(i) - cy;
                var dz = buf.positionZ(i) - cz;
                var dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist >= radius || dist < 1e-6f) continue;
                var nx = dx / dist;
                var ny = dy / dist;
                var nz = dz / dist;
                buf.setPosition(i, cx + nx * radius, cy + ny * radius, cz + nz * radius);
                var dot = buf.velocityX(i) * nx + buf.velocityY(i) * ny + buf.velocityZ(i) * nz;
                if (dot < 0f) {
                    buf.setVelocity(i,
                            buf.velocityX(i) - (1f + bounce) * dot * nx,
                            buf.velocityY(i) - (1f + bounce) * dot * ny,
                            buf.velocityZ(i) - (1f + bounce) * dot * nz);
                }
            }
        };
    }

    private static SimNode bounds(VfxBlock block, PortValueSource ports) {
        var minX = propFloat(block, "min_x", -10f);
        var minY = propFloat(block, "min_y", -10f);
        var minZ = propFloat(block, "min_z", -10f);
        var maxX = propFloat(block, "max_x", 10f);
        var maxY = propFloat(block, "max_y", 10f);
        var maxZ = propFloat(block, "max_z", 10f);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                var x = buf.positionX(i);
                var y = buf.positionY(i);
                var z = buf.positionZ(i);
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                    buf.kill(i);
                } else {
                    i++;
                }
            }
        };
    }

    private static SimNode kill(VfxBlock block, PortValueSource ports) {
        var time = propFloat(block, "time", 5f);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                if (buf.age(i) >= time) {
                    buf.kill(i);
                } else {
                    i++;
                }
            }
        };
    }

    // ==================== over-life ====================

    private static SimNode lifeColor(VfxBlock block, PortValueSource ports) {
        var gradientId = propString(block, "gradient", "");
        var layerFilter = layerFilter(block);
        return (buf, ctx) -> {
            var gradient = ctx.gradient(gradientId);
            if (gradient == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                var c = GradientSampler.sample(gradient, lifeT(buf, i));
                buf.setColorRgb(i, c.x, c.y, c.z);
            }
        };
    }

    private static SimNode lifeAlpha(VfxBlock block, PortValueSource ports) {
        var curveId = propString(block, "curve", "");
        var layerFilter = layerFilter(block);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                buf.setAlpha(i, buf.startAlpha(i) * CurveSampler.sample(curve, lifeT(buf, i)));
            }
        };
    }

    private static SimNode lifeSize(VfxBlock block, PortValueSource ports) {
        var curveId = propString(block, "curve", "");
        var layerFilter = layerFilter(block);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                buf.setSizeScaled(i, buf.startSize(i) * CurveSampler.sample(curve, lifeT(buf, i)));
            }
        };
    }

    private static SimNode lifeVelocity(VfxBlock block, PortValueSource ports) {
        var curveId = propString(block, "curve", "");
        var layerFilter = layerFilter(block);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                var s = CurveSampler.sample(curve, lifeT(buf, i));
                buf.setVelocity(i,
                        buf.velocityX(i) * s,
                        buf.velocityY(i) * s,
                        buf.velocityZ(i) * s);
            }
        };
    }

    // ==================== orient ====================

    private static SimNode orientVelocity(VfxBlock block, PortValueSource ports) {
        var offset = propFloat(block, "offset", 0f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var angle = (float) Math.atan2(buf.velocityZ(i), buf.velocityX(i));
                buf.setRotation(i, angle + offset);
            }
        };
    }

    private static SimNode orientFixed(VfxBlock block, PortValueSource ports) {
        var rotation = propFloat(block, "rotation", 0f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, rotation);
            }
        };
    }

    private static SimNode orientSpin(VfxBlock block, PortValueSource ports) {
        var speed = propFloat(block, "speed", 1f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, buf.rotation(i) + speed * ctx.dt());
            }
        };
    }

    // ==================== arc（M22，路径驱动，CPU spine + GPU 观感，无线程） ====================

    /**
     * 两点电弧（Blender 式：from→to + 表面法线起拱 + 递归分支 + 噪声动画）。
     */
    private static SimNode arcBolt(VfxBlock block, PortValueSource ports) {
        var ox = propFloat(block, "origin_x", 0f);
        var oy = propFloat(block, "origin_y", 0f);
        var oz = propFloat(block, "origin_z", 0f);
        var fromX = propFloat(block, "from_x", ox);
        var fromY = propFloat(block, "from_y", oy);
        var fromZ = propFloat(block, "from_z", oz);
        var toX = propFloat(block, "to_x", ox);
        var toY = propFloat(block, "to_y", oy + 2f);
        var toZ = propFloat(block, "to_z", oz);
        var probability = propFloat(block, "probability", 0.5f);
        var width = propFloat(block, "width", 0.01f);
        var segments = propInt(block, "segments", 12);
        var lifetime = propFloat(block, "lifetime", 1f);
        var color = propColor(block, "color");
        var emission = propFloat(block, "emission", 1f);
        var branchDepth = propInt(block, "branch_depth", 1);
        var branchCount = propInt(block, "branch_count", 2);
        var branchAngle = propFloat(block, "branch_angle", 1.57f);
        var branchLengthScale = propFloat(block, "branch_length_scale", 0.3f);
        var branchWidthScale = propFloat(block, "branch_width_scale", 0.35f);
        var branchBrightnessScale = propFloat(block, "branch_brightness_scale", 0.6f);
        // 主弧法线：from→to 连线方向（起拱方向，贴表面）
        float vx = toX - fromX, vy = toY - fromY, vz = toZ - fromZ;
        var vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float nx, ny, nz;
        if (vlen < 1e-6f) {
            nx = 0;
            ny = 1;
            nz = 0;
        } else {
            nx = vx / vlen;
            ny = vy / vlen;
            nz = vz / vlen;
        }
        long[] seed = {0L};
        // 每 N 秒生成一条电弧（低频，避免每帧生成导致几十上百条累积）；interval=0 则每帧概率生成
        var interval = propFloat(block, "interval", 0f);
        float[] accumulator = {0f};
        // 断续出现（复刻 Blender 随机点云阵列 Delete Geometry）：按概率随机跳过，产生零星断档
        return (buf, ctx) -> {
            if (interval > 0f) {
                accumulator[0] += ctx.dt();
                if (accumulator[0] < interval) return;
                accumulator[0] = 0f;
            }
            seed[0]++;
            if (ctx.random().nextFloat() > probability) return;
            var arc = ctx.arcs().add();
            CurveGenerator.generateFromTo(
                    arc, fromX, fromY, fromZ, toX, toY, toZ,
                    nx, ny, nz,
                    width, segments,
                    color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                    lifetime, seed[0],
                    branchDepth, branchCount, branchAngle,
                    branchLengthScale, branchWidthScale, branchBrightnessScale);
        };
    }

    /**
     * 环绕电弧。
     */
    private static SimNode arcOrbit(VfxBlock block, PortValueSource ports) {
        var ox = propFloat(block, "origin_x", 0f);
        var oy = propFloat(block, "origin_y", 0f);
        var oz = propFloat(block, "origin_z", 0f);
        var radius = propFloat(block, "radius", 1.5f);
        var width = propFloat(block, "width", 0.01f);
        var segments = propInt(block, "segments", 12);
        var lifetime = propFloat(block, "lifetime", 0f);
        var color = propColor(block, "color");
        var emission = propFloat(block, "emission", 1f);
        var height = propFloat(block, "height", 2f);
        var branchDepth = propInt(block, "branch_depth", 0);
        var branchCount = propInt(block, "branch_count", 2);
        var branchAngle = propFloat(block, "branch_angle", 1.57f);
        var branchLengthScale = propFloat(block, "branch_length_scale", 0.3f);
        var branchWidthScale = propFloat(block, "branch_width_scale", 0.35f);
        var branchBrightnessScale = propFloat(block, "branch_brightness_scale", 0.6f);
        long[] seed = {0L};
        float[] angle = {0f};
        var speed = propFloat(block, "speed", 1f);
        return (buf, ctx) -> {
            seed[0]++;
            angle[0] += ctx.dt() * speed;
            var x = ox + (float) Math.cos(angle[0]) * radius;
            var z = oz + (float) Math.sin(angle[0]) * radius;
            var arc = ctx.arcs().add();
            CurveGenerator.generate(
                    arc, x, oy, z, 0, 1, 0,
                    width, segments,
                    color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                    lifetime, seed[0],
                    branchDepth, branchCount, branchAngle,
                    branchLengthScale, branchWidthScale, branchBrightnessScale, height);
        };
    }

    /**
     * 表面电弧（M29，Blender「闪电附着」主流水线）：表面布点 + per-point 短弧 + 断续时序 + 端点吸附。
     */
    private static SimNode arcSurface(VfxBlock block, PortValueSource ports) {
        var ox = propFloat(block, "origin_x", 0f);
        var oy = propFloat(block, "origin_y", 0f);
        var oz = propFloat(block, "origin_z", 0f);
        var mesh = propString(block, "mesh", "builtin:plane");
        var density = propFloat(block, "density", 3.8f);
        var probability = propFloat(block, "probability", 0.5f);
        var frequency = propFloat(block, "frequency", 1f);
        var framePeriod = propInt(block, "frame_period", 3);
        var fps = propFloat(block, "fps", 30f);
        var height = propFloat(block, "height", 1f);
        var curve = propFloat(block, "curve", 0.78f);
        var width = propFloat(block, "width", 0.01f);
        var segments = propInt(block, "segments", 12);
        var lifetime = propFloat(block, "lifetime", 1f);
        var color = propColor(block, "color");
        var emission = propFloat(block, "emission", 1f);
        var noiseStrength = propFloat(block, "noise_strength", 0.5f);
        var driftSpeed = propFloat(block, "drift_speed", 1.5f);
        var base = MeshAssets.resolve(mesh);
        // 表面网格平移 origin（布点 + 端点吸附都在同一位移后的表面，保持一致性）
        var surface = base == null ? null : offsetTriangles(base, ox, oy, oz);
        var distributor = surface == null ? null : new SurfaceDistributor(surface);
        long[] seed = {0L};
        long[] lastGateFrame = {Long.MIN_VALUE};
        return (buf, ctx) -> {
            if (distributor == null || surface == null) return;
            // 帧周期断续时序（M29b-01，复刻 Blender Compare(Frame MOD N) EQUAL 0）：
            // frequency<=0 时每帧 spawn（兼容旧资产/测试）；否则只在 frame % frame_period == 0
            // 的帧 spawn 一批，其余帧跳过——避免每帧全密度 spawn 导致弧数爆炸（稳态 ~450 → <30）。
            var frame = (long) (ctx.time() * fps);
            if (frequency > 0f) {
                if (frame % framePeriod != 0) return;
                if (frame == lastGateFrame[0]) return;
            }
            lastGateFrame[0] = frame;
            seed[0]++;
            // 表面布点 + 随机删减（Blender 随机点云阵列子组）
            var samples = distributor.distribute(density, probability, ctx.time(), frequency, seed[0]);
            for (var s : samples) {
                var arc = ctx.arcs().add();
                CurveGenerator.generateSurfaceArc(
                        arc,
                        s.x(), s.y(), s.z(),
                        s.nx(), s.ny(), s.nz(),
                        height, curve, width, segments,
                        color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                        lifetime, seed[0] * 31 + (long) (s.x() * 1000f));
                arc.setSurface(surface);
                arc.setNoiseStrength(noiseStrength);
                arc.setDriftSpeed(driftSpeed);
            }
        };
    }

    /**
     * 接触闪电（M30，复刻 Blender 主组第二套系统）：源面布点 + 到接触对象距离剔除 + 直线弧末端吸附接触面。
     */
    private static SimNode arcContact(VfxBlock block, PortValueSource ports) {
        var ox = propFloat(block, "origin_x", 0f);
        var oy = propFloat(block, "origin_y", 0f);
        var oz = propFloat(block, "origin_z", 0f);
        var mesh = propString(block, "mesh", "builtin:plane");
        var contactMesh = propString(block, "contact_mesh", "builtin:sphere");
        var contactRange = propFloat(block, "contact_range", 4.1f);
        var cox = propFloat(block, "contact_origin_x", 0f);
        var coy = propFloat(block, "contact_origin_y", 4.3f);
        var coz = propFloat(block, "contact_origin_z", 0f);
        var density = propFloat(block, "density", 3.8f);
        var probability = propFloat(block, "probability", 0.5f);
        var frequency = propFloat(block, "frequency", 1f);
        var framePeriod = propInt(block, "frame_period", 3);
        var fps = propFloat(block, "fps", 30f);
        var height = propFloat(block, "height", 1f);
        var curve = propFloat(block, "curve", 0.78f);
        var width = propFloat(block, "width", 0.01f);
        var segments = propInt(block, "segments", 12);
        var lifetime = propFloat(block, "lifetime", 1f);
        var color = propColor(block, "color");
        var emission = propFloat(block, "emission", 1f);
        var noiseStrength = propFloat(block, "noise_strength", 0.5f);
        var driftSpeed = propFloat(block, "drift_speed", 1.5f);
        var base = MeshAssets.resolve(mesh);
        var contactBase = MeshAssets.resolve(contactMesh);
        var surface = base == null ? null : offsetTriangles(base, ox, oy, oz);
        var contact = contactBase == null ? null : offsetTriangles(contactBase, cox, coy, coz);
        var distributor = surface == null ? null : new SurfaceDistributor(surface);
        long[] seed = {0L};
        long[] lastGateFrame = {Long.MIN_VALUE};
        return (buf, ctx) -> {
            if (distributor == null || surface == null || contact == null) return;
            // 帧周期断续时序（M29b-01，同 arc_surface）：frequency<=0 每帧，否则按帧周期门控
            var frame = (long) (ctx.time() * fps);
            if (frequency > 0f) {
                if (frame % framePeriod != 0) return;
                if (frame == lastGateFrame[0]) return;
            }
            lastGateFrame[0] = frame;
            seed[0]++;
            var samples = distributor.distribute(density, probability, ctx.time(), frequency, seed[0]);
            for (var s : samples) {
                float px = s.x(), py = s.y(), pz = s.z();
                // 到接触对象最近距离 → 超出接触范围剔除（Blender Compare GREATER_THAN → Delete Geometry）
                var dist = MeshDistance.nearestDistance(contact, px, py, pz);
                if (dist > contactRange) continue;
                // 接触表面最近点（Blender Sample Nearest Surface.002.Value → Set Position.004 末端）
                var nearest = MeshDistance.nearestPoint(contact, px, py, pz);
                var arc = ctx.arcs().add();
                CurveGenerator.generateContactArc(
                        arc, px, py, pz,
                        nearest[0], nearest[1], nearest[2],
                        width, segments,
                        color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                        lifetime, seed[0] * 97 + (long) (s.x() * 1000f));
                // 末端吸附到接触对象表面（Blender Set Position.004 = Sample Nearest Surface.002 + Endpoint）
                arc.setSurface(contact);
                arc.setNoiseStrength(noiseStrength);
                arc.setDriftSpeed(driftSpeed);
            }
        };
    }

    /**
     * 粒子火花（M30，复刻 Blender 主组第三套系统）：弧→点 + 概率删减 + 溅射方向+重力 + 迷你管对齐速度。
     */
    private static SimNode arcSpark(VfxBlock block, PortValueSource ports) {
        var probability = propFloat(block, "probability", 0.5f);
        var maxSparks = propInt(block, "max_sparks", 3);
        var splashSpeed = propFloat(block, "splash_speed", 1.3f);
        var gravity = propFloat(block, "gravity", -0.9f);
        var lifetime = propFloat(block, "lifetime", 0.5f);
        var scale = propFloat(block, "scale", 1.4f);
        var radius = propFloat(block, "radius", 0.005f);
        var color = propColor(block, "color");
        var emission = propFloat(block, "emission", 1f);
        long[] seed = {0L};
        return (buf, ctx) -> {
            seed[0]++;
            var random = ctx.random();
            var arcs = ctx.arcs();
            var n = arcs.count();
            for (var a = 0; a < n; a++) {
                var arc = arcs.arc(a);
                // 只从本帧新增的带表面弧（arc_surface/arc_contact 源弧）取点；火花弧（无表面、
                // fresh=false 于下帧）不再派生火花，消除指数放大（M29b-02）
                if (!arc.fresh() || !arc.hasSurface()) continue;
                var size = arc.size();
                if (size < 2) continue;
                // 每弧火花数上限（防本帧新增多条弧时火花总量过大）
                var spawned = 0;
                // 弧→点（Blender Curve to Points，Count=10 → 按控制点取点）
                var pointCount = Math.min(size, 10);
                for (var i = 0; i < pointCount; i++) {
                    if (spawned >= maxSparks) break;
                    // 概率删减（Blender Delete Geometry + Random Value：保留 = 粒子密度）
                    if (random.nextFloat() > probability) continue;
                    var idx = i * (size - 1) / Math.max(1, pointCount - 1);
                    float px = arc.x(idx), py = arc.y(idx), pz = arc.z(idx);
                    // 溅射方向：绕表面法线随机（Blender 矢量选择：约束矢量=法线，角度 π/2）
                    var ref = surfaceTangent(arc, idx);
                    var dir = SurfaceDistributor.tangentDirection(ref[0], ref[1], ref[2],
                            (float) Math.PI / 3f, random);
                    // 初始速度：溅射速度 × Random(0.3~1.2)（Blender Math.007 = Random.006 × 溅射速度）
                    var speed = splashSpeed * (0.3f + 0.9f * random.nextFloat());
                    var vx = dir[0] * speed;
                    var vy = dir[1] * speed;
                    var vz = dir[2] * speed;
                    // 迷你电弧：2 点短弧，方向 = 速度（复刻 Align Rotation to Vector(速度)）
                    // 长度 = 实例 Scale（Random.005 0.01~0.03 × 粒子缩放）× 生命系数曲线
                    var lifeScale = BlenderArcCurves.sample(BlenderArcCurves.PARTICLE_LIFE, 0f);
                    var len = (0.01f + 0.02f * random.nextFloat()) * scale * lifeScale;
                    var vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    var ux = vlen < 1e-6f ? 0f : vx / vlen;
                    var uy = vlen < 1e-6f ? 1f : vy / vlen;
                    var uz = vlen < 1e-6f ? 0f : vz / vlen;
                    var spark = arcs.add();
                    var ex = px + ux * len;
                    var ey = py + uy * len;
                    var ez = pz + uz * len;
                    spark.addPoint(px, py, pz, radius * (0.2f + 0.4f * random.nextFloat()), 0, 0);
                    spark.addPoint(ex, ey, ez, radius * 0.3f, 0, 0);
                    spark.setColor(color[0] * emission, color[1] * emission, color[2] * emission, color[3]);
                    spark.setLifetime(lifetime);
                    spark.setSeed(seed[0] * 131 + a * 17L + i);
                    spark.setSparkVelocity(vx, vy, vz);
                    spawned++;
                }
            }
        };
    }

    /**
     * 估算弧线第 i 控制点的表面切向（相邻点差，供火花方向参考）。
     */
    private static float[] surfaceTangent(ArcCurve arc, int i) {
        var prev = Math.max(0, i - 1);
        var next = Math.min(arc.size() - 1, i + 1);
        var tx = arc.x(next) - arc.x(prev);
        var ty = arc.y(next) - arc.y(prev);
        var tz = arc.z(next) - arc.z(prev);
        var len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1e-6f) return new float[]{0, 1, 0};
        return new float[]{tx / len, ty / len, tz / len};
    }

    /**
     * 平移三角形网格（每 3 个浮点 +x、+y、+z），返回新数组。
     */
    private static float[] offsetTriangles(float[] tris, float ox, float oy, float oz) {
        if (tris.length == 0) return tris;
        var out = tris.clone();
        for (var i = 0; i + 2 < out.length; i += 3) {
            out[i] += ox;
            out[i + 1] += oy;
            out[i + 2] += oz;
        }
        return out;
    }

    // ==================== 辅助 ====================

    /**
     * 端口求值：有数据流绑定返回算子值（逐粒子），否则返回属性默认。
     */
    private static float portFloat(PortValueSource ports, String portId, int particleIndex,
                                   ParticleBuffer buffer, SimContext ctx, float fallback) {
        var v = ports.eval(portId, particleIndex, buffer, ctx);
        if (v == null || v.type() != ValueType.FLOAT) return fallback;
        return v.asFloat();
    }

    private static byte layerOf(VfxBlock block) {
        return ParticleBuffer.layerByte(propString(block, "layer", "fire"));
    }

    /**
     * over-life layer 过滤：""=全部，fire=0，smoke=1。
     */
    private static byte layerFilter(VfxBlock block) {
        return ParticleBuffer.layerFilter(propString(block, "layer", ""));
    }

    private static float lifeT(ParticleBuffer buf, int i) {
        var l = buf.lifetime(i);
        if (l <= 0f) return 1f;
        return Math.min(1f, buf.age(i) / l);
    }

    private static float jitter(SimContext ctx, float amp) {
        return amp * (ctx.random().nextFloat() * 2f - 1f);
    }

    private static float hash(float x, float y, float z) {
        var s = (float) Math.sin(x * 127.1f + y * 311.7f + z * 74.7f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    private static EmitterShape buildShape(VfxBlock block) {
        var ox = propFloat(block, "origin_x", 0f);
        var oy = propFloat(block, "origin_y", 0f);
        var oz = propFloat(block, "origin_z", 0f);
        var scale = propFloat(block, "mesh_scale", 1f);
        return switch (propString(block, "shape", "point")) {
            case "sphere" -> new SphereShape(ox, oy, oz, propFloat(block, "radius", 1f));
            case "box" -> new BoxShape(ox, oy, oz,
                    propFloat(block, "half_x", 1f), propFloat(block, "half_y", 1f), propFloat(block, "half_z", 1f));
            case "cone" ->
                    new ConeShape(ox, oy, oz, propFloat(block, "radius", 1f), propFloat(block, "cone_height", 2f));
            case "cylinder" ->
                    new CylinderShape(ox, oy, oz, propFloat(block, "radius", 1f), propFloat(block, "cone_height", 2f));
            case "torus" ->
                    new TorusShape(ox, oy, oz, propFloat(block, "radius", 1f), propFloat(block, "half_x", 0.25f));
            case "circle_edge" -> new CircleEdgeShape(ox, oy, oz, propFloat(block, "radius", 1f));
            case "disc" -> new DiscShape(ox, oy, oz, propFloat(block, "radius", 1f));
            case "mesh" -> meshShape(block, ox, oy, oz, scale);
            default -> new PointShape(ox, oy, oz);
        };
    }

    private static EmitterShape meshShape(VfxBlock block, float ox, float oy, float oz, float scale) {
        var id = propString(block, "mesh", "");
        var triangles = id.isEmpty() ? null : MeshAssets.triangles(id);
        return triangles == null
                ? MeshShape.unitCube(ox, oy, oz, scale)
                : new MeshShape(ox, oy, oz, scale, triangles);
    }

    private static float propFloat(VfxBlock block, String id, float def) {
        var v = block.properties().get(id);
        return v == null ? def : Float.parseFloat(v);
    }

    private static int propInt(VfxBlock block, String id, int def) {
        var v = block.properties().get(id);
        return v == null ? def : Integer.parseInt(v);
    }

    private static boolean propBool(VfxBlock block, String id, boolean def) {
        var v = block.properties().get(id);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static String propString(VfxBlock block, String id, String def) {
        return block.properties().getOrDefault(id, def);
    }

    private static float[] propColor(VfxBlock block, String id) {
        var csv = block.properties().get(id);
        if (csv == null) return new float[]{1f, 1f, 1f, 1f};
        var parts = csv.split(",");
        return new float[]{
                parts.length > 0 ? Float.parseFloat(parts[0].trim()) : 1f,
                parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1f,
                parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1f,
                parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1f
        };
    }

    private static NodeType type(String id, String category, String name, List<PropertySpec> props) {
        return new NodeType(id, category, name, List.of(), props);
    }

    private static NodeType type(String id, String category, String name, List<PropertySpec> head, List<PropertySpec> tail) {
        var props = new ArrayList<PropertySpec>(head.size() + tail.size());
        props.addAll(head);
        props.addAll(tail);
        return new NodeType(id, category, name, List.of(), List.copyOf(props));
    }

    private static NodeType typeWithPorts(String id, String category, String name, List<PortSpec> ports,
                                          List<PropertySpec> props) {
        return new NodeType(id, category, name, List.copyOf(ports), List.copyOf(props));
    }

    private static NodeType typeWithPorts(String id, String category, String name, List<PortSpec> ports,
                                          List<PropertySpec> head, List<PropertySpec> tail) {
        var props = new ArrayList<PropertySpec>(head.size() + tail.size());
        props.addAll(head);
        props.addAll(tail);
        return new NodeType(id, category, name, List.copyOf(ports), List.copyOf(props));
    }

    private static PortSpec in(
            String id, String name, ValueType type) {
        return new PortSpec(
                id, name, PortDirection.INPUT, type,
                switch (type) {
                    case FLOAT, TIME, INT -> Value.of(0f);
                    case COLOR -> Value.color(1f, 1f, 1f, 1f);
                    default -> Value.of(0f);
                });
    }

    private static PropertySpec prop(String id, ValueType type, Value def) {
        return new PropertySpec(id, id, type, def, Optional.empty());
    }
}
