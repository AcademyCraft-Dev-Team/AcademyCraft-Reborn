package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.GradientSampler;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.shape.*;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;

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
        vfx.register("vfx.spawn_rate", VfxNodes::spawnRate);

        metadata.register(type("vfx.spawn_burst", "spawn", "Spawn Burst",
                props(List.of(prop("count", ValueType.INT, Value.of(10))), SPAWN_TAIL_PROPS)));
        vfx.register("vfx.spawn_burst", VfxNodes::spawnBurst);

        metadata.register(type("vfx.spawn_periodic", "spawn", "Spawn Periodic Burst",
                props(
                        List.of(
                                prop("count", ValueType.INT, Value.of(5)),
                                prop("interval", ValueType.FLOAT, Value.of(1f))
                        ),
                        SPAWN_TAIL_PROPS
                )));
        vfx.register("vfx.spawn_periodic", VfxNodes::spawnPeriodic);

        metadata.register(type("vfx.spawn_distance", "spawn", "Spawn By Distance",
                props(
                        List.of(
                                prop("rate", ValueType.FLOAT, Value.of(5f)),
                                prop("speed", ValueType.FLOAT, Value.of(1f))
                        ),
                        PARTICLE_BASIC_PROPS,
                        List.of(prop("layer", ValueType.STRING, Value.string("fire")))
                )));
        vfx.register("vfx.spawn_distance", VfxNodes::spawnDistance);

        // ==================== init（只处理本帧新粒子） ====================

        metadata.register(type("vfx.init_position", "init", "Set Position (Shape)", SHAPE_PROPS));
        vfx.register("vfx.init_position", node -> {
            var shape = buildShape(node);
            return (buf, ctx) -> {
                var p = new float[3];
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    shape.sample(ctx.random(), p);
                    buf.setPosition(i, p[0], p[1], p[2]);
                }
            };
        });

        metadata.register(type("vfx.init_velocity", "init", "Set Velocity",
                List.of(
                        prop("vx", ValueType.FLOAT, Value.of(0f)),
                        prop("vy", ValueType.FLOAT, Value.of(1f)),
                        prop("vz", ValueType.FLOAT, Value.of(0f)),
                        prop("random", ValueType.FLOAT, Value.of(0f)),
                        prop("param", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.init_velocity", node -> {
            var vx = propFloat(node, "vx", 0f);
            var vy = propFloat(node, "vy", 1f);
            var vz = propFloat(node, "vz", 0f);
            var random = propFloat(node, "random", 0f);
            var param = propString(node, "param", "");
            return (buf, ctx) -> {
                float rvx = vx, rvy = vy, rvz = vz;
                if (!param.isEmpty()) {
                    rvx = ctx.paramVec3(param, 0, vx);
                    rvy = ctx.paramVec3(param, 1, vy);
                    rvz = ctx.paramVec3(param, 2, vz);
                }
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    var rx = random * (ctx.random().nextFloat() * 2f - 1f);
                    var ry = random * (ctx.random().nextFloat() * 2f - 1f);
                    var rz = random * (ctx.random().nextFloat() * 2f - 1f);
                    buf.setVelocity(i, rvx + rx, rvy + ry, rvz + rz);
                }
            };
        });

        metadata.register(type("vfx.init_color", "init", "Set Color",
                List.of(prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)), prop("param", ValueType.STRING, Value.string("")))));
        vfx.register("vfx.init_color", node -> {
            var color = propColor(node, "color");
            var param = propString(node, "param", "");
            return (buf, ctx) -> {
                float r = color[0], g = color[1], b = color[2], a = color[3];
                if (!param.isEmpty()) {
                    r = ctx.paramColor(param, 0, color[0]);
                    g = ctx.paramColor(param, 1, color[1]);
                    b = ctx.paramColor(param, 2, color[2]);
                    a = ctx.paramColor(param, 3, color[3]);
                }
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    buf.setColor(i, r, g, b, a);
                }
            };
        });

        metadata.register(type("vfx.init_size", "init", "Set Size",
                List.of(prop("size", ValueType.FLOAT, Value.of(0.1f)), prop("param", ValueType.STRING, Value.string("")))));
        vfx.register("vfx.init_size", node -> {
            var size = propFloat(node, "size", 0.1f);
            var param = propString(node, "param", "");
            return (buf, ctx) -> {
                var rs = param.isEmpty() ? size : ctx.paramFloat(param, size);
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    buf.setSize(i, rs);
                }
            };
        });

        metadata.register(type("vfx.init_rotation", "init", "Set Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        vfx.register("vfx.init_rotation", node -> {
            var rotation = propFloat(node, "rotation", 0f);
            return (buf, ctx) -> {
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    buf.setRotation(i, rotation);
                }
            };
        });

        metadata.register(type("vfx.init_lifetime", "init", "Set Lifetime",
                List.of(prop("lifetime", ValueType.FLOAT, Value.of(1f)))));
        vfx.register("vfx.init_lifetime", node -> {
            var lifetime = propFloat(node, "lifetime", 1f);
            return (buf, ctx) -> {
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    buf.setLifetime(i, lifetime);
                }
            };
        });

        metadata.register(type("vfx.init_mass", "init", "Set Mass",
                List.of(prop("mass", ValueType.FLOAT, Value.of(1f)))));
        vfx.register("vfx.init_mass", node -> {
            var mass = propFloat(node, "mass", 1f);
            return (buf, ctx) -> {
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
                    buf.setMass(i, mass);
                }
            };
        });

        metadata.register(type("vfx.init_randomize", "init", "Randomize",
                List.of(
                        prop("pos", ValueType.FLOAT, Value.of(0.1f)),
                        prop("vel", ValueType.FLOAT, Value.of(0.1f)),
                        prop("size", ValueType.FLOAT, Value.of(0.1f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.1f))
                )));
        vfx.register("vfx.init_randomize", node -> {
            var posAmp = propFloat(node, "pos", 0.1f);
            var velAmp = propFloat(node, "vel", 0.1f);
            var sizeAmp = propFloat(node, "size", 0.1f);
            var lifeAmp = propFloat(node, "lifetime", 0.1f);
            return (buf, ctx) -> {
                for (var i = ctx.spawnStart; i < buf.count(); i++) {
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
                }
            };
        });

        // ==================== update（处理全部粒子） ====================

        metadata.register(type("vfx.update_velocity", "update", "Integrate Velocity", List.of()));
        vfx.register("vfx.update_velocity", n -> (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                buf.setPosition(i,
                        buf.positionX(i) + buf.velocityX(i) * dt,
                        buf.positionY(i) + buf.velocityY(i) * dt,
                        buf.positionZ(i) + buf.velocityZ(i) * dt);
            }
        });

        metadata.register(type("vfx.update_gravity", "update", "Gravity",
                List.of(prop("gravity", ValueType.FLOAT, Value.of(-9.8f)), prop("param", ValueType.STRING, Value.string("")))));
        vfx.register("vfx.update_gravity", node -> {
            var g = propFloat(node, "gravity", -9.8f);
            var param = propString(node, "param", "");
            return (buf, ctx) -> {
                var rg = param.isEmpty() ? g : ctx.paramFloat(param, g);
                var dt = ctx.dt();
                for (var i = 0; i < buf.count(); i++) {
                    buf.setVelocity(i, buf.velocityX(i), buf.velocityY(i) + rg * dt, buf.velocityZ(i));
                }
            };
        });

        metadata.register(type("vfx.update_force", "update", "Constant Force",
                List.of(
                        prop("fx", ValueType.FLOAT, Value.of(0f)),
                        prop("fy", ValueType.FLOAT, Value.of(0f)),
                        prop("fz", ValueType.FLOAT, Value.of(0f))
                )));
        vfx.register("vfx.update_force", node -> {
            var fx = propFloat(node, "fx", 0f);
            var fy = propFloat(node, "fy", 0f);
            var fz = propFloat(node, "fz", 0f);
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
        });

        metadata.register(type("vfx.update_noise", "update", "Noise Force", NOISE_PROPS));
        vfx.register("vfx.update_noise", node -> {
            var amp = propFloat(node, "amplitude", 1f);
            var freq = propFloat(node, "frequency", 1f);
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
        });

        metadata.register(type("vfx.update_turbulence", "update", "Turbulence", NOISE_PROPS));
        vfx.register("vfx.update_turbulence", node -> {
            var amp = propFloat(node, "amplitude", 1f);
            var freq = propFloat(node, "frequency", 1f);
            return (buf, ctx) -> {
                var dt = ctx.dt();
                var t = ctx.time() * freq;
                for (var i = 0; i < buf.count(); i++) {
                    var n = hash(buf.positionX(i) * freq, buf.positionY(i) * freq, t) * 2f - 1f;
                    // 垂直于粒子速度方向的旋转扰动
                    var vx = buf.velocityX(i);
                    var vy = buf.velocityY(i);
                    var vz = buf.velocityZ(i);
                    var len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if (len < 1e-5f) continue;
                    vx /= len;
                    vy /= len;
                    vz /= len;
                    var tx = vz;
                    var ty = 0f;
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
                            buf.velocityY(i) + ty * n * amp * dt,
                            buf.velocityZ(i) + tz * n * amp * dt);
                }
            };
        });

        metadata.register(type("vfx.update_vortex", "update", "Vortex",
                List.of(
                        prop("cx", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)),
                        prop("strength", ValueType.FLOAT, Value.of(1f))
                )));
        vfx.register("vfx.update_vortex", node -> {
            var cx = propFloat(node, "cx", 0f);
            var cz = propFloat(node, "cz", 0f);
            var strength = propFloat(node, "strength", 1f);
            return (buf, ctx) -> {
                var dt = ctx.dt();
                for (var i = 0; i < buf.count(); i++) {
                    var dx = buf.positionX(i) - cx;
                    var dz = buf.positionZ(i) - cz;
                    var r2 = dx * dx + dz * dz;
                    if (r2 < 1e-6f) continue;
                    var inv = 1f / (float) Math.sqrt(r2);
                    var tx = -dz * inv * strength * dt;
                    var tz = dx * inv * strength * dt;
                    buf.setVelocity(i, buf.velocityX(i) + tx, buf.velocityY(i), buf.velocityZ(i) + tz);
                }
            };
        });

        metadata.register(type("vfx.update_drag", "update", "Drag",
                List.of(prop("drag", ValueType.FLOAT, Value.of(0.1f)))));
        vfx.register("vfx.update_drag", node -> {
            var drag = propFloat(node, "drag", 0.1f);
            return (buf, ctx) -> {
                var factor = (float) Math.exp(-drag * ctx.dt());
                for (var i = 0; i < buf.count(); i++) {
                    buf.setVelocity(i,
                            buf.velocityX(i) * factor,
                            buf.velocityY(i) * factor,
                            buf.velocityZ(i) * factor);
                }
            };
        });

        metadata.register(type("vfx.update_damping", "update", "Damping",
                List.of(prop("damping", ValueType.FLOAT, Value.of(0.5f)))));
        vfx.register("vfx.update_damping", node -> {
            var damping = propFloat(node, "damping", 0.5f);
            return (buf, ctx) -> {
                var factor = Math.max(0f, 1f - damping * ctx.dt());
                for (var i = 0; i < buf.count(); i++) {
                    buf.setVelocity(i,
                            buf.velocityX(i) * factor,
                            buf.velocityY(i) * factor,
                            buf.velocityZ(i) * factor);
                }
            };
        });

        metadata.register(type("vfx.update_age", "update", "Age", List.of()));
        vfx.register("vfx.update_age", n -> (buf, ctx) -> {
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
        });

        metadata.register(type("vfx.update_fade", "update", "Fade", List.of()));
        vfx.register("vfx.update_fade", n -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var t = Math.min(1f, buf.age(i) / buf.lifetime(i));
                buf.setAlpha(i, buf.startAlpha(i) * (1f - t));
                buf.setSizeScaled(i, buf.startSize(i) * (1f - t));
            }
        });

        // ==================== collision / bounds ====================

        metadata.register(type("vfx.collision_ground", "collision", "Ground Collision", BOUNCE_KILL_PROPS));
        vfx.register("vfx.collision_ground", node -> {
            var bounce = propFloat(node, "bounce", 0.5f);
            var kill = propBool(node, "kill", false);
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
        });

        metadata.register(type("vfx.collision_plane", "collision", "Plane Collision",
                props(List.of(prop("height", ValueType.FLOAT, Value.of(0f))), BOUNCE_KILL_PROPS)));
        vfx.register("vfx.collision_plane", node -> {
            var height = propFloat(node, "height", 0f);
            var bounce = propFloat(node, "bounce", 0.5f);
            var kill = propBool(node, "kill", false);
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
        });

        metadata.register(type("vfx.collision_sphere", "collision", "Sphere Collision",
                List.of(
                        prop("cx", ValueType.FLOAT, Value.of(0f)),
                        prop("cy", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)),
                        prop("radius", ValueType.FLOAT, Value.of(1f)),
                        prop("bounce", ValueType.FLOAT, Value.of(0.5f))
                )));
        vfx.register("vfx.collision_sphere", node -> {
            var cx = propFloat(node, "cx", 0f);
            var cy = propFloat(node, "cy", 0f);
            var cz = propFloat(node, "cz", 0f);
            var radius = propFloat(node, "radius", 1f);
            var bounce = propFloat(node, "bounce", 0.5f);
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
        });

        metadata.register(type("vfx.bounds", "collision", "Bounds (Kill Outside)",
                List.of(
                        prop("min_x", ValueType.FLOAT, Value.of(-10f)),
                        prop("min_y", ValueType.FLOAT, Value.of(-10f)),
                        prop("min_z", ValueType.FLOAT, Value.of(-10f)),
                        prop("max_x", ValueType.FLOAT, Value.of(10f)),
                        prop("max_y", ValueType.FLOAT, Value.of(10f)),
                        prop("max_z", ValueType.FLOAT, Value.of(10f))
                )));
        vfx.register("vfx.bounds", node -> {
            var minX = propFloat(node, "min_x", -10f);
            var minY = propFloat(node, "min_y", -10f);
            var minZ = propFloat(node, "min_z", -10f);
            var maxX = propFloat(node, "max_x", 10f);
            var maxY = propFloat(node, "max_y", 10f);
            var maxZ = propFloat(node, "max_z", 10f);
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
        });

        metadata.register(type("vfx.kill", "collision", "Kill After Time",
                List.of(prop("time", ValueType.FLOAT, Value.of(5f)))));
        vfx.register("vfx.kill", node -> {
            var time = propFloat(node, "time", 5f);
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
        });

        // ==================== over-life（曲线/渐变，M13-05） ====================

        metadata.register(type("vfx.life_color", "over-life", "Color Over Lifetime",
                List.of(
                        prop("gradient", ValueType.STRING, Value.string("")),
                        prop("layer", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.life_color", node -> {
            var gradientId = propString(node, "gradient", "");
            var layerFilter = layerFilter(node);
            return (buf, ctx) -> {
                var gradient = ctx.gradient(gradientId);
                if (gradient == null) return;
                for (var i = 0; i < buf.count(); i++) {
                    if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                    var t = lifeT(buf, i);
                    var c = GradientSampler.sample(gradient, t);
                    buf.setColor(i, c.x, c.y, c.z, buf.alpha(i));
                }
            };
        });

        metadata.register(type("vfx.life_alpha", "over-life", "Alpha Over Lifetime", CURVE_LAYER_PROPS));
        vfx.register("vfx.life_alpha", node -> {
            var curveId = propString(node, "curve", "");
            var layerFilter = layerFilter(node);
            return (buf, ctx) -> {
                var curve = ctx.curve(curveId);
                if (curve == null) return;
                for (var i = 0; i < buf.count(); i++) {
                    if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                    buf.setAlpha(i, buf.startAlpha(i) * CurveSampler.sample(curve, lifeT(buf, i)));
                }
            };
        });

        metadata.register(type("vfx.life_size", "over-life", "Size Over Lifetime", CURVE_LAYER_PROPS));
        vfx.register("vfx.life_size", node -> {
            var curveId = propString(node, "curve", "");
            var layerFilter = layerFilter(node);
            return (buf, ctx) -> {
                var curve = ctx.curve(curveId);
                if (curve == null) return;
                for (var i = 0; i < buf.count(); i++) {
                    if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                    buf.setSizeScaled(i, buf.startSize(i) * CurveSampler.sample(curve, lifeT(buf, i)));
                }
            };
        });

        metadata.register(type("vfx.life_velocity", "over-life", "Velocity Over Lifetime", CURVE_LAYER_PROPS));
        vfx.register("vfx.life_velocity", node -> {
            var curveId = propString(node, "curve", "");
            var layerFilter = layerFilter(node);
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
        });

        // ==================== orient（M13-06） ====================

        metadata.register(type("vfx.orient_face_camera", "orient", "Face Camera", List.of()));
        vfx.register("vfx.orient_face_camera", n -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, 0f);
            }
        });

        metadata.register(type("vfx.orient_velocity", "orient", "Align To Velocity",
                List.of(prop("offset", ValueType.FLOAT, Value.of(0f)))));
        vfx.register("vfx.orient_velocity", node -> {
            var offset = propFloat(node, "offset", 0f);
            return (buf, ctx) -> {
                for (var i = 0; i < buf.count(); i++) {
                    var angle = (float) Math.atan2(buf.velocityZ(i), buf.velocityX(i));
                    buf.setRotation(i, angle + offset);
                }
            };
        });

        metadata.register(type("vfx.orient_fixed", "orient", "Fixed Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        vfx.register("vfx.orient_fixed", node -> {
            var rotation = propFloat(node, "rotation", 0f);
            return (buf, ctx) -> {
                for (var i = 0; i < buf.count(); i++) {
                    buf.setRotation(i, rotation);
                }
            };
        });

        metadata.register(type("vfx.orient_spin", "orient", "Spin",
                List.of(prop("speed", ValueType.FLOAT, Value.of(1f)))));
        vfx.register("vfx.orient_spin", node -> {
            var speed = propFloat(node, "speed", 1f);
            return (buf, ctx) -> {
                for (var i = 0; i < buf.count(); i++) {
                    buf.setRotation(i, buf.rotation(i) + speed * ctx.dt());
                }
            };
        });

        // ==================== output（M13-07 / M21l 数据驱动） ====================
        // 着色器不穷举：vertex/shader/blend 全部由图数据显式指定（共享 OUTPUT_PROPERTIES 仅中性兜底），
        // 节点类型只决定几何；layer 过滤该输出渲染的粒子层（多输出节点实现分层，无 smoke 概念）。

        metadata.register(type("vfx.output_point", "output", "Output Points", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_point", n -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.output_quad", "output", "Output Quad / Billboard", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_quad", n -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.output_quad_additive", "output", "Output Quad / Additive", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_quad_additive", n -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.output_quad_glow", "output", "Output Quad / Additive Glow", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_quad_glow", n -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.output_mesh", "output", "Output Mesh", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_mesh", n -> (buf, ctx) -> {
        });

        metadata.register(type("vfx.output_line", "output", "Output Line / Trail", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_line", n -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        });

        metadata.register(type("vfx.output_ribbon", "output", "Output Ribbon", OUTPUT_PROPERTIES));
        vfx.register("vfx.output_ribbon", n -> (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        });

        // ==================== param（M15-04 存活参数兜底） ====================

        metadata.register(type("vfx.param_float", "param", "Float Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("value", ValueType.FLOAT, Value.of(0f))
                )));
        vfx.register("vfx.param_float", node -> {
            var param = propString(node, "param", "");
            var value = propFloat(node, "value", 0f);
            return (buf, ctx) -> {
                // 仅在无外部绑定时提供兜底值；存活参数由 setLiveParam 注入
                if (param.isEmpty() || ctx.param(param) != null) return;
                ctx.paramIfAbsent(param, Value.of(value));
            };
        });

        metadata.register(type("vfx.param_vec3", "param", "Vec3 Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("x", ValueType.FLOAT, Value.of(0f)),
                        prop("y", ValueType.FLOAT, Value.of(0f)),
                        prop("z", ValueType.FLOAT, Value.of(0f))
                )));
        vfx.register("vfx.param_vec3", node -> {
            var param = propString(node, "param", "");
            var x = propFloat(node, "x", 0f);
            var y = propFloat(node, "y", 0f);
            var z = propFloat(node, "z", 0f);
            return (buf, ctx) -> {
                if (param.isEmpty() || ctx.param(param) != null) return;
                ctx.paramIfAbsent(param, Value.of(new Vector3f(x, y, z)));
            };
        });

        metadata.register(type("vfx.param_color", "param", "Color Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("r", ValueType.FLOAT, Value.of(1f)),
                        prop("g", ValueType.FLOAT, Value.of(1f)),
                        prop("b", ValueType.FLOAT, Value.of(1f)),
                        prop("a", ValueType.FLOAT, Value.of(1f))
                )));
        vfx.register("vfx.param_color", node -> {
            var param = propString(node, "param", "");
            var r = propFloat(node, "r", 1f);
            var g = propFloat(node, "g", 1f);
            var b = propFloat(node, "b", 1f);
            var a = propFloat(node, "a", 1f);
            return (buf, ctx) -> {
                if (param.isEmpty() || ctx.param(param) != null) return;
                ctx.paramIfAbsent(param, Value.color(r, g, b, a));
            };
        });

        metadata.register(type("vfx.param_curve", "param", "Curve Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("curve", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.param_curve", node -> {
            var param = propString(node, "param", "");
            var curveId = propString(node, "curve", "");
            return (buf, ctx) -> {
                // 把黑板 CURVE 源参数复制到引用的 param id（缺省时），供 over-life 节点按 param 引用采样
                if (param.isEmpty() || ctx.curve(param) != null) return;
                var source = ctx.curve(curveId);
                if (source != null) {
                    ctx.curveIfAbsent(param, source);
                }
            };
        });

        metadata.register(type("vfx.param_gradient", "param", "Gradient Parameter",
                List.of(
                        prop("param", ValueType.STRING, Value.string("")),
                        prop("gradient", ValueType.STRING, Value.string(""))
                )));
        vfx.register("vfx.param_gradient", node -> {
            var param = propString(node, "param", "");
            var gradientId = propString(node, "gradient", "");
            return (buf, ctx) -> {
                if (param.isEmpty() || ctx.gradient(param) != null) return;
                var source = ctx.gradient(gradientId);
                if (source != null) {
                    ctx.gradientIfAbsent(param, source);
                }
            };
        });
    }

    // ==================== spawn 工厂 ====================

    private static SimNode spawnRate(GraphNode node) {
        var lifetime = propFloat(node, "lifetime", 1f);
        var size = propFloat(node, "size", 0.1f);
        var color = propColor(node, "color");
        var vx = propFloat(node, "vx", 0f);
        var vy = propFloat(node, "vy", 0f);
        var vz = propFloat(node, "vz", 0f);
        var param = propString(node, "param", "");
        var shape = buildShape(node);
        var layer = layerOf(node);
        float[] acc = {0f};

        return (buf, ctx) -> {
            var rate = param.isEmpty() ? propFloat(node, "rate", 10f) : ctx.paramFloat(param, 10f);
            acc[0] += rate * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            // 任何情况都先标记本帧新粒子起点：n==0 时空跑 init（避免对旧粒子重复 init，暂停时抖动/消失）
            ctx.spawnStart = buf.count();
            if (n == 0) return;
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
        };
    }

    private static SimNode spawnBurst(GraphNode node) {
        var count = propInt(node, "count", 10);
        var lifetime = propFloat(node, "lifetime", 1f);
        var size = propFloat(node, "size", 0.1f);
        var color = propColor(node, "color");
        var vx = propFloat(node, "vx", 0f);
        var vy = propFloat(node, "vy", 0f);
        var vz = propFloat(node, "vz", 0f);
        var shape = buildShape(node);
        var layer = layerOf(node);
        boolean[] fired = {false};

        return (buf, ctx) -> {
            if (fired[0]) {
                // 已触发：标记起点为当前数量，init 空跑（避免对旧粒子重复 init）
                ctx.spawnStart = buf.count();
                return;
            }
            fired[0] = true;
            ctx.spawnStart = buf.count();
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
        };
    }

    private static SimNode spawnPeriodic(GraphNode node) {
        var count = propInt(node, "count", 5);
        var interval = propFloat(node, "interval", 1f);
        var lifetime = propFloat(node, "lifetime", 1f);
        var size = propFloat(node, "size", 0.1f);
        var color = propColor(node, "color");
        var vx = propFloat(node, "vx", 0f);
        var vy = propFloat(node, "vy", 0f);
        var vz = propFloat(node, "vz", 0f);
        var shape = buildShape(node);
        var layer = layerOf(node);
        float[] acc = {0f};

        return (buf, ctx) -> {
            acc[0] += ctx.dt();
            // 任何情况都先标记起点：未到间隔时空跑 init（避免对旧粒子重复 init）
            ctx.spawnStart = buf.count();
            if (acc[0] < interval) return;
            acc[0] = 0f;
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
        };
    }

    private static SimNode spawnDistance(GraphNode node) {
        var rate = propFloat(node, "rate", 5f);
        var speed = propFloat(node, "speed", 1f);
        var lifetime = propFloat(node, "lifetime", 1f);
        var size = propFloat(node, "size", 0.1f);
        var color = propColor(node, "color");
        var vx = propFloat(node, "vx", 0f);
        var vy = propFloat(node, "vy", 0f);
        var vz = propFloat(node, "vz", 0f);
        var layer = layerOf(node);
        float[] acc = {0f};

        return (buf, ctx) -> {
            // 近似：按 emitter 速度折算每秒 spawn 数（无轨迹追踪时的简化）
            acc[0] += rate * speed * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            // 任何情况都先标记起点：n==0 时空跑 init
            ctx.spawnStart = buf.count();
            if (n == 0) return;
            for (var k = 0; k < n; k++) {
                var i = buf.spawn();
                buf.setVelocity(i, vx, vy, vz);
                buf.setSize(i, size);
                buf.setColor(i, color[0], color[1], color[2], color[3]);
                buf.setLifetime(i, lifetime);
                buf.setAge(i, 0f);
                buf.setLayer(i, layer);
            }
        };
    }

    // ==================== 辅助 ====================

    private static float lifeT(ParticleBuffer buf, int i) {
        var l = buf.lifetime(i);
        if (l <= 0f) return 1f;
        return Math.min(1f, buf.age(i) / l);
    }

    private static float jitter(SimContext ctx, float amp) {
        return amp * (ctx.random().nextFloat() * 2f - 1f);
    }

    /**
     * over-life 节点 layer 过滤：""=全部，否则只作用于指定层（-1=全部，0=fire，1=smoke）。
     */
    private static byte layerFilter(GraphNode node) {
        return ParticleBuffer.layerFilter(propString(node, "layer", ""));
    }

    /**
     * spawn 节点 layer 属性（fire/smoke）→ 粒子层字节。
     */
    private static byte layerOf(GraphNode node) {
        return ParticleBuffer.layerByte(propString(node, "layer", "fire"));
    }

    private static float hash(float x, float y, float z) {
        var s = (float) Math.sin(x * 127.1f + y * 311.7f + z * 74.7f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    private static EmitterShape buildShape(GraphNode node) {
        var ox = propFloat(node, "origin_x", 0f);
        var oy = propFloat(node, "origin_y", 0f);
        var oz = propFloat(node, "origin_z", 0f);
        var scale = propFloat(node, "mesh_scale", 1f);
        return switch (propString(node, "shape", "point")) {
            case "sphere" -> new SphereShape(ox, oy, oz, propFloat(node, "radius", 1f));
            case "box" -> new BoxShape(ox, oy, oz,
                    propFloat(node, "half_x", 1f), propFloat(node, "half_y", 1f), propFloat(node, "half_z", 1f));
            case "cone" -> new ConeShape(ox, oy, oz, propFloat(node, "radius", 1f), propFloat(node, "cone_height", 2f));
            case "cylinder" ->
                    new CylinderShape(ox, oy, oz, propFloat(node, "radius", 1f), propFloat(node, "cone_height", 2f));
            case "torus" -> new TorusShape(ox, oy, oz,
                    propFloat(node, "radius", 1f), propFloat(node, "half_x", 0.25f));
            case "circle_edge" -> new CircleEdgeShape(ox, oy, oz, propFloat(node, "radius", 1f));
            case "disc" -> new DiscShape(ox, oy, oz, propFloat(node, "radius", 1f));
            case "mesh" -> meshShape(node, ox, oy, oz, scale);
            default -> new PointShape(ox, oy, oz);
        };
    }

    /**
     * mesh 形状（A3）：按 {@code mesh} 属性查注册的三角形资产，未注册回退单位立方体。
     */
    private static EmitterShape meshShape(GraphNode node, float ox, float oy, float oz, float scale) {
        var id = propString(node, "mesh", "");
        var triangles = id.isEmpty() ? null : MeshAssets.triangles(id);
        return triangles == null
                ? MeshShape.unitCube(ox, oy, oz, scale)
                : new MeshShape(ox, oy, oz, scale, triangles);
    }

    private static float propFloat(GraphNode node, String id, float def) {
        var v = node.properties().get(id);
        return v == null ? def : Float.parseFloat(v);
    }

    private static int propInt(GraphNode node, String id, int def) {
        var v = node.properties().get(id);
        return v == null ? def : Integer.parseInt(v);
    }

    private static boolean propBool(GraphNode node, String id, boolean def) {
        var v = node.properties().get(id);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static String propString(GraphNode node, String id, String def) {
        return node.properties().getOrDefault(id, def);
    }

    private static float[] propColor(GraphNode node, String id) {
        var csv = node.properties().get(id);
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
