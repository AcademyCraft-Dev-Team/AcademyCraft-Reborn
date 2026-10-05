package org.academy.api.client.render.vfxgraph.nodes;


import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.registry.NodeType;
import org.academy.api.client.render.graph.registry.PortSpec;
import org.academy.api.client.render.graph.registry.PropertySpec;


import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;

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

    private static final List<PropertySpec> ARC_CONTACT_PROPS = List.of(
            prop("contact_mesh", ValueType.STRING, Value.string("builtin:sphere")),
            prop("contact_range", ValueType.FLOAT, Value.of(4.1f)),
            prop("contact_origin_x", ValueType.FLOAT, Value.of(0f)),
            prop("contact_origin_y", ValueType.FLOAT, Value.of(4.3f)),
            prop("contact_origin_z", ValueType.FLOAT, Value.of(0f))
    );

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
        metadata.register(type("vfx.block.material_sheets", "spawn", "Material Sheets / Sampled Surface", MaterialSheetEmitter.properties()));
        blocks.register("vfx.block.material_sheets", (block, ports) -> MaterialSheetEmitter.create(block));
        metadata.register(type("vfx.block.organic_strands", "spawn", "Translucent Organic Strands",
                List.of(prop("mode", ValueType.INT, Value.of(0)),
                        prop("count", ValueType.INT, Value.of(12)),
                        prop("segments", ValueType.INT, Value.of(80)),
                        prop("duration", ValueType.FLOAT, Value.of(0.65f)),
                        prop("radius", ValueType.FLOAT, Value.of(0.65f)),
                        prop("length", ValueType.FLOAT, Value.of(16f)),
                        prop("speed", ValueType.FLOAT, Value.of(8f)),
                        prop("thickness", ValueType.FLOAT, Value.of(0.04f)),
                        prop("opacity", ValueType.FLOAT, Value.of(0.6f)))));
        blocks.register("vfx.block.organic_strands", (block, ports) -> OrganicStrandEmitter.create(block));
        metadata.register(type("vfx.block.cloud_vortex", "spawn", "Cloud Vortex / Hollow Turbulent Funnel",
                CloudVortexEmitter.properties()));
        blocks.register("vfx.block.cloud_vortex", CloudVortexEmitter::create);
        metadata.register(type("vfx.block.iron_sand", "spawn", "Iron Sand / Granular Field and Embedded Currents",
                IronSandEmitter.properties()));
        blocks.register("vfx.block.iron_sand", IronSandEmitter::create);
        metadata.register(type("vfx.block.electric_ring", "spawn", "Blue-White Expanding / Contracting Ring",
                ElectricRingEmitter.properties()));
        blocks.register("vfx.block.electric_ring", ElectricRingEmitter::create);
        metadata.register(type("vfx.block.electric_shield", "spawn", "Blue-White Shield Surface / Interception",
                ElectricAttachmentEmitter.shieldProperties()));
        blocks.register("vfx.block.electric_shield", ElectricAttachmentEmitter::shield);
        metadata.register(type("vfx.block.electric_paths", "spawn", "Blue-White Bound Paths / Trails",
                ElectricAttachmentEmitter.pathProperties()));
        blocks.register("vfx.block.electric_paths", ElectricAttachmentEmitter::paths);
        metadata.register(type("vfx.block.electric_bolt", "spawn", "Blue-White Endpoint Discharge",
                ElectricArcEmitter.boltProperties()));
        blocks.register("vfx.block.electric_bolt", ElectricArcEmitter::bolt);
        metadata.register(type("vfx.block.electric_tree", "spawn", "Blue-White Forward Delta Tree",
                ElectricArcEmitter.treeProperties()));
        blocks.register("vfx.block.electric_tree", ElectricArcEmitter::tree);
        metadata.register(type("vfx.block.electric_orbit", "spawn", "Charge Ring / Hot Arcs / Filaments",
                ElectricArcEmitter.orbitProperties()));
        blocks.register("vfx.block.electric_orbit", ElectricArcEmitter::orbit);

        metadata.register(type("vfx.block.axial_beam", "spawn", "Axial Burst / Beam Layer",
                AxialBurstEmitter.beamProperties()));
        blocks.register("vfx.block.axial_beam", AxialBurstEmitter::beam);
        metadata.register(type("vfx.block.axial_discharge", "spawn", "Axial Burst / Discharge",
                AxialBurstEmitter.dischargeProperties()));
        blocks.register("vfx.block.axial_discharge", AxialBurstEmitter::discharge);
        metadata.register(type("vfx.block.axial_shock", "spawn", "Axial Burst / Pressure and Sparks",
                AxialBurstEmitter.shockProperties()));
        blocks.register("vfx.block.axial_shock", AxialBurstEmitter::shock);
        metadata.register(type("vfx.block.sky_discharge", "spawn", "Cloud-to-ground Discharge",
                SkyDischargeEmitter.channelProperties()));
        blocks.register("vfx.block.sky_discharge", SkyDischargeEmitter::channel);
        metadata.register(type("vfx.block.storm_atmosphere", "spawn", "Storm Cloud / Impact Atmosphere",
                SkyDischargeEmitter.atmosphereProperties()));
        blocks.register("vfx.block.storm_atmosphere", SkyDischargeEmitter::atmosphere);
        metadata.register(type("vfx.block.surface_discharge", "spawn", "Surface-Attached Lightning Patches",
                SurfaceDischargeEmitter.properties()));
        blocks.register("vfx.block.surface_discharge", SurfaceDischargeEmitter::create);

        metadata.register(typeWithPorts("vfx.block.spawn_rate", "spawn", "Spawn Rate",
                List.of(in("rate", "Rate", ValueType.FLOAT)),
                List.of(prop("rate", ValueType.FLOAT, Value.of(10f)), prop("param", ValueType.STRING, Value.string(""))),
                SPAWN_TAIL));
        blocks.register("vfx.block.spawn_rate", SpawnBlocks::rate);

        metadata.register(typeWithPorts("vfx.block.spawn_burst", "spawn", "Spawn Burst",
                List.of(),
                List.of(prop("count", ValueType.INT, Value.of(10))), SPAWN_TAIL));
        blocks.register("vfx.block.spawn_burst", SpawnBlocks::burst);

        metadata.register(typeWithPorts("vfx.block.spawn_periodic", "spawn", "Spawn Periodic Burst",
                List.of(),
                List.of(prop("count", ValueType.INT, Value.of(5)), prop("interval", ValueType.FLOAT, Value.of(1f))),
                SPAWN_TAIL));
        blocks.register("vfx.block.spawn_periodic", SpawnBlocks::periodic);

        metadata.register(typeWithPorts("vfx.block.spawn_distance", "spawn", "Spawn By Distance",
                List.of(),
                List.of(prop("rate", ValueType.FLOAT, Value.of(5f)), prop("speed", ValueType.FLOAT, Value.of(1f))),
                SPAWN_TAIL));
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
                List.of(prop("height", ValueType.FLOAT, Value.of(0f))), BOUNCE_KILL));
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
                        prop("reshuffle", ValueType.FLOAT, Value.of(0f)),
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
        blocks.register("vfx.block.arc_bolt", ArcEmitterBlocks::bolt);

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
        blocks.register("vfx.block.arc_orbit", ArcEmitterBlocks::orbit);

        metadata.register(type("vfx.block.vortex_jet", "spawn", "Continuous Ink Vortex Jet",
                List.of(
                        prop("side", ValueType.FLOAT, Value.of(1f)),
                        prop("root_x", ValueType.FLOAT, Value.of(0.18f)),
                        prop("length", ValueType.FLOAT, Value.of(5.2f)),
                        prop("rise", ValueType.FLOAT, Value.of(3f)),
                        prop("back", ValueType.FLOAT, Value.of(2.5f)),
                        prop("radius", ValueType.FLOAT, Value.of(0.65f)),
                        prop("turns", ValueType.FLOAT, Value.of(5.5f)),
                        prop("speed", ValueType.FLOAT, Value.of(8f)),
                        prop("filaments", ValueType.INT, Value.of(18)),
                        prop("segments", ValueType.INT, Value.of(100)),
                        prop("flecks", ValueType.INT, Value.of(24)),
                        prop("highlight_count", ValueType.INT, Value.of(10)),
                        prop("highlight_speed", ValueType.FLOAT, Value.of(0.22f)),
                        prop("highlight_color", ValueType.COLOR, Value.color(0.64f, 0.12f, 1f, 0.95f)),
                        prop("hollow", ValueType.FLOAT, Value.of(0.8f)),
                        prop("phase", ValueType.FLOAT, Value.of(0f)),
                        prop("sweep_param", ValueType.STRING, Value.string("sweep_right")),
                        prop("pitch_param", ValueType.STRING, Value.string("pitch_right")),
                        prop("color", ValueType.COLOR, Value.color(0.009f, 0.01f, 0.014f, 1f))
                )));
        blocks.register("vfx.block.vortex_jet", VortexJetBlock::create);

        metadata.register(type("vfx.block.arc_tornado", "spawn", "Compressed Wind Ring Tornado",
                List.of(
                        prop("expand_param", ValueType.STRING, Value.string("expand_rate")),
                        prop("expand_min", ValueType.FLOAT, Value.of(0.5f)),
                        prop("expand_max", ValueType.FLOAT, Value.of(3f)),
                        prop("charge_param", ValueType.STRING, Value.string("charge_progress")),
                        prop("emission_param", ValueType.STRING, Value.string("emission")),
                        prop("focus_param", ValueType.STRING, Value.string("focus_offset")),
                        prop("bottom_radius", ValueType.FLOAT, Value.of(15f)),
                        prop("base_radius", ValueType.FLOAT, Value.of(15f)),
                        prop("max_radius", ValueType.FLOAT, Value.of(96f)),
                        prop("base_height", ValueType.FLOAT, Value.of(32f)),
                        prop("max_height", ValueType.FLOAT, Value.of(128f)),
                        prop("min_rings", ValueType.INT, Value.of(9)),
                        prop("max_rings", ValueType.INT, Value.of(30)),
                        prop("min_helices", ValueType.INT, Value.of(1)),
                        prop("max_helices", ValueType.INT, Value.of(3)),
                        prop("ring_segments", ValueType.INT, Value.of(88)),
                        prop("helix_segments", ValueType.INT, Value.of(224)),
                        prop("helix_turns", ValueType.FLOAT, Value.of(8.5f)),
                        prop("width", ValueType.FLOAT, Value.of(0.12f)),
                        prop("rotation_min", ValueType.FLOAT, Value.of(0.82f)),
                        prop("rotation_max", ValueType.FLOAT, Value.of(5.2f)),
                        prop("irregularity", ValueType.FLOAT, Value.of(0.095f)),
                        prop("center_wander", ValueType.FLOAT, Value.of(0.055f)),
                        prop("fragmentation", ValueType.FLOAT, Value.of(0.22f)),
                        prop("tilt", ValueType.FLOAT, Value.of(0.075f)),
                        prop("nested_radius", ValueType.FLOAT, Value.of(0.54f)),
                        prop("nested_width", ValueType.FLOAT, Value.of(0.72f)),
                        prop("collapse_start", ValueType.FLOAT, Value.of(0.8f)),
                        prop("collapse_end", ValueType.FLOAT, Value.of(0.94f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.075f)),
                        prop("color_dark", ValueType.COLOR, Value.color(0.28f, 0.3f, 0.34f, 0.32f)),
                        prop("color_light", ValueType.COLOR, Value.color(0.58f, 0.61f, 0.66f, 0.42f)),
                        prop("color_edge", ValueType.COLOR, Value.color(0.88f, 0.91f, 0.96f, 0.52f))
                )));
        blocks.register("vfx.block.arc_tornado", ArcTornadoBlock::create);

        metadata.register(type("vfx.block.tornado_volume", "spawn", "Volumetric Inverted Cone Tornado",
                List.of(
                        prop("expand_param", ValueType.STRING, Value.string("expand_rate")),
                        prop("charge_param", ValueType.STRING, Value.string("charge_progress")),
                        prop("emission_param", ValueType.STRING, Value.string("emission")),
                        prop("focus_param", ValueType.STRING, Value.string("focus_offset")),
                        prop("expand_min", ValueType.FLOAT, Value.of(0.5f)),
                        prop("expand_max", ValueType.FLOAT, Value.of(3f)),
                        prop("bottom_radius", ValueType.FLOAT, Value.of(15f)),
                        prop("base_radius", ValueType.FLOAT, Value.of(15f)),
                        prop("max_radius", ValueType.FLOAT, Value.of(96f)),
                        prop("base_height", ValueType.FLOAT, Value.of(32f)),
                        prop("max_height", ValueType.FLOAT, Value.of(128f)),
                        prop("volume_count", ValueType.INT, Value.of(480)),
                        prop("dust_count", ValueType.INT, Value.of(420)),
                        prop("volume_size", ValueType.FLOAT, Value.of(5.4f)),
                        prop("dust_size", ValueType.FLOAT, Value.of(0.8f)),
                        prop("rotation_speed", ValueType.FLOAT, Value.of(3.4f)),
                        prop("rise_speed", ValueType.FLOAT, Value.of(0.105f)),
                        prop("inward_force", ValueType.FLOAT, Value.of(3.2f)),
                        prop("turbulence", ValueType.FLOAT, Value.of(0.18f)),
                        prop("drag", ValueType.FLOAT, Value.of(2.4f)),
                        prop("volume_turns", ValueType.FLOAT, Value.of(3.8f)),
                        prop("dust_turns", ValueType.FLOAT, Value.of(8.5f)),
                        prop("volume_radius_scale", ValueType.FLOAT, Value.of(1.45f)),
                        prop("dust_radius_scale", ValueType.FLOAT, Value.of(1.55f)),
                        prop("collapse_start", ValueType.FLOAT, Value.of(0.8f)),
                        prop("collapse_end", ValueType.FLOAT, Value.of(0.94f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(120f)),
                        prop("volume_layer", ValueType.STRING, Value.string("wind_volume")),
                        prop("dust_layer", ValueType.STRING, Value.string("wind_dust")),
                        prop("volume_color", ValueType.COLOR, Value.color(0.70f, 0.73f, 0.76f, 0.40f)),
                        prop("dust_color", ValueType.COLOR, Value.color(0.50f, 0.48f, 0.44f, 0.72f))
                )));
        blocks.register("vfx.block.tornado_volume", TornadoVolumeBlock::create);

        metadata.register(type("vfx.block.plasma_convergence", "spawn", "Plasma Convergence Motes",
                List.of(
                        prop("progress_param", ValueType.STRING, Value.string("focus_progress")),
                        prop("formation_param", ValueType.STRING, Value.string("")),
                        prop("count", ValueType.INT, Value.of(72)),
                        prop("arm_count", ValueType.INT, Value.of(4)),
                        prop("turns", ValueType.FLOAT, Value.of(5.5f)),
                        prop("stagger", ValueType.FLOAT, Value.of(0.28f)),
                        prop("angular_acceleration", ValueType.FLOAT, Value.of(6.2f)),
                        prop("irregularity", ValueType.FLOAT, Value.of(0.09f)),
                        prop("start_radius", ValueType.FLOAT, Value.of(18f)),
                        prop("start_height", ValueType.FLOAT, Value.of(12f)),
                        prop("end_radius", ValueType.FLOAT, Value.of(0.24f)),
                        prop("size_min", ValueType.FLOAT, Value.of(0.16f)),
                        prop("size_max", ValueType.FLOAT, Value.of(0.88f)),
                        prop("surface_bulges", ValueType.INT, Value.of(3)),
                        prop("surface_radius", ValueType.FLOAT, Value.of(6.65f)),
                        prop("surface_pulse", ValueType.FLOAT, Value.of(0.42f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(120f)),
                        prop("layer", ValueType.STRING, Value.string("plasma_mote")),
                        prop("color", ValueType.COLOR, Value.color(1f, 0.34f, 0.78f, 0.94f))
                )));
        blocks.register("vfx.block.plasma_convergence", PlasmaConvergenceBlock::create);

        metadata.register(type("vfx.block.arc_plasma_shell", "spawn", "Surface-Wrapped Segmented Lightning",
                List.of(
                        prop("position_param", ValueType.STRING, Value.string("")),
                        prop("emission_param", ValueType.STRING, Value.string("")),
                        prop("progress_param", ValueType.STRING, Value.string("")),
                        prop("radius", ValueType.FLOAT, Value.of(8.45f)),
                        prop("radius_min", ValueType.FLOAT, Value.of(0.42f)),
                        prop("radius_max", ValueType.FLOAT, Value.of(8.45f)),
                        prop("radius_power", ValueType.FLOAT, Value.of(2.3f)),
                        prop("duration", ValueType.FLOAT, Value.of(0f)),
                        prop("count", ValueType.INT, Value.of(3)),
                        prop("segments", ValueType.INT, Value.of(72)),
                        prop("rotation_speed", ValueType.FLOAT, Value.of(0.72f)),
                        prop("jitter", ValueType.FLOAT, Value.of(0.022f)),
                        prop("arc_span_min", ValueType.FLOAT, Value.of(0.16f)),
                        prop("arc_span_max", ValueType.FLOAT, Value.of(0.38f)),
                        prop("surface_offset", ValueType.FLOAT, Value.of(0.22f)),
                        prop("flicker_rate", ValueType.FLOAT, Value.of(12f)),
                        prop("width", ValueType.FLOAT, Value.of(0.045f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.025f)),
                        prop("color", ValueType.COLOR, Value.color(0.66f, 0.9f, 1f, 0.98f))
                )));
        blocks.register("vfx.block.arc_plasma_shell", PlasmaShellBlock::create);

        metadata.register(type("vfx.block.arc_shockwave", "spawn", "Expanding Shock Rings",
                List.of(
                        prop("duration", ValueType.FLOAT, Value.of(1.25f)),
                        prop("base_radius", ValueType.FLOAT, Value.of(1.5f)),
                        prop("max_radius", ValueType.FLOAT, Value.of(28f)),
                        prop("ring_count", ValueType.INT, Value.of(5)),
                        prop("segments", ValueType.INT, Value.of(80)),
                        prop("width", ValueType.FLOAT, Value.of(0.085f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.075f)),
                        prop("color", ValueType.COLOR, Value.color(0.62f, 0.66f, 0.72f, 0.72f))
                )));
        blocks.register("vfx.block.arc_shockwave", ArcEmitterBlocks::shockwave);

        metadata.register(type("vfx.block.arc_radial_ripple", "spawn", "Concentric Radial Ripple",
                List.of(
                        prop("duration", ValueType.FLOAT, Value.of(1f)),
                        prop("duration_param", ValueType.STRING, Value.string("")),
                        prop("intensity", ValueType.FLOAT, Value.of(1f)),
                        prop("intensity_param", ValueType.STRING, Value.string("")),
                        prop("radius", ValueType.FLOAT, Value.of(1.5f)),
                        prop("ring_count", ValueType.INT, Value.of(8)),
                        prop("segments", ValueType.INT, Value.of(32)),
                        prop("width_scale", ValueType.FLOAT, Value.of(0.48f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.075f)),
                        prop("core_color", ValueType.COLOR, Value.color(0.5f, 0.2f, 0.8f, 0.7f)),
                        prop("core_color_param", ValueType.STRING, Value.string("")),
                        prop("edge_color", ValueType.COLOR, Value.color(0.1f, 0f, 0.3f, 0f)),
                        prop("edge_color_param", ValueType.STRING, Value.string(""))
                )));
        blocks.register("vfx.block.arc_radial_ripple", ArcEmitterBlocks::radialRipple);

        metadata.register(type("vfx.block.arc_collapsing_box", "spawn", "Collapsing Wireframe Box",
                List.of(
                        prop("progress_param", ValueType.STRING, Value.string("progress")),
                        prop("width_param", ValueType.STRING, Value.string("width")),
                        prop("height_param", ValueType.STRING, Value.string("height")),
                        prop("yaw_param", ValueType.STRING, Value.string("yaw")),
                        prop("width", ValueType.FLOAT, Value.of(1f)),
                        prop("height", ValueType.FLOAT, Value.of(2f)),
                        prop("yaw", ValueType.FLOAT, Value.of(0f)),
                        prop("collapse_degrees", ValueType.FLOAT, Value.of(81f)),
                        prop("line_width", ValueType.FLOAT, Value.of(0.02f)),
                        prop("lifetime", ValueType.FLOAT, Value.of(0.075f)),
                        prop("color", ValueType.COLOR, Value.color(0.8f, 0.85f, 1f, 1f))
                )));
        blocks.register("vfx.block.arc_collapsing_box", ArcEmitterBlocks::collapsingBox);

        metadata.register(type("vfx.block.arc_surface", "spawn", "Arc Surface",
                ARC_SURFACE_PROPS));
        blocks.register("vfx.block.arc_surface", SurfaceArcBlocks::surface);

        metadata.register(type("vfx.block.arc_contact", "spawn", "Arc Contact",
                Stream.concat(ARC_SURFACE_PROPS.stream(), ARC_CONTACT_PROPS.stream()).toList()));
        blocks.register("vfx.block.arc_contact", SurfaceArcBlocks::contact);

        metadata.register(type("vfx.block.arc_spark", "spawn", "Arc Spark",
                ARC_SPARK_PROPS));
        blocks.register("vfx.block.arc_spark", SurfaceArcBlocks::spark);

        metadata.register(type("vfx.block.output_arc", "output", "Output Arc",
                Stream.concat(OUTPUT_PROPERTIES.stream(), ARC_OUTPUT_PROPERTIES.stream()).toList()));
        blocks.register("vfx.block.output_arc", OutputBlocks::noop);
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
