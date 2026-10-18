package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * VFX 节点目录：注册节点元数据（核心 NodeRegistry）与模拟工厂（VfxNodeRegistry）。
 *
 * <p>M13 补全 spawn/init/update/collision/over-life/orient/output/shape 全谱系。
 * 相位约定：spawn 节点把 {@code ctx.spawnStart} 置为本帧首个新粒子索引；init 节点只处理
 * {@code [spawnStart, count)}；update/over-life/orient 处理全部粒子。over-life 引用黑板
 * CURVE/GRADIENT 参数（经 {@code SimContext.curve/gradient} + {@code CurveSampler}）。</p>
 */
public final class VfxNodes {
    private VfxNodes() {
    }

    // ==================== 共享属性块（消除节点注册的重复穷举） ====================

    /**
     * 粒子基础属性：lifetime/size/color/初速度（spawn 系共用）。
     */
    private static final List<PropertySpec> PARTICLE_BASIC_PROPS = List.of(
            prop("lifetime", ValueType.FLOAT, Value.of(1f)),
            prop("size", ValueType.FLOAT, Value.of(0.1f)),
            prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)),
            prop("vx", ValueType.FLOAT, Value.of(0f)),
            prop("vy", ValueType.FLOAT, Value.of(0f)),
            prop("vz", ValueType.FLOAT, Value.of(0f))
    );

    /**
     * 发射形状属性：shape/origin/尺寸（spawn 尾块与 init_position 共用）。
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
     * spawn_rate/burst/periodic 共享尾块：lifetime..layer。
     */
    private static final List<PropertySpec> SPAWN_TAIL_PROPS = props(
            PARTICLE_BASIC_PROPS,
            SHAPE_PROPS,
            List.of(prop("layer", ValueType.STRING, Value.string("fire")))
    );

    /**
     * update_noise/turbulence 共用：amplitude/frequency。
     */
    private static final List<PropertySpec> NOISE_PROPS = List.of(
            prop("amplitude", ValueType.FLOAT, Value.of(1f)),
            prop("frequency", ValueType.FLOAT, Value.of(1f))
    );

    /**
     * over-life curve 系（alpha/size/velocity）共用：curve/layer。
     */
    private static final List<PropertySpec> CURVE_LAYER_PROPS = List.of(
            prop("curve", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    /**
     * collision_ground/plane 共用尾块：bounce/kill。
     */
    private static final List<PropertySpec> BOUNCE_KILL_PROPS = List.of(
            prop("bounce", ValueType.FLOAT, Value.of(0.5f)),
            prop("kill", ValueType.BOOL, Value.of(false))
    );

    /**
     * output 系节点共享属性默认（数据驱动，不按节点类型枚举）：
     * vertex/shader/blend 全部由图数据显式指定，此处仅中性兜底——**不写死具体 shader id**；
     * layer 过滤该输出负责渲染的粒子层（空串=全部，分层外观用多输出节点表达）。
     */
    private static final List<PropertySpec> OUTPUT_PROPERTIES = List.of(
            prop("vertex", ValueType.STRING, Value.string("")),
            prop("shader", ValueType.STRING, Value.string("")),
            prop("blend", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    private static final List<PropertySpec> SCREEN_FLASH_OUTPUT_PROPERTIES = List.of(
            prop("vertex", ValueType.STRING, Value.string("")),
            prop("shader", ValueType.STRING, Value.string("")),
            prop("blend", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string("")),
            prop("cap", ValueType.FLOAT, Value.of(1f))
    );

    public static void registerAll(NodeRegistry metadata, VfxNodeRegistry vfx) {
        // ==================== spawn ====================

        metadata.register(type("vfx.spawn_rate", "spawn", "Spawn Rate",
                props(
                        List.of(
                                prop("rate", ValueType.FLOAT, Value.of(10f)),
                                prop("param", ValueType.STRING, Value.string(""))
                        ),
                        SPAWN_TAIL_PROPS
                )));
        vfx.register("vfx.spawn_rate", SpawnNodes::rate);

        metadata.register(type("vfx.spawn_burst", "spawn", "Spawn Burst",
                props(List.of(prop("count", ValueType.INT, Value.of(10))), SPAWN_TAIL_PROPS)));
        vfx.register("vfx.spawn_burst", SpawnNodes::burst);

        metadata.register(type("vfx.spawn_periodic", "spawn", "Spawn Periodic Burst",
                props(
                        List.of(
                                prop("count", ValueType.INT, Value.of(5)),
                                prop("interval", ValueType.FLOAT, Value.of(1f))
                        ),
                        SPAWN_TAIL_PROPS
                )));
        vfx.register("vfx.spawn_periodic", SpawnNodes::periodic);

        metadata.register(type("vfx.spawn_distance", "spawn", "Spawn By Distance",
                props(
                        List.of(
                                prop("rate", ValueType.FLOAT, Value.of(5f)),
                                prop("speed", ValueType.FLOAT, Value.of(1f))
                        ),
                        PARTICLE_BASIC_PROPS,
                        List.of(prop("layer", ValueType.STRING, Value.string("fire")))
                )));
        vfx.register("vfx.spawn_distance", SpawnNodes::distance);

        // ==================== init（只处理本帧新粒子） ====================

        metadata.register(type("vfx.init_position", "init", "Set Position (Shape)", SHAPE_PROPS));
        vfx.register("vfx.init_position", InitNodes::position);

        metadata.register(type("vfx.init_velocity", "init", "Set Velocity",
                List.of(
                        prop("vx", ValueType.FLOAT, Value.of(0f)),
                        prop("vy", ValueType.FLOAT, Value.of(1f)),
                        prop("vz", ValueType.FLOAT, Value.of(0f)),
                        prop("random", ValueType.FLOAT, Value.of(0f)),
                        prop("param", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.init_velocity", InitNodes::velocity);

        metadata.register(type("vfx.init_color", "init", "Set Color",
                List.of(prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)), prop("param", ValueType.STRING, Value.string("")))));
        vfx.register("vfx.init_color", InitNodes::color);

        metadata.register(type("vfx.init_size", "init", "Set Size",
                List.of(prop("size", ValueType.FLOAT, Value.of(0.1f)), prop("param", ValueType.STRING, Value.string("")))));
        vfx.register("vfx.init_size", InitNodes::size);

        metadata.register(type("vfx.init_rotation", "init", "Set Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        vfx.register("vfx.init_rotation", InitNodes::rotation);

        metadata.register(type("vfx.init_lifetime", "init", "Set Lifetime",
                List.of(prop("lifetime", ValueType.FLOAT, Value.of(1f)))));
        vfx.register("vfx.init_lifetime", InitNodes::lifetime);

        metadata.register(type("vfx.init_mass", "init", "Set Mass",
                List.of(prop("mass", ValueType.FLOAT, Value.of(1f)))));
        vfx.register("vfx.init_mass", InitNodes::mass);

        metadata.register(type("vfx.init_randomize", "init", "Randomize",
                List.of(
                        prop("pos", ValueType.FLOAT, Value.of(0.1f)),
                        prop("vel", ValueType.FLOAT, Value.of(0.1f)),
                        prop("size", ValueType.FLOAT, Value.of(0.1f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.1f))
                )));
        vfx.register("vfx.init_randomize", InitNodes::randomize);

        // ==================== update（处理全部粒子） ====================

        metadata.register(type("vfx.update_velocity", "update", "Integrate Velocity", List.of()));
        vfx.register("vfx.update_velocity", UpdateNodes::velocity);

        metadata.register(type("vfx.update_gravity", "update", "Gravity",
                List.of(prop("gravity", ValueType.FLOAT, Value.of(-9.8f)), prop("param", ValueType.STRING, Value.string("")))));
        vfx.register("vfx.update_gravity", UpdateNodes::gravity);

        metadata.register(type("vfx.update_force", "update", "Constant Force",
                List.of(
                        prop("fx", ValueType.FLOAT, Value.of(0f)),
                        prop("fy", ValueType.FLOAT, Value.of(0f)),
                        prop("fz", ValueType.FLOAT, Value.of(0f))
                )));
        vfx.register("vfx.update_force", UpdateNodes::force);

        metadata.register(type("vfx.update_noise", "update", "Noise Force", NOISE_PROPS));
        vfx.register("vfx.update_noise", UpdateNodes::noise);

        metadata.register(type("vfx.update_turbulence", "update", "Turbulence", NOISE_PROPS));
        vfx.register("vfx.update_turbulence", UpdateNodes::turbulence);

        metadata.register(type("vfx.update_vortex", "update", "Vortex",
                List.of(
                        prop("cx", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)),
                        prop("strength", ValueType.FLOAT, Value.of(1f))
                )));
        vfx.register("vfx.update_vortex", UpdateNodes::vortex);

        metadata.register(type("vfx.update_drag", "update", "Drag",
                List.of(prop("drag", ValueType.FLOAT, Value.of(0.1f)))));
        vfx.register("vfx.update_drag", UpdateNodes::drag);

        metadata.register(type("vfx.update_damping", "update", "Damping",
                List.of(prop("damping", ValueType.FLOAT, Value.of(0.5f)))));
        vfx.register("vfx.update_damping", UpdateNodes::damping);

        metadata.register(type("vfx.update_age", "update", "Age", List.of()));
        vfx.register("vfx.update_age", UpdateNodes::age);

        metadata.register(type("vfx.update_fade", "update", "Fade", List.of()));
        vfx.register("vfx.update_fade", UpdateNodes::fade);

        // ==================== collision / bounds ====================

        metadata.register(type("vfx.collision_ground", "collision", "Ground Collision", BOUNCE_KILL_PROPS));
        vfx.register("vfx.collision_ground", CollisionNodes::ground);

        metadata.register(type("vfx.collision_plane", "collision", "Plane Collision",
                props(List.of(prop("height", ValueType.FLOAT, Value.of(0f))), BOUNCE_KILL_PROPS)));
        vfx.register("vfx.collision_plane", CollisionNodes::plane);

        metadata.register(type("vfx.collision_sphere", "collision", "Sphere Collision",
                List.of(
                        prop("cx", ValueType.FLOAT, Value.of(0f)),
                        prop("cy", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)),
                        prop("radius", ValueType.FLOAT, Value.of(1f)),
                        prop("bounce", ValueType.FLOAT, Value.of(0.5f))
                )));
        vfx.register("vfx.collision_sphere", CollisionNodes::sphere);

        metadata.register(type("vfx.bounds", "collision", "Bounds (Kill Outside)",
                List.of(
                        prop("min_x", ValueType.FLOAT, Value.of(-10f)),
                        prop("min_y", ValueType.FLOAT, Value.of(-10f)),
                        prop("min_z", ValueType.FLOAT, Value.of(-10f)),
                        prop("max_x", ValueType.FLOAT, Value.of(10f)),
                        prop("max_y", ValueType.FLOAT, Value.of(10f)),
                        prop("max_z", ValueType.FLOAT, Value.of(10f))
                )));
        vfx.register("vfx.bounds", CollisionNodes::bounds);

        metadata.register(type("vfx.kill", "collision", "Kill After Time",
                List.of(prop("time", ValueType.FLOAT, Value.of(5f)))));
        vfx.register("vfx.kill", CollisionNodes::kill);

        // ==================== over-life（曲线/渐变，M13-05） ====================

        metadata.register(type("vfx.life_color", "over-life", "Color Over Lifetime",
                List.of(
                        prop("gradient", ValueType.STRING, Value.string("")),
                        prop("layer", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.life_color", LifeNodes::color);

        metadata.register(type("vfx.life_alpha", "over-life", "Alpha Over Lifetime", CURVE_LAYER_PROPS));
        vfx.register("vfx.life_alpha", LifeNodes::alpha);

        metadata.register(type("vfx.life_size", "over-life", "Size Over Lifetime", CURVE_LAYER_PROPS));
        vfx.register("vfx.life_size", LifeNodes::size);

        metadata.register(type("vfx.life_velocity", "over-life", "Velocity Over Lifetime", CURVE_LAYER_PROPS));
        vfx.register("vfx.life_velocity", LifeNodes::velocity);

        // ==================== orient（M13-06） ====================

        metadata.register(type("vfx.orient_face_camera", "orient", "Face Camera", List.of()));
        vfx.register("vfx.orient_face_camera", OrientNodes::faceCamera);

        metadata.register(type("vfx.orient_velocity", "orient", "Align To Velocity",
                List.of(prop("offset", ValueType.FLOAT, Value.of(0f)))));
        vfx.register("vfx.orient_velocity", OrientNodes::velocity);

        metadata.register(type("vfx.orient_fixed", "orient", "Fixed Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        vfx.register("vfx.orient_fixed", OrientNodes::fixed);

        metadata.register(type("vfx.orient_spin", "orient", "Spin",
                List.of(prop("speed", ValueType.FLOAT, Value.of(1f)))));
        vfx.register("vfx.orient_spin", OrientNodes::spin);

        metadata.register(type("vfx.output_point", "output", "Output Points", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_point", OutputNodes::noop);

        metadata.register(type("vfx.output_quad", "output", "Output Quad / Billboard", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_quad", OutputNodes::noop);

        metadata.register(type("vfx.output_quad_additive", "output", "Output Quad / Additive", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_quad_additive", OutputNodes::noop);

        metadata.register(type("vfx.output_quad_glow", "output", "Output Quad / Additive Glow", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_quad_glow", OutputNodes::noop);

        metadata.register(type("vfx.output_mesh", "output", "Output Mesh", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_mesh", OutputNodes::noop);

        metadata.register(type("vfx.output_line", "output", "Output Line / Trail", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_line", OutputNodes::trail);

        metadata.register(type("vfx.output_ribbon", "output", "Output Ribbon", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_ribbon", OutputNodes::trail);

        metadata.register(type("vfx.output_screen_flash", "output",
                "Output Screen Flash", SCREEN_FLASH_OUTPUT_PROPERTIES));
        vfx.register("vfx.output_screen_flash", OutputNodes::noop);

        metadata.register(type("vfx.param_float", "param", "Float Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("value", ValueType.FLOAT, Value.of(0f))
                )));
        vfx.register("vfx.param_float", ParamNodes::floatParam);

        metadata.register(type("vfx.param_vec3", "param", "Vec3 Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("x", ValueType.FLOAT, Value.of(0f)),
                        prop("y", ValueType.FLOAT, Value.of(0f)),
                        prop("z", ValueType.FLOAT, Value.of(0f))
                )));
        vfx.register("vfx.param_vec3", ParamNodes::vec3);

        metadata.register(type("vfx.param_color", "param", "Color Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("r", ValueType.FLOAT, Value.of(1f)),
                        prop("g", ValueType.FLOAT, Value.of(1f)),
                        prop("b", ValueType.FLOAT, Value.of(1f)),
                        prop("a", ValueType.FLOAT, Value.of(1f))
                )));
        vfx.register("vfx.param_color", ParamNodes::color);

        metadata.register(type("vfx.param_curve", "param", "Curve Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("curve", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.param_curve", ParamNodes::curve);

        metadata.register(type("vfx.param_gradient", "param", "Gradient Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("gradient", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.param_gradient", ParamNodes::gradient);
    }

    private static NodeType type(String id, String category, String name, List<PropertySpec> props) {
        return new NodeType(id, category, name, List.of(), props);
    }

    private static PropertySpec prop(String id, ValueType type, Value def) {
        return new PropertySpec(id, id, type, def, Optional.empty());
    }

    /**
     * 拼接属性块（节点注册复用共享块，避免逐节点重复穷举）。
     */
    @SafeVarargs
    private static List<PropertySpec> props(List<PropertySpec>... parts) {
        return Arrays.stream(parts).flatMap(List::stream).toList();
    }
}
