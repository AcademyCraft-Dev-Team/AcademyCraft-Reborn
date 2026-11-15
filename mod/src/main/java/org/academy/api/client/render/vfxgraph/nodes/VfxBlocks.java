package org.academy.api.client.render.vfxgraph.nodes;


import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class VfxBlocks {
    private VfxBlocks() {
    }

    private static final List<PropertySpec> PARTICLE_BASIC = List.of(
            prop("lifetime", ValueType.FLOAT, Value.of(1f)),
            prop("size", ValueType.FLOAT, Value.of(0.1f)),
            prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)),
            prop("vx", ValueType.FLOAT, Value.of(0f)),
            prop("vy", ValueType.FLOAT, Value.of(0f)),
            prop("vz", ValueType.FLOAT, Value.of(0f)),
            prop("layer", ValueType.STRING, Value.string("fire"))
    );

    private static final List<PropertySpec> SHAPE_PROPS = List.of(
            prop("shape", ValueType.STRING, Value.string("point")),
            prop("position_param", ValueType.STRING, Value.string("")),
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

    private static final List<PropertySpec> SPAWN_TAIL = Stream.concat(
            PARTICLE_BASIC.stream(), SHAPE_PROPS.stream()).toList();

    private static final List<PropertySpec> OUTPUT_PROPERTIES = List.of(
            prop("vertex", ValueType.STRING, Value.string("")),
            prop("shader", ValueType.STRING, Value.string("")),
            prop("texture", ValueType.STRING, Value.string("")),
            prop("blend", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    private static final List<PropertySpec> DISTORTION_OUTPUT_PROPERTIES = List.of(
            prop("layer", ValueType.STRING, Value.string("")),
            prop("distortion_strength", ValueType.FLOAT, Value.of(0.025f)),
            prop("distortion_ring_width", ValueType.FLOAT, Value.of(0.5f)),
            prop("distortion_edge_blur", ValueType.FLOAT, Value.of(0.05f))
    );

    private static final List<PropertySpec> SCREEN_FLASH_OUTPUT_PROPERTIES = List.of(
            prop("vertex", ValueType.STRING, Value.string("")),
            prop("shader", ValueType.STRING, Value.string("")),
            prop("blend", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string("")),
            prop("cap", ValueType.FLOAT, Value.of(1f))
    );

    private static final List<PropertySpec> INSTANCED_TEX_OUTPUT_PROPERTIES = List.of(
            prop("topology", ValueType.STRING, Value.string("quads")),
            prop("texture1", ValueType.STRING, Value.string("")),
            prop("scale", ValueType.STRING, Value.string("")),
            prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f))
    );

    private static final List<PropertySpec> CURVE_LAYER = List.of(
            prop("curve", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    private static final List<PropertySpec> NOISE = List.of(
            prop("amplitude", ValueType.FLOAT, Value.of(1f)),
            prop("frequency", ValueType.FLOAT, Value.of(1f))
    );

    private static final List<PropertySpec> BOUNCE_KILL = List.of(
            prop("bounce", ValueType.FLOAT, Value.of(0.5f)),
            prop("kill", ValueType.BOOL, Value.of(false))
    );

    public static void registerAll(NodeRegistry metadata, VfxBlockRegistry blocks) {
        registerCore(metadata, blocks);
        NeoForge.EVENT_BUS.post(new VfxBlockRegistrationEvent(metadata, blocks));
    }

    private static void registerCore(NodeRegistry metadata, VfxBlockRegistry blocks) {
        metadata.register(spawnTypeWithPorts("vfx.block.spawn_rate", "Spawn Rate",
                List.of(in("rate", "Rate", ValueType.FLOAT)),
                List.of(prop("rate", ValueType.FLOAT, Value.of(10f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.spawn_rate", SpawnBlocks::rate);

        metadata.register(spawnTypeWithPorts("vfx.block.spawn_burst", "Spawn Burst",
                List.of(),
                List.of(prop("count", ValueType.INT, Value.of(10)))));
        blocks.register("vfx.block.spawn_burst", SpawnBlocks::burst);

        metadata.register(spawnTypeWithPorts("vfx.block.spawn_periodic", "Spawn Periodic Burst",
                List.of(),
                List.of(prop("count", ValueType.INT, Value.of(5)), prop("interval", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.spawn_periodic", SpawnBlocks::periodic);

        metadata.register(spawnTypeWithPorts("vfx.block.spawn_distance", "Spawn By Distance",
                List.of(),
                List.of(prop("rate", ValueType.FLOAT, Value.of(5f)), prop("speed", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.spawn_distance", SpawnBlocks::distance);


        metadata.register(type("vfx.block.init_position", "init", "Set Position (Shape)", SHAPE_PROPS));
        blocks.register("vfx.block.init_position", InitBlocks::position);

        metadata.register(typeWithPorts("vfx.block.init_velocity", "init", "Set Velocity",
                List.of(in("vx", "X", ValueType.FLOAT), in("vy", "Y", ValueType.FLOAT), in("vz", "Z", ValueType.FLOAT)),
                List.of(
                        prop("vx", ValueType.FLOAT, Value.of(0f)),
                        prop("vy", ValueType.FLOAT, Value.of(1f)),
                        prop("vz", ValueType.FLOAT, Value.of(0f)),
                        prop("random", ValueType.FLOAT, Value.of(0f)),
                        prop("param", ValueType.STRING, Value.string(""))
                )));
        blocks.register("vfx.block.init_velocity", InitBlocks::velocity);

        metadata.register(typeWithPorts("vfx.block.init_color", "init", "Set Color",
                List.of(in("color", "Color", ValueType.COLOR)),
                List.of(prop("color", ValueType.COLOR, Value.color(1f, 1f, 1f, 1f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.init_color", InitBlocks::color);

        metadata.register(typeWithPorts("vfx.block.init_size", "init", "Set Size",
                List.of(in("size", "Size", ValueType.FLOAT)),
                List.of(prop("size", ValueType.FLOAT, Value.of(0.1f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.init_size", InitBlocks::size);

        metadata.register(type("vfx.block.init_rotation", "init", "Set Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.init_rotation", InitBlocks::rotation);

        metadata.register(type("vfx.block.init_lifetime", "init", "Set Lifetime",
                List.of(prop("lifetime", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.init_lifetime", InitBlocks::lifetime);

        metadata.register(type("vfx.block.init_mass", "init", "Set Mass",
                List.of(prop("mass", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.init_mass", InitBlocks::mass);

        metadata.register(type("vfx.block.init_randomize", "init", "Randomize",
                List.of(
                        prop("pos", ValueType.FLOAT, Value.of(0.1f)),
                        prop("vel", ValueType.FLOAT, Value.of(0.1f)),
                        prop("size", ValueType.FLOAT, Value.of(0.1f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.1f))
                )));
        blocks.register("vfx.block.init_randomize", InitBlocks::randomize);


        metadata.register(type("vfx.block.update_velocity", "update", "Integrate Velocity", List.of()));
        blocks.register("vfx.block.update_velocity", UpdateBlocks::velocity);

        metadata.register(typeWithPorts("vfx.block.update_gravity", "update", "Gravity",
                List.of(in("gravity", "Gravity", ValueType.FLOAT)),
                List.of(prop("gravity", ValueType.FLOAT, Value.of(-9.8f)), prop("param", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.update_gravity", UpdateBlocks::gravity);

        metadata.register(type("vfx.block.update_force", "update", "Constant Force",
                List.of(prop("fx", ValueType.FLOAT, Value.of(0f)), prop("fy", ValueType.FLOAT, Value.of(0f)),
                        prop("fz", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.update_force", UpdateBlocks::force);

        metadata.register(type("vfx.block.update_noise", "update", "Noise Force", NOISE));
        blocks.register("vfx.block.update_noise", UpdateBlocks::noise);

        metadata.register(type("vfx.block.update_turbulence", "update", "Turbulence", NOISE));
        blocks.register("vfx.block.update_turbulence", UpdateBlocks::turbulence);

        metadata.register(type("vfx.block.update_vortex", "update", "Vortex",
                List.of(prop("cx", ValueType.FLOAT, Value.of(0f)), prop("cy", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)), prop("strength", ValueType.FLOAT, Value.of(1f)),
                        prop("pull", ValueType.FLOAT, Value.of(0f)),
                        prop("vertical_pull", ValueType.FLOAT, Value.of(0f)),
                        prop("layer", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.update_vortex", UpdateBlocks::vortex);

        metadata.register(type("vfx.block.update_follow", "update", "Follow Live Position",
                List.of(
                        prop("position_param", ValueType.STRING, Value.string("projectile_position")),
                        prop("direction_param", ValueType.STRING, Value.string("projectile_direction")),
                        prop("layer", ValueType.STRING, Value.string("")),
                        prop("orbit_radius", ValueType.FLOAT, Value.of(0f)),
                        prop("orbit_speed", ValueType.FLOAT, Value.of(0f)),
                        prop("phase", ValueType.FLOAT, Value.of(0f)),
                        prop("back_offset", ValueType.FLOAT, Value.of(0f)),
                        prop("size_param", ValueType.STRING, Value.string("")),
                        prop("size_min", ValueType.FLOAT, Value.of(1f)),
                        prop("size_max", ValueType.FLOAT, Value.of(1f)),
                        prop("size_power", ValueType.FLOAT, Value.of(1f))
                )));
        blocks.register("vfx.block.update_follow", UpdateBlocks::follow);

        metadata.register(type("vfx.block.update_live", "update", "Apply Live Attributes",
                List.of(
                        prop("layer", ValueType.STRING, Value.string("")),
                        prop("size_param", ValueType.STRING, Value.string("")),
                        prop("size_scale", ValueType.FLOAT, Value.of(1f)),
                        prop("alpha_param", ValueType.STRING, Value.string("")),
                        prop("alpha_scale", ValueType.FLOAT, Value.of(1f)),
                        prop("rotation_param", ValueType.STRING, Value.string("")),
                        prop("rotation_scale", ValueType.FLOAT, Value.of(1f))
                )));
        blocks.register("vfx.block.update_live", UpdateBlocks::live);

        metadata.register(type("vfx.block.update_drag", "update", "Drag",
                List.of(prop("drag", ValueType.FLOAT, Value.of(0.1f)))));
        blocks.register("vfx.block.update_drag", UpdateBlocks::drag);

        metadata.register(type("vfx.block.update_damping", "update", "Damping",
                List.of(prop("damping", ValueType.FLOAT, Value.of(0.5f)))));
        blocks.register("vfx.block.update_damping", UpdateBlocks::damping);

        metadata.register(type("vfx.block.update_age", "update", "Age", List.of()));
        blocks.register("vfx.block.update_age", UpdateBlocks::age);

        metadata.register(type("vfx.block.update_fade", "update", "Fade", List.of()));
        blocks.register("vfx.block.update_fade", UpdateBlocks::fade);


        metadata.register(type("vfx.block.collision_ground", "collision", "Ground Collision", BOUNCE_KILL));
        blocks.register("vfx.block.collision_ground", CollisionBlocks::ground);

        metadata.register(type("vfx.block.collision_plane", "collision", "Plane Collision",
                Stream.concat(Stream.of(prop("height", ValueType.FLOAT, Value.of(0f))),
                        BOUNCE_KILL.stream()).toList()));
        blocks.register("vfx.block.collision_plane", CollisionBlocks::plane);

        metadata.register(type("vfx.block.collision_sphere", "collision", "Sphere Collision",
                List.of(prop("cx", ValueType.FLOAT, Value.of(0f)), prop("cy", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)), prop("radius", ValueType.FLOAT, Value.of(1f)),
                        prop("bounce", ValueType.FLOAT, Value.of(0.5f)))));
        blocks.register("vfx.block.collision_sphere", CollisionBlocks::sphere);

        metadata.register(type("vfx.block.bounds", "collision", "Bounds (Kill Outside)",
                List.of(prop("min_x", ValueType.FLOAT, Value.of(-10f)), prop("min_y", ValueType.FLOAT, Value.of(-10f)),
                        prop("min_z", ValueType.FLOAT, Value.of(-10f)), prop("max_x", ValueType.FLOAT, Value.of(10f)),
                        prop("max_y", ValueType.FLOAT, Value.of(10f)), prop("max_z", ValueType.FLOAT, Value.of(10f)))));
        blocks.register("vfx.block.bounds", CollisionBlocks::bounds);

        metadata.register(type("vfx.block.kill", "collision", "Kill After Time",
                List.of(prop("time", ValueType.FLOAT, Value.of(5f)))));
        blocks.register("vfx.block.kill", CollisionBlocks::kill);


        metadata.register(type("vfx.block.life_color", "over-life", "Color Over Lifetime",
                List.of(prop("gradient", ValueType.STRING, Value.string("")), prop("layer", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.life_color", LifeBlocks::color);

        metadata.register(type("vfx.block.life_alpha", "over-life", "Alpha Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_alpha", LifeBlocks::alpha);

        metadata.register(type("vfx.block.life_size", "over-life", "Size Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_size", LifeBlocks::size);

        metadata.register(type("vfx.block.life_velocity", "over-life", "Velocity Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_velocity", LifeBlocks::velocity);


        metadata.register(type("vfx.block.orient_face_camera", "orient", "Face Camera", List.of()));
        blocks.register("vfx.block.orient_face_camera", OrientBlocks::faceCamera);

        metadata.register(type("vfx.block.orient_velocity", "orient", "Align To Velocity",
                List.of(prop("offset", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.orient_velocity", OrientBlocks::velocity);

        metadata.register(type("vfx.block.orient_fixed", "orient", "Fixed Rotation",
                List.of(prop("rotation", ValueType.FLOAT, Value.of(0f)))));
        blocks.register("vfx.block.orient_fixed", OrientBlocks::fixed);

        metadata.register(type("vfx.block.orient_spin", "orient", "Spin",
                List.of(prop("speed", ValueType.FLOAT, Value.of(1f)))));
        blocks.register("vfx.block.orient_spin", OrientBlocks::spin);


        metadata.register(type("vfx.block.output_point", "output", "Output Points", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_point", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_quad", "output", "Output Quad", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_quad", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_quad_additive", "output", "Output Quad / Additive", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_quad_additive", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_quad_glow", "output", "Output Quad / Additive Glow", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_quad_glow", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_mesh", "output", "Output Mesh", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_mesh", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_line", "output", "Output Line / Trail", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_line", OutputBlocks::trail);

        metadata.register(type("vfx.block.output_ribbon", "output", "Output Ribbon", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_ribbon", OutputBlocks::trail);


        metadata.register(type("vfx.block.output_distortion_ring", "output",
                "Output Screen-Distortion Ring", DISTORTION_OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_distortion_ring", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_screen_flash", "output",
                "Output Screen Flash", SCREEN_FLASH_OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_screen_flash", OutputBlocks::noop);

        metadata.register(type("vfx.block.output_instanced_tex", "output", "Output Instanced Textured Geometry",
                Stream.concat(OUTPUT_PROPERTIES.stream(), INSTANCED_TEX_OUTPUT_PROPERTIES.stream()).toList()));
        blocks.register("vfx.block.output_instanced_tex", OutputBlocks::noop);
        metadata.register(type("vfx.block.output_block_geometry", "output", "Output Block Geometry",
                OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_block_geometry", OutputBlocks::noop);
    }

    private static NodeType type(String id, String category, String name, List<PropertySpec> props) {
        return new NodeType(id, category, name, List.of(), props);
    }

    private static NodeType typeWithPorts(String id, String category, String name, List<PortSpec> ports,
                                          List<PropertySpec> props) {
        return new NodeType(id, category, name, List.copyOf(ports), List.copyOf(props));
    }

    private static NodeType spawnTypeWithPorts(String id, String name, List<PortSpec> ports,
                                               List<PropertySpec> props) {
        var all = new ArrayList<PropertySpec>(props.size() + SPAWN_TAIL.size());
        all.addAll(props);
        all.addAll(SPAWN_TAIL);
        return new NodeType(id, "spawn", name, List.copyOf(ports), List.copyOf(all));
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
