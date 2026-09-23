package org.academy.api.client.render.vfxgraph.nodes;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Mth;
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
import org.academy.api.common.ability.VortexAttackPattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class VfxBlocks {
    private static final AtomicLong NEXT_TRANSIENT_ARC_GROUP = new AtomicLong(1L);

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
                List.of(prop("cx", ValueType.FLOAT, Value.of(0f)), prop("cy", ValueType.FLOAT, Value.of(0f)),
                        prop("cz", ValueType.FLOAT, Value.of(0f)), prop("strength", ValueType.FLOAT, Value.of(1f)),
                        prop("pull", ValueType.FLOAT, Value.of(0f)),
                        prop("vertical_pull", ValueType.FLOAT, Value.of(0f)),
                        prop("layer", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.update_vortex", VfxBlocks::updateVortex);

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
        blocks.register("vfx.block.update_follow", VfxBlocks::updateFollow);

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
        blocks.register("vfx.block.update_live", VfxBlocks::updateLive);

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


        metadata.register(type("vfx.block.life_color", "over-life", "Color Over Lifetime",
                List.of(prop("gradient", ValueType.STRING, Value.string("")), prop("layer", ValueType.STRING, Value.string("")))));
        blocks.register("vfx.block.life_color", VfxBlocks::lifeColor);

        metadata.register(type("vfx.block.life_alpha", "over-life", "Alpha Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_alpha", VfxBlocks::lifeAlpha);

        metadata.register(type("vfx.block.life_size", "over-life", "Size Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_size", VfxBlocks::lifeSize);

        metadata.register(type("vfx.block.life_velocity", "over-life", "Velocity Over Lifetime", CURVE_LAYER));
        blocks.register("vfx.block.life_velocity", VfxBlocks::lifeVelocity);


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
            byte filter = layerFilter(n);
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        });

        metadata.register(type("vfx.block.output_ribbon", "output", "Output Ribbon", OUTPUT_PROPERTIES));
        blocks.register("vfx.block.output_ribbon", (n, p) -> (buf, ctx) -> {
            byte filter = layerFilter(n);
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        });


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
        blocks.register("vfx.block.vortex_jet", VfxBlocks::vortexJet);

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
        blocks.register("vfx.block.arc_tornado", VfxBlocks::arcTornado);

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
        blocks.register("vfx.block.tornado_volume", VfxBlocks::tornadoVolume);

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
        blocks.register("vfx.block.plasma_convergence", VfxBlocks::plasmaConvergence);

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
        blocks.register("vfx.block.arc_plasma_shell", VfxBlocks::arcPlasmaShell);

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
        blocks.register("vfx.block.arc_shockwave", VfxBlocks::arcShockwave);

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
        blocks.register("vfx.block.arc_radial_ripple", VfxBlocks::arcRadialRipple);

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
        blocks.register("vfx.block.arc_collapsing_box", VfxBlocks::arcCollapsingBox);

        metadata.register(type("vfx.block.arc_surface", "spawn", "Arc Surface",
                ARC_SURFACE_PROPS));
        blocks.register("vfx.block.arc_surface", VfxBlocks::arcSurface);

        metadata.register(type("vfx.block.arc_contact", "spawn", "Arc Contact",
                Stream.concat(ARC_SURFACE_PROPS.stream(), ARC_CONTACT_PROPS.stream()).toList()));
        blocks.register("vfx.block.arc_contact", VfxBlocks::arcContact);

        metadata.register(type("vfx.block.arc_spark", "spawn", "Arc Spark",
                ARC_SPARK_PROPS));
        blocks.register("vfx.block.arc_spark", VfxBlocks::arcSpark);

        metadata.register(type("vfx.block.output_arc", "output", "Output Arc",
                Stream.concat(OUTPUT_PROPERTIES.stream(), ARC_OUTPUT_PROPERTIES.stream()).toList()));
        blocks.register("vfx.block.output_arc", (n, p) -> (buf, ctx) -> {
        });
    }


    private static SimNode spawnRate(VfxBlock block, PortValueSource ports) {
        float lifetime = propFloat(block, "lifetime", 1f);
        float size = propFloat(block, "size", 0.1f);
        float[] color = propColor(block, "color");
        float vx = propFloat(block, "vx", 0f);
        float vy = propFloat(block, "vy", 0f);
        float vz = propFloat(block, "vz", 0f);
        byte layer = layerOf(block);
        float rate = propFloat(block, "rate", 10f);
        String positionParam = propString(block, "position_param", "");
        EmitterShape shape = buildShape(block);
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
                applyPositionParam(p, positionParam, ctx);
                int i = buf.spawn();
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
        int count = propInt(block, "count", 10);
        float lifetime = propFloat(block, "lifetime", 1f);
        float size = propFloat(block, "size", 0.1f);
        float[] color = propColor(block, "color");
        float vx = propFloat(block, "vx", 0f);
        float vy = propFloat(block, "vy", 0f);
        float vz = propFloat(block, "vz", 0f);
        byte layer = layerOf(block);
        String positionParam = propString(block, "position_param", "");
        EmitterShape shape = buildShape(block);
        boolean[] fired = {false};
        return (buf, ctx) -> {
            if (fired[0]) return;
            fired[0] = true;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                shape.sample(ctx.random(), p);
                applyPositionParam(p, positionParam, ctx);
                int i = buf.spawn();
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
        int count = propInt(block, "count", 5);
        float interval = propFloat(block, "interval", 1f);
        float lifetime = propFloat(block, "lifetime", 1f);
        float size = propFloat(block, "size", 0.1f);
        float[] color = propColor(block, "color");
        float vx = propFloat(block, "vx", 0f);
        float vy = propFloat(block, "vy", 0f);
        float vz = propFloat(block, "vz", 0f);
        byte layer = layerOf(block);
        String positionParam = propString(block, "position_param", "");
        EmitterShape shape = buildShape(block);
        float[] acc = {0f};
        return (buf, ctx) -> {
            acc[0] += ctx.dt();
            if (acc[0] < interval) return;
            acc[0] = 0f;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                shape.sample(ctx.random(), p);
                applyPositionParam(p, positionParam, ctx);
                int i = buf.spawn();
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
        float rate = propFloat(block, "rate", 5f);
        float speed = propFloat(block, "speed", 1f);
        float lifetime = propFloat(block, "lifetime", 1f);
        float size = propFloat(block, "size", 0.1f);
        float[] color = propColor(block, "color");
        float vx = propFloat(block, "vx", 0f);
        float vy = propFloat(block, "vy", 0f);
        float vz = propFloat(block, "vz", 0f);
        byte layer = layerOf(block);
        String positionParam = propString(block, "position_param", "");
        EmitterShape shape = buildShape(block);
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
                applyPositionParam(p, positionParam, ctx);
                int i = buf.spawn();
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


    private static SimNode initPosition(VfxBlock block, PortValueSource ports) {
        EmitterShape shape = buildShape(block);
        String positionParam = propString(block, "position_param", "");
        return (buf, ctx) -> {
            var p = new float[3];
            ctx.forEachIncoming(i -> {
                shape.sample(ctx.random(), p);
                applyPositionParam(p, positionParam, ctx);
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
        float cx = propFloat(block, "cx", 0f);
        float cy = propFloat(block, "cy", 0f);
        float cz = propFloat(block, "cz", 0f);
        float strength = propFloat(block, "strength", 1f);
        float pull = propFloat(block, "pull", 0f);
        float verticalPull = propFloat(block, "vertical_pull", 0f);
        byte layerFilter = ParticleBuffer.layerFilter(propString(block, "layer", ""));
        return (buf, ctx) -> {
            float dt = ctx.dt();
            for (int i = 0; i < buf.count(); i++) {
                if (layerFilter >= 0 && buf.layer(i) != layerFilter) continue;
                float dx = buf.positionX(i) - cx;
                float dz = buf.positionZ(i) - cz;
                float r2 = dx * dx + dz * dz;
                if (r2 < 1e-6f) continue;
                float inv = 1f / (float) Math.sqrt(r2);
                float radialX = dx * inv;
                float radialZ = dz * inv;
                float vx = buf.velocityX(i);
                float vz = buf.velocityZ(i);
                float radialSpeed = vx * radialX + vz * radialZ;
                float targetRadialSpeed = -pull;
                if (radialSpeed > targetRadialSpeed) {
                    float correction = radialSpeed - targetRadialSpeed;
                    vx -= radialX * correction;
                    vz -= radialZ * correction;
                }
                buf.setVelocity(i,
                        vx - radialZ * strength * dt,
                        buf.velocityY(i) + (cy - buf.positionY(i)) * verticalPull * dt,
                        vz + radialX * strength * dt);
            }
        };
    }

    private static SimNode updateFollow(VfxBlock block, PortValueSource ports) {
        String positionParam = propString(block, "position_param", "projectile_position");
        String directionParam = propString(block, "direction_param", "projectile_direction");
        byte filter = layerFilter(block);
        float orbitRadius = propFloat(block, "orbit_radius", 0f);
        float orbitSpeed = propFloat(block, "orbit_speed", 0f);
        float phase = propFloat(block, "phase", 0f);
        float backOffset = propFloat(block, "back_offset", 0f);
        String sizeParam = propString(block, "size_param", "");
        float sizeMin = propFloat(block, "size_min", 1f);
        float sizeMax = propFloat(block, "size_max", sizeMin);
        float sizePower = Math.max(0.05f, propFloat(block, "size_power", 1f));
        return (buf, ctx) -> {
            float px = ctx.paramVec3(positionParam, 0, 0f);
            float py = ctx.paramVec3(positionParam, 1, 0f);
            float pz = ctx.paramVec3(positionParam, 2, 0f);
            float fx = ctx.paramVec3(directionParam, 0, 0f);
            float fy = ctx.paramVec3(directionParam, 1, 0f);
            float fz = ctx.paramVec3(directionParam, 2, 1f);
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (fl < 1e-5f) {
                fx = 0f;
                fy = 0f;
                fz = 1f;
            } else {
                fx /= fl;
                fy /= fl;
                fz /= fl;
            }

            float rx = -fz;
            float ry = 0f;
            float rz = fx;
            float rl = (float) Math.sqrt(rx * rx + rz * rz);
            if (rl < 1e-4f) {
                rx = 1f;
                rz = 0f;
            } else {
                rx /= rl;
                rz /= rl;
            }
            float ux = ry * fz - rz * fy;
            float uy = rz * fx - rx * fz;
            float uz = rx * fy - ry * fx;
            float sizeT = (float) Math.pow(clamp01(ctx.paramFloat(sizeParam, 0f)), sizePower);
            float liveSize = lerp(sizeMin, sizeMax, smoothstep(sizeT));
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                float angle = ctx.time() * orbitSpeed + phase + buf.seed(i) * 2.3999632f;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                buf.setPosition(i,
                        px - fx * backOffset + orbitRadius * (rx * cos + ux * sin),
                        py - fy * backOffset + orbitRadius * (ry * cos + uy * sin),
                        pz - fz * backOffset + orbitRadius * (rz * cos + uz * sin));
                if (!sizeParam.isEmpty()) {
                    buf.setSize(i, liveSize);
                }
            }
        };
    }

    private static SimNode updateLive(VfxBlock block, PortValueSource ports) {
        byte filter = layerFilter(block);
        String sizeParam = propString(block, "size_param", "");
        float sizeScale = propFloat(block, "size_scale", 1f);
        String alphaParam = propString(block, "alpha_param", "");
        float alphaScale = propFloat(block, "alpha_scale", 1f);
        String rotationParam = propString(block, "rotation_param", "");
        float rotationScale = propFloat(block, "rotation_scale", 1f);
        return (buf, ctx) -> {
            float liveSize = Math.max(0f, ctx.paramFloat(sizeParam, 1f) * sizeScale);
            float liveAlpha = clamp01(ctx.paramFloat(alphaParam, 1f) * alphaScale);
            float liveRotation = ctx.paramFloat(rotationParam, 0f) * rotationScale;
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                if (!sizeParam.isEmpty()) {
                    buf.setSize(i, liveSize);
                }
                if (!alphaParam.isEmpty()) {
                    buf.setAlpha(i, liveAlpha);
                }
                if (!rotationParam.isEmpty()) {
                    buf.setRotation(i, liveRotation);
                }
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
        var branchLengthScaleParam = propString(block, "branch_length_scale_param", "");
        var branchWidthScale = propFloat(block, "branch_width_scale", 0.35f);
        var branchBrightnessScale = propFloat(block, "branch_brightness_scale", 0.6f);
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
        var interval = propFloat(block, "interval", 0f);
        float[] accumulator = {0f};
        var reshuffle = propFloat(block, "reshuffle", 0f);
        long[] transientGroup = {NEXT_TRANSIENT_ARC_GROUP.getAndIncrement()};
        float[] carryAge = {0f};
        return (buf, ctx) -> {
            var bls = branchLengthScaleParam.isEmpty()
                    ? branchLengthScale
                    : ctx.paramFloat(branchLengthScaleParam, branchLengthScale);
            if (reshuffle > 0f) {
                accumulator[0] += ctx.dt();
                if (accumulator[0] < reshuffle) return;
                accumulator[0] = 0f;
                if (carryAge[0] >= 0f && carryAge[0] < lifetime) {
                    carryAge[0] += reshuffle;
                    if (carryAge[0] >= lifetime) {
                        ctx.arcs().removeGroup(transientGroup[0]);
                        carryAge[0] = Float.NaN;
                    } else {
                        ctx.arcs().removeGroup(transientGroup[0]);
                        seed[0]++;
                        var arc = ctx.arcs().add(transientGroup[0]);
                        arc.setAge(carryAge[0]);
                        CurveGenerator.generateFromTo(
                                arc, fromX, fromY, fromZ, toX, toY, toZ,
                                nx, ny, nz,
                                width, segments,
                                color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                                lifetime, seed[0],
                                branchDepth, branchCount, branchAngle,
                                bls, branchWidthScale, branchBrightnessScale);
                    }
                } else {
                    if (ctx.random().nextFloat() > probability) return;
                    ctx.arcs().removeGroup(transientGroup[0]);
                    carryAge[0] = 0f;
                    seed[0]++;
                    var arc = ctx.arcs().add(transientGroup[0]);
                    arc.setAge(0f);
                    CurveGenerator.generateFromTo(
                            arc, fromX, fromY, fromZ, toX, toY, toZ,
                            nx, ny, nz,
                            width, segments,
                            color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                            lifetime, seed[0],
                            branchDepth, branchCount, branchAngle,
                            bls, branchWidthScale, branchBrightnessScale);
                }
                return;
            }
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
                    bls, branchWidthScale, branchBrightnessScale);
        };
    }

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

    private static SimNode arcTornado(VfxBlock block, PortValueSource ports) {
        String expandParam = propString(block, "expand_param", "expand_rate");
        String chargeParam = propString(block, "charge_param", "charge_progress");
        String emissionParam = propString(block, "emission_param", "emission");
        String focusParam = propString(block, "focus_param", "focus_offset");
        float expandMin = propFloat(block, "expand_min", 0.5f);
        float expandMax = Math.max(expandMin + 0.001f, propFloat(block, "expand_max", 3f));
        float bottomRadius = Math.max(0.1f, propFloat(block, "bottom_radius", 15f));
        float baseRadius = propFloat(block, "base_radius", 15f);
        float maxRadius = propFloat(block, "max_radius", 96f);
        float baseHeight = propFloat(block, "base_height", 32f);
        float maxHeight = propFloat(block, "max_height", 128f);
        int minRings = propInt(block, "min_rings", 9);
        int maxRings = propInt(block, "max_rings", 30);
        int minHelices = Math.max(0, propInt(block, "min_helices", 1));
        int maxHelices = Math.max(minHelices, propInt(block, "max_helices", 3));
        int ringSegments = Math.max(16, propInt(block, "ring_segments", 88));
        int helixSegments = Math.max(32, propInt(block, "helix_segments", 224));
        float helixTurns = propFloat(block, "helix_turns", 8.5f);
        float width = propFloat(block, "width", 0.12f);
        float rotationMin = propFloat(block, "rotation_min", 0.82f);
        float rotationMax = propFloat(block, "rotation_max", 5.2f);
        float irregularity = Math.max(0f, propFloat(block, "irregularity", 0.095f));
        float centerWander = Math.max(0f, propFloat(block, "center_wander", 0.055f));
        float fragmentation = clamp01(propFloat(block, "fragmentation", 0.22f));
        float tilt = Math.max(0f, propFloat(block, "tilt", 0.075f));
        float nestedRadius = Math.clamp(propFloat(block, "nested_radius", 0.54f), 0.1f, 0.9f);
        float nestedWidth = Math.max(0f, propFloat(block, "nested_width", 0.72f));
        float collapseStart = Math.clamp(
                propFloat(block, "collapse_start", 0.8f), 0f, 0.99f);
        float collapseEnd = Math.clamp(
                propFloat(block, "collapse_end", 0.94f), collapseStart + 0.01f, 1f);
        float lifetime = propFloat(block, "lifetime", 0.075f);
        float[] dark = propColor(block, "color_dark");
        float[] light = propColor(block, "color_light");
        float[] edge = propColor(block, "color_edge");
        long[] seed = {0L};
        return (buf, ctx) -> {
            float progress = clamp01(ctx.paramFloat(chargeParam, 0f));
            float expandRate = Math.max(expandMin, Math.min(expandMax,
                    ctx.paramFloat(expandParam, expandMin)));
            float expansion = clamp01((expandRate - expandMin) / (expandMax - expandMin));
            float collapse = smoothstep(clamp01((progress - collapseStart) / (collapseEnd - collapseStart)));
            float collapseEase = collapse * collapse;
            float emission = clamp01(ctx.paramFloat(emissionParam, 1f));
            if (emission <= 0.001f || collapseEase >= 0.9995f) return;

            float radius = lerp(baseRadius, maxRadius, expansion);
            float height = lerp(baseHeight, maxHeight, expansion);
            int ringCount = Math.max(2, Math.round(lerp(minRings, maxRings, expansion)));
            int helixCount = Math.max(0, Math.round(lerp(minHelices, maxHelices, expansion)));
            float rotationSpeed = lerp(rotationMin, rotationMax, expansion) * (1f + collapse * 2.65f);
            float rotation = ctx.time() * rotationSpeed;
            float focusX = ctx.paramVec3(focusParam, 0, 0f);
            float focusY = ctx.paramVec3(focusParam, 1, 31f);
            float focusZ = ctx.paramVec3(focusParam, 2, 6f);
            float brightness = emission * lerp(0.54f, 1f, expansion);
            float liveWidth = width * lerp(0.72f, 1.18f, expansion) * (1f + collapse * 0.18f);

            for (int ring = 0; ring < ringCount; ring++) {
                float u = ringCount == 1 ? 0f : (float) ring / (ringCount - 1);
                float layerJitter = stableWave(ring, 1.71f) * height / Math.max(2f, ringCount) * 0.42f;
                float ringRadius = lerp(bottomRadius, radius, (float) Math.pow(u, 0.62));
                float ringY = Math.max(0f, Math.min(height, u * height + layerJitter));
                float ringPhase = rotation * (0.66f + u * 0.62f + stableWave(ring, 3.2f) * 0.06f)
                        + ring * 0.47f;
                float wander = ringRadius * centerWander * (0.35f + u * 0.65f);
                float centerX = (float) Math.sin(ring * 1.37f + ctx.time() * 0.19f) * wander;
                float centerZ = (float) Math.cos(ring * 1.91f - ctx.time() * 0.15f) * wander;
                float ellipse = 1f + stableWave(ring, 5.43f) * irregularity * 0.55f;
                float tiltX = stableWave(ring, 6.31f) * tilt;
                float tiltZ = stableWave(ring, 9.17f) * tilt;
                float ringWidthScale = 0.74f + stableUnit(ring, 4.29f) * 0.52f;
                float[] color = ring % 5 == 4 ? edge : ring % 2 == 0 ? light : dark;
                boolean fragmented = stableUnit(ring, 8.17f) < fragmentation;
                int fragmentCount = fragmented ? 2 + (ring % 4 == 3 ? 1 : 0) : 1;
                for (int fragment = 0; fragment < fragmentCount; fragment++) {
                    float startAngle;
                    float span;
                    if (fragmentCount == 1) {
                        startAngle = 0f;
                        span = (float) (Math.PI * 2.0);
                    } else {
                        startAngle = (float) (Math.PI * 2.0 * fragment / fragmentCount)
                                + stableWave(ring * 7 + fragment, 2.93f) * 0.38f;
                        float baseSpan = (float) (Math.PI * 2.0 / fragmentCount) * 0.78f;
                        span = baseSpan * (0.82f + stableUnit(ring * 11 + fragment, 4.61f) * 0.34f);
                    }
                    int points = Math.max(12, Math.round(ringSegments * span / (float) (Math.PI * 2.0)));
                    var arc = ctx.arcs().add();
                    for (int point = 0; point <= points; point++) {
                        float t = (float) point / points;
                        float angle = startAngle + span * t + ringPhase;
                        float harmonic = (float) Math.sin(angle * 2f + ring * 1.13f) * 0.48f
                                + (float) Math.sin(angle * 5f - ring * 0.73f) * 0.31f
                                + (float) Math.sin(angle * 9f + ring * 2.07f) * 0.16f;
                        float ripple = 1f + irregularity * harmonic;
                        float x = centerX + (float) Math.cos(angle) * ringRadius * ripple * ellipse;
                        float z = centerZ + (float) Math.sin(angle) * ringRadius * ripple / ellipse;
                        float y = ringY
                                + (float) Math.cos(angle) * ringRadius * tiltX
                                + (float) Math.sin(angle) * ringRadius * tiltZ
                                + (float) Math.sin(angle + ring * 0.83f) * ringRadius * irregularity * 0.13f
                                + (float) Math.sin(angle * 3f - ringPhase) * 0.09f * (1f + u);
                        float widthRipple = 0.88f + 0.12f
                                * (float) Math.sin(angle * 2f + ring * 1.61f);
                        float pointWidth = liveWidth * ringWidthScale * widthRipple
                                * (0.88f + 0.12f * (float) Math.sin(t * Math.PI));
                        arc.addPoint(
                                lerp(x, focusX, collapseEase),
                                lerp(y, focusY, collapseEase),
                                lerp(z, focusZ, collapseEase),
                                pointWidth, 0f);
                    }
                    configureCleanArc(arc, color, brightness, lifetime, ++seed[0]);
                }

                if (nestedWidth > 0.001f) {
                    float innerRadius = ringRadius * nestedRadius
                            * (0.96f + stableWave(ring, 7.77f) * irregularity * 0.22f);
                    var innerArc = ctx.arcs().add();
                    for (int point = 0; point <= ringSegments; point++) {
                        float t = (float) point / ringSegments;
                        float angle = t * (float) (Math.PI * 2.0) + ringPhase + 0.27f;
                        float ripple = 1f + irregularity * 0.62f
                                * ((float) Math.sin(angle * 3f - ring * 0.91f) * 0.7f
                                + (float) Math.sin(angle * 7f + ring * 1.37f) * 0.3f);
                        float x = centerX * 0.72f + (float) Math.cos(angle) * innerRadius * ripple * ellipse;
                        float z = centerZ * 0.72f + (float) Math.sin(angle) * innerRadius * ripple / ellipse;
                        float y = ringY
                                + (float) Math.cos(angle) * innerRadius * tiltX
                                + (float) Math.sin(angle) * innerRadius * tiltZ
                                + (float) Math.sin(angle * 2f + ring) * innerRadius * irregularity * 0.08f;
                        innerArc.addPoint(
                                lerp(x, focusX, collapseEase),
                                lerp(y, focusY, collapseEase),
                                lerp(z, focusZ, collapseEase),
                                liveWidth * ringWidthScale * nestedWidth
                                        * (0.9f + 0.1f * (float) Math.sin(angle * 3f)),
                                0f);
                    }
                    configureCleanArc(innerArc, dark, brightness * 0.62f, lifetime, ++seed[0]);
                }
            }

            for (int helix = 0; helix < helixCount; helix++) {
                float phase = rotation * (1.08f + stableWave(helix, 7.11f) * 0.09f)
                        + (float) (Math.PI * 2.0 * helix / helixCount);
                var arc = ctx.arcs().add();
                for (int point = 0; point <= helixSegments; point++) {
                    float u = (float) point / helixSegments;
                    float localRadius = lerp(bottomRadius, radius, (float) Math.pow(u, 0.64));
                    float radialNoise = 1f + irregularity * (
                            (float) Math.sin(u * 17f + helix * 2.1f) * 0.52f
                                    + (float) Math.sin(u * 43f - helix * 1.3f) * 0.22f);
                    float angle = phase + u * helixTurns * (float) (Math.PI * 2.0)
                            + irregularity * 1.35f * (float) Math.sin(u * 11f + helix);
                    float helixWander = localRadius * centerWander * 0.58f;
                    float x = (float) Math.cos(angle) * localRadius * radialNoise
                            + (float) Math.sin(u * 8f + helix) * helixWander;
                    float y = u * height
                            + (float) Math.sin(u * 19f + helix * 1.7f) * height * irregularity * 0.012f;
                    float z = (float) Math.sin(angle) * localRadius * radialNoise
                            + (float) Math.cos(u * 7f - helix) * helixWander;
                    arc.addPoint(
                            lerp(x, focusX, collapseEase),
                            lerp(y, focusY, collapseEase),
                            lerp(z, focusZ, collapseEase),
                            liveWidth * 0.34f * (1.04f + 0.12f * (float) Math.sin(u * Math.PI)), 0f);
                }
                float[] color = helix % 3 == 2 ? edge : light;
                configureCleanArc(arc, color, brightness * 1.08f, lifetime, ++seed[0]);
            }
        };
    }

    private static SimNode vortexJet(VfxBlock block, PortValueSource ports) {
        float side = propFloat(block, "side", 1f) < 0f ? -1f : 1f;
        float root = propFloat(block, "root_x", 0.18f);
        float length = Math.max(0.1f, propFloat(block, "length", 5.2f));
        float rise = propFloat(block, "rise", 3f);
        float back = propFloat(block, "back", 2.5f);
        float radius = Math.max(0.01f, propFloat(block, "radius", 0.65f));
        float turns = propFloat(block, "turns", 5.5f);
        float speed = propFloat(block, "speed", 8f);
        float phase = propFloat(block, "phase", 0f);
        int filaments = Math.clamp(propInt(block, "filaments", 18), 3, 48);
        int segments = Math.clamp(propInt(block, "segments", 100), 24, 192);
        int flecks = Math.clamp(propInt(block, "flecks", 24), 0, 96);
        int highlights = Math.clamp(propInt(block, "highlight_count", 10), 0, 24);
        float highlightSpeed = Math.max(0f, propFloat(block, "highlight_speed", 0.22f));
        float[] highlightColor = propColor(block, "highlight_color");
        float hollow = clamp01(propFloat(block, "hollow", 0.8f));
        String sweepParam = propString(block, "sweep_param", "sweep_right");
        String pitchParam = propString(block, "pitch_param", "pitch_right");
        float[] color = propColor(block, "color");
        long group = NEXT_TRANSIENT_ARC_GROUP.getAndIncrement();
        var point = new Vector3f();
        var spine = new Vector3f();
        var surfaceNormal = new Vector3f();
        var rotation = new Quaternionf();
        var shape = new VortexAttackGeometry();
        var bodyOcclusion = new FirstPersonBodyOcclusion();
        return (buf, ctx) -> {
            ctx.arcs().removeGroup(group);
            float radial = Math.max(0f, ctx.paramFloat("radial_scale", 1f));
            float axial = Math.max(0f, ctx.paramFloat("length_scale", 1f));
            float alpha = clamp01(ctx.paramFloat("opacity", 1f));
            float spread = Math.max(0f, ctx.paramFloat("spread_scale", 1f));
            if (radial < 0.001f || axial < 0.001f || alpha < 0.001f) return;
            float time = ctx.paramFloat("vortex_time", ctx.time()) + phase;
            int liveSegments = Math.clamp(Math.round(ctx.paramFloat("vortex_segments", segments)), 24, segments);
            int liveFilaments = Math.clamp(Math.round(ctx.paramFloat("vortex_filaments", filaments)), 3, filaments);
            int liveFlecks = Math.clamp(Math.round(ctx.paramFloat("vortex_flecks", flecks)), 0, flecks);
            int tubeSegments = Math.clamp(Math.round(ctx.paramFloat("vortex_rings", 8)), 3, 8);
            rotation.identity().rotateY(ctx.paramFloat(sweepParam, 0f) * Mth.DEG_TO_RAD)
                    .rotateZ(ctx.paramFloat(pitchParam, 0f) * Mth.DEG_TO_RAD);
            int attackMode = Math.clamp(Math.round(ctx.paramFloat("attack_mode", 0f)), 0, 5);
            float attackProgress = ctx.paramFloat("attack_progress", 1f);
            boolean clipBody = ctx.paramFloat("first_person_body_clip", 0f) > 0.5f && attackMode == 0;
            if (clipBody) {
                bodyOcclusion.eye.set(ctx.paramVec3("body_clip_eye", 0, 0), ctx.paramVec3("body_clip_eye", 1, 0), ctx.paramVec3("body_clip_eye", 2, 0));
                bodyOcclusion.min.set(ctx.paramVec3("body_clip_min", 0, 0), ctx.paramVec3("body_clip_min", 1, 0), ctx.paramVec3("body_clip_min", 2, 0));
                bodyOcclusion.max.set(ctx.paramVec3("body_clip_max", 0, 0), ctx.paramVec3("body_clip_max", 1, 0), ctx.paramVec3("body_clip_max", 2, 0));
            }
            if (attackProgress < 0f) {
                float duration = VortexAttackPattern.byId(attackMode).durationSeconds();
                attackProgress = (ctx.time() % duration) / duration;
            }
            if (attackMode == 4 && side > 0f || attackMode == 5 && side < 0f) attackMode = 0;
            int branchCount = attackMode == 3 && attackProgress > 0f && attackProgress < 1f ? 2 : 1;
            for (int branch = 0; branch < branchCount; branch++) {
                float targetX = ctx.paramFloat("attack_target_x", 0f);
                float targetY = ctx.paramFloat("attack_target_y", -1.2f);
                float targetZ = ctx.paramFloat("attack_target_z", 12f);
                if (attackMode == 3) {
                    String corner = "attack_corner_" + ((side < 0f ? 0 : 2) + branch);
                    targetX = ctx.paramVec3(corner, 0, side * 6f);
                    targetY = ctx.paramVec3(corner, 1, -1.2f);
                    targetZ = ctx.paramVec3(corner, 2, branch == 0 ? 6f : -6f);
                }
                shape.configure(attackMode, attackProgress, branch,
                        targetX * side - root, targetY, targetZ,
                        length * axial * spread, rise * axial, back * axial,
                        radius * radial, turns, speed, time);
                shape.prepareGrid(liveSegments);
                float branchAlpha = branch == 0 ? 1f : shape.activity();
                if (branchAlpha < 0.001f) continue;
                for (int strand = 0; strand <= liveFilaments; strand++) {
                    boolean core = strand == 0;
                    float strandPhase = strand * 2.399963f + phase;
                    float orbit = core ? 0f : 0.70f + 0.32f * stableUnit(strand, 3.17f);
                    var arc = ctx.arcs().add(group);
                    arc.setMaxTubeSegments(tubeSegments);
                    for (int segment = 0; segment <= liveSegments; segment++) {
                        float u = segment / (float) liveSegments;
                        shape.sample(u, orbit, strandPhase, point);
                        point.x *= side;
                        rotation.transform(point);
                        float localRadius = VortexJetGeometry.radius(u, radius * radial);
                        float width = localRadius * (core ? 0.72f : 0.12f + 0.05f * stableUnit(strand, 6.1f));
                        width *= 0.86f + 0.14f * (float) Math.sin(u * 57f - time * speed + strandPhase);
                        width *= Math.clamp((1f - u) * 28f, 0.015f, 1f) * shape.widthScale(u);
                        boolean hidden = clipBody && bodyOcclusion.occludes(point, side * root);
                        if (hidden) width = 0f;
                        int run = hidden || VortexJetGeometry.hollow(u, time, strand, hollow) ? segment + 1 : 0;
                        arc.addPoint(point.x + side * root, point.y, point.z, width, 0f, run);
                    }
                    float shade = core ? 0.48f : 0.8f + 1.6f * stableUnit(strand, 8.1f);
                    arc.setColor(color[0] * shade, color[1] * shade, color[2] * shade, color[3] * alpha * branchAlpha);
                    arc.setLifetime(1f);
                    arc.setSeed(branch * 10000L + strand + 1);
                    arc.setNoiseStrength(0f);
                    arc.setDriftSpeed(0f);
                }
                for (int highlight = 0; highlight < highlights; highlight++) {
                    int strand = 1 + (highlight * 7) % filaments;
                    if (strand > liveFilaments) continue;
                    float strandPhase = strand * 2.399963f + phase;
                    float orbit = 0.70f + 0.32f * stableUnit(strand, 3.17f);
                    float start = (stableUnit(highlight, 4.71f) + time * highlightSpeed) % 1f;
                    float pulseLength = 1.04f * (0.045f + 0.035f * stableUnit(highlight, 7.31f));
                    float fade = Math.min(1f, start * 16f) * Math.min(1f, (1f - start) * 14f);
                    var arc = ctx.arcs().add(group);
                    arc.setMaxTubeSegments(tubeSegments);
                    for (int j = 0; j <= 12; j++) {
                        float u = Math.min(1f, start + pulseLength * j / 12f);
                        shape.sample(u, orbit, strandPhase, point);
                        float width = VortexJetGeometry.radius(u, radius * radial)
                                * (0.12f + 0.05f * stableUnit(strand, 6.1f));
                        width *= 0.86f + 0.14f * (float) Math.sin(u * 57f - time * speed + strandPhase);
                        width *= Math.clamp((1f - u) * 28f, 0.015f, 1f) * shape.widthScale(u);
                        shape.sample(u, 0f, strandPhase, spine);
                        point.sub(spine, surfaceNormal).normalize();
                        point.fma(width * 0.98f, surfaceNormal);
                        point.x *= side;
                        rotation.transform(point);
                        width = Math.min(width * 0.20f, 0.012f * radial)
                                * (float) Math.sin(Math.PI * j / 12f);
                        boolean hidden = clipBody && bodyOcclusion.occludes(point, side * root);
                        if (hidden) width = 0f;
                        int run = hidden || VortexJetGeometry.hollow(u, time, strand, hollow) ? j + 1 : 0;
                        arc.addPoint(point.x + side * root, point.y, point.z, width, 0f, run);
                    }
                    arc.setColor(highlightColor[0], highlightColor[1], highlightColor[2],
                            highlightColor[3] * alpha * fade * branchAlpha);
                    arc.setLifetime(1f);
                    arc.setSeed(branch * 10000L + 1000 + highlight);
                    arc.setNoiseStrength(0f);
                    arc.setDriftSpeed(0f);
                }
                for (int fleck = 0; fleck < liveFlecks; fleck++) {
                    float u = (stableUnit(fleck, 2.13f) + time * 0.22f) % 1f;
                    float fade = Math.min(1f, u * 12f) * Math.min(1f, (1f - u) * 12f);
                    float orbit = 1.15f + stableUnit(fleck, 9.31f) * 0.6f;
                    var arc = ctx.arcs().add(group);
                    arc.setMaxTubeSegments(tubeSegments);
                    for (int j = 0; j < 3; j++) {
                        float v = Math.min(1f, u + j * 0.006f);
                        shape.sample(v, orbit, fleck * 2.4f, point);
                        point.x *= side;
                        rotation.transform(point);
                        boolean hidden = clipBody && bodyOcclusion.occludes(point, side * root);
                        arc.addPoint(point.x + side * root, point.y, point.z,
                                hidden ? 0f : (j == 1 ? 0.019f : 0.002f) * radial * fade, 0f, hidden ? j + 1 : 0);
                    }
                    arc.setColor(color[0], color[1], color[2], alpha * fade * 0.8f * branchAlpha);
                    arc.setLifetime(1f);
                    arc.setSeed(branch * 10000L + fleck + 100);
                    arc.setNoiseStrength(0f);
                    arc.setDriftSpeed(0f);
                }
            }
        };
    }

    private static SimNode tornadoVolume(VfxBlock block, PortValueSource ports) {
        String expandParam = propString(block, "expand_param", "expand_rate");
        String chargeParam = propString(block, "charge_param", "charge_progress");
        String emissionParam = propString(block, "emission_param", "emission");
        String focusParam = propString(block, "focus_param", "focus_offset");
        float expandMin = propFloat(block, "expand_min", 0.5f);
        float expandMax = Math.max(expandMin + 0.001f, propFloat(block, "expand_max", 3f));
        float bottomRadius = Math.max(0.1f, propFloat(block, "bottom_radius", 15f));
        float baseRadius = Math.max(bottomRadius, propFloat(block, "base_radius", 15f));
        float maxRadius = Math.max(baseRadius, propFloat(block, "max_radius", 96f));
        float baseHeight = Math.max(0.1f, propFloat(block, "base_height", 32f));
        float maxHeight = Math.max(baseHeight, propFloat(block, "max_height", 128f));
        int volumeCount = Math.max(24, propInt(block, "volume_count", 480));
        int dustCount = Math.max(16, propInt(block, "dust_count", 420));
        float volumeSize = Math.max(0.05f, propFloat(block, "volume_size", 5.4f));
        float dustSize = Math.max(0.01f, propFloat(block, "dust_size", 0.8f));
        float vortexStrength = propFloat(block, "rotation_speed", 3.4f);
        float liftStrength = Math.max(0.001f, propFloat(block, "rise_speed", 0.105f));
        float inwardForce = Math.max(0.05f, propFloat(block, "inward_force", 3.2f));
        float turbulence = Math.max(0f, propFloat(block, "turbulence", 0.18f));
        float drag = Math.max(0.05f, propFloat(block, "drag", 2.4f));
        float volumeTurns = propFloat(block, "volume_turns", 3.8f);
        float dustTurns = propFloat(block, "dust_turns", 8.5f);
        float volumeRadiusScale = Math.max(1f, propFloat(block, "volume_radius_scale", 1.45f));
        float dustRadiusScale = Math.max(1f, propFloat(block, "dust_radius_scale", 1.55f));
        float collapseStart = Math.clamp(
                propFloat(block, "collapse_start", 0.8f), 0f, 0.99f);
        float collapseEnd = Math.clamp(
                propFloat(block, "collapse_end", 0.94f), collapseStart + 0.01f, 1f);
        float lifetime = Math.max(1f, propFloat(block, "lifetime", 120f));
        byte volumeLayer = ParticleBuffer.layerByte(propString(block, "volume_layer", "wind_volume"));
        byte dustLayer = ParticleBuffer.layerByte(propString(block, "dust_layer", "wind_dust"));
        float[] volumeColor = propColor(block, "volume_color");
        float[] dustColor = propColor(block, "dust_color");

        float[] volumeHeightState = new float[volumeCount];
        float[] volumeDepthState = new float[volumeCount];
        float[] volumeRadialVelocity = new float[volumeCount];
        float[] volumeLiftVelocity = new float[volumeCount];
        float[] volumeAngleState = new float[volumeCount];
        float[] volumeAngularVelocity = new float[volumeCount];
        float[] dustHeightState = new float[dustCount];
        float[] dustDepthState = new float[dustCount];
        float[] dustRadialVelocity = new float[dustCount];
        float[] dustLiftVelocity = new float[dustCount];
        float[] dustAngleState = new float[dustCount];
        float[] dustAngularVelocity = new float[dustCount];
        boolean[] fired = {false};
        return (buf, ctx) -> {
            float fullTurn = (float) (Math.PI * 2.0);
            if (!fired[0]) {
                fired[0] = true;
                int start = buf.count();
                for (int index = 0; index < volumeCount; index++) {
                    float u = ((index + 0.5f) / volumeCount + stableUnit(index, 7.47f) * 0.08f) % 1f;
                    volumeHeightState[index] = u;
                    volumeDepthState[index] = 0.68f + stableUnit(index, 4.17f) * 0.30f;
                    volumeRadialVelocity[index] = stableWave(index, 5.31f) * 0.025f;
                    volumeLiftVelocity[index] = liftStrength * (0.78f + stableUnit(index, 2.31f) * 0.44f);
                    volumeAngleState[index] = stableUnit(index, 1.19f) * fullTurn + u * volumeTurns * fullTurn;
                    volumeAngularVelocity[index] = vortexStrength * (0.88f + stableUnit(index, 8.03f) * 0.24f);
                    int particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, volumeSize);
                    buf.setColor(particle,
                            volumeColor[0], volumeColor[1], volumeColor[2], volumeColor[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setLayer(particle, volumeLayer);
                }
                for (int index = 0; index < dustCount; index++) {
                    float u = ((index + 0.5f) / dustCount + stableUnit(index, 3.19f) * 0.05f) % 1f;
                    dustHeightState[index] = u;
                    dustDepthState[index] = 0.78f + stableUnit(index, 9.41f) * 0.21f;
                    dustRadialVelocity[index] = stableWave(index, 4.73f) * 0.04f;
                    dustLiftVelocity[index] = liftStrength * (1.72f + stableUnit(index, 6.23f) * 0.62f);
                    dustAngleState[index] = stableUnit(index, 2.67f) * fullTurn + u * dustTurns * fullTurn;
                    dustAngularVelocity[index] = vortexStrength * (1.18f + stableUnit(index, 5.29f) * 0.34f);
                    int particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, dustSize);
                    buf.setColor(particle, dustColor[0], dustColor[1], dustColor[2], dustColor[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setLayer(particle, dustLayer);
                }
                ctx.emitBatch(start, buf.count());
            }

            float expandRate = Math.max(expandMin, Math.min(expandMax,
                    ctx.paramFloat(expandParam, expandMin)));
            float expansion = clamp01((expandRate - expandMin) / (expandMax - expandMin));
            float radius = lerp(baseRadius, maxRadius, expansion);
            float height = lerp(baseHeight, maxHeight, expansion);
            float progress = clamp01(ctx.paramFloat(chargeParam, 0f));
            float collapse = smoothstep(clamp01((progress - collapseStart) / (collapseEnd - collapseStart)));
            float collapseEase = collapse * collapse;
            float emission = clamp01(ctx.paramFloat(emissionParam, 1f));
            float focusX = ctx.paramVec3(focusParam, 0, 0f);
            float focusY = ctx.paramVec3(focusParam, 1, 31f);
            float focusZ = ctx.paramVec3(focusParam, 2, 6f);
            float time = ctx.time();
            float dt = Math.clamp(ctx.dt(), 0f, 0.05f);
            float radialDamping = (float) Math.exp(-drag * dt);

            int volumeIndex = 0;
            for (int particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != volumeLayer) continue;
                int index = volumeIndex++;
                float particleSeed = buf.seed(particle);
                float u = volumeHeightState[index];
                float liftTarget = liftStrength * (0.78f + stableUnit(index, 2.31f) * 0.44f)
                        * (1f + expansion * 0.16f);
                float verticalNoise = (float) Math.sin(time * 0.83f + particleSeed * 1.37f)
                        * turbulence * 0.025f;
                volumeLiftVelocity[index] += ((liftTarget - volumeLiftVelocity[index]) * drag
                        + verticalNoise) * dt;
                u += volumeLiftVelocity[index] * dt;
                if (u >= 1f) {
                    u -= (float) Math.floor(u);
                    volumeDepthState[index] = 0.68f + stableUnit(index + (int) time, 4.17f) * 0.30f;
                    volumeRadialVelocity[index] = stableWave(index + (int) time, 5.31f) * 0.025f;
                }
                volumeHeightState[index] = u;

                float angularTarget = vortexStrength * (1.22f - u * 0.42f)
                        * (0.86f + stableUnit(index, 8.03f) * 0.28f);
                float angularNoise = (float) Math.sin(time * 0.91f + particleSeed * 0.73f)
                        * turbulence * 0.22f;
                volumeAngularVelocity[index] += ((angularTarget - volumeAngularVelocity[index]) * drag * 0.82f
                        + angularNoise) * dt;
                volumeAngleState[index] += volumeAngularVelocity[index] * dt;

                float targetDepth = 0.69f + stableUnit(index, 4.17f) * 0.27f;
                float centrifugal = volumeAngularVelocity[index] * volumeAngularVelocity[index] * 0.0065f;
                float radialNoise = (float) Math.sin(time * 1.07f + particleSeed * 1.91f + u * 11f)
                        * turbulence * 0.18f;
                float radialAcceleration = (targetDepth - volumeDepthState[index]) * inwardForce
                        + centrifugal * (1f - u * 0.38f) + radialNoise;
                volumeRadialVelocity[index] = (volumeRadialVelocity[index] + radialAcceleration * dt)
                        * radialDamping;
                volumeDepthState[index] = Math.clamp(
                        volumeDepthState[index] + volumeRadialVelocity[index] * dt, 0.58f, 1.08f);

                float bezierHeight = u * u * (3f - 2f * u);
                float coneRadius = lerp(bottomRadius, radius, (float) Math.pow(bezierHeight, 0.72f))
                        * volumeRadiusScale;
                float radial = coneRadius * volumeDepthState[index];
                float angle = volumeAngleState[index];
                float wander = coneRadius * 0.045f;
                float x = (float) Math.cos(angle) * radial
                        + (float) Math.sin(time * 0.37f + u * 8f) * wander;
                float y = u * height
                        + (float) Math.sin(angle * 0.46f + particleSeed) * height * 0.012f;
                float z = (float) Math.sin(angle) * radial
                        + (float) Math.cos(time * 0.31f - u * 7f) * wander;
                buf.setPosition(particle,
                        lerp(x, focusX, collapseEase),
                        lerp(y, focusY, collapseEase),
                        lerp(z, focusZ, collapseEase));
                buf.setVelocity(particle,
                        -(float) Math.sin(angle) * volumeAngularVelocity[index] * radial,
                        volumeLiftVelocity[index] * height,
                        (float) Math.cos(angle) * volumeAngularVelocity[index] * radial);
                buf.setRotation(particle, angle + u * 0.7f);
                buf.setAge(particle, time);
                float endFade = smoothstep(clamp01(u / 0.11f))
                        * smoothstep(clamp01((1f - u) / 0.14f));
                float sizeVariation = 0.74f + stableUnit(index, 8.63f) * 0.52f;
                buf.setSizeScaled(particle, buf.startSize(particle) * expandRate * sizeVariation
                        * (0.82f + u * 0.34f) * (1f - collapseEase * 0.58f));
                buf.setAlpha(particle, volumeColor[3] * emission * endFade
                        * (0.62f + u * 0.38f) * (1f - collapseEase));
            }

            int dustIndex = 0;
            for (int particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != dustLayer) continue;
                int index = dustIndex++;
                float particleSeed = buf.seed(particle);
                float u = dustHeightState[index];
                float liftTarget = liftStrength * (1.72f + stableUnit(index, 6.23f) * 0.62f)
                        * (1f + expansion * 0.22f);
                float verticalNoise = (float) Math.sin(time * 1.31f - particleSeed * 0.91f)
                        * turbulence * 0.05f;
                dustLiftVelocity[index] += ((liftTarget - dustLiftVelocity[index]) * drag * 0.9f
                        + verticalNoise) * dt;
                u += dustLiftVelocity[index] * dt;
                if (u >= 1f) {
                    u -= (float) Math.floor(u);
                    dustDepthState[index] = 0.78f + stableUnit(index + (int) time, 9.41f) * 0.21f;
                    dustRadialVelocity[index] = stableWave(index + (int) time, 4.73f) * 0.04f;
                }
                dustHeightState[index] = u;

                float angularTarget = vortexStrength * (1.58f - u * 0.55f)
                        * (0.9f + stableUnit(index, 5.29f) * 0.3f);
                float angularNoise = (float) Math.sin(time * 1.47f + particleSeed * 1.17f)
                        * turbulence * 0.36f;
                dustAngularVelocity[index] += ((angularTarget - dustAngularVelocity[index]) * drag
                        + angularNoise) * dt;
                dustAngleState[index] += dustAngularVelocity[index] * dt;

                float targetDepth = 0.78f + stableUnit(index, 9.41f) * 0.19f;
                float centrifugal = dustAngularVelocity[index] * dustAngularVelocity[index] * 0.0045f;
                float radialNoise = (float) Math.sin(time * 1.79f + particleSeed * 0.63f + u * 17f)
                        * turbulence * 0.28f;
                float radialAcceleration = (targetDepth - dustDepthState[index]) * inwardForce * 1.18f
                        + centrifugal * (1f - u * 0.32f) + radialNoise;
                dustRadialVelocity[index] = (dustRadialVelocity[index] + radialAcceleration * dt)
                        * radialDamping;
                dustDepthState[index] = Math.clamp(
                        dustDepthState[index] + dustRadialVelocity[index] * dt, 0.66f, 1.1f);

                float bezierHeight = u * u * (3f - 2f * u);
                float coneRadius = lerp(bottomRadius * 0.86f, radius * 0.96f,
                        (float) Math.pow(bezierHeight, 0.69f)) * dustRadiusScale;
                float radial = coneRadius * dustDepthState[index];
                float angle = dustAngleState[index];
                float x = (float) Math.cos(angle) * radial;
                float y = u * height
                        + (float) Math.sin(angle * 0.35f + particleSeed) * 0.38f;
                float z = (float) Math.sin(angle) * radial;
                buf.setPosition(particle,
                        lerp(x, focusX, collapseEase),
                        lerp(y, focusY, collapseEase),
                        lerp(z, focusZ, collapseEase));
                buf.setVelocity(particle,
                        -(float) Math.sin(angle) * dustAngularVelocity[index] * radial,
                        dustLiftVelocity[index] * height,
                        (float) Math.cos(angle) * dustAngularVelocity[index] * radial);
                buf.setRotation(particle, angle);
                buf.setAge(particle, time);
                float sizeVariation = 0.58f + stableUnit(index, 5.87f) * 0.82f;
                buf.setSizeScaled(particle, buf.startSize(particle) * expandRate * sizeVariation
                        * (1f - collapseEase * 0.72f));
                float endFade = smoothstep(clamp01(u / 0.075f))
                        * smoothstep(clamp01((1f - u) / 0.11f));
                buf.setAlpha(particle, dustColor[3] * emission * endFade
                        * (0.66f + u * 0.34f) * (1f - collapseEase));
            }
        };
    }

    private static SimNode plasmaConvergence(VfxBlock block, PortValueSource ports) {
        String progressParam = propString(block, "progress_param", "focus_progress");
        String formationParam = propString(block, "formation_param", "");
        int count = Math.max(8, propInt(block, "count", 72));
        int armCount = Math.clamp(propInt(block, "arm_count", 4), 2, 8);
        float turns = Math.max(0.5f, propFloat(block, "turns", 5.5f));
        float stagger = Math.clamp(propFloat(block, "stagger", 0.28f), 0f, 0.48f);
        float angularAcceleration = Math.max(0f, propFloat(block, "angular_acceleration", 6.2f));
        float irregularity = Math.clamp(propFloat(block, "irregularity", 0.09f), 0f, 0.35f);
        float startRadius = Math.max(0f, propFloat(block, "start_radius", 18f));
        float startHeight = Math.max(0f, propFloat(block, "start_height", 12f));
        float endRadius = Math.max(0f, propFloat(block, "end_radius", 0.24f));
        float sizeMin = Math.max(0.01f, propFloat(block, "size_min", 0.16f));
        float sizeMax = Math.max(sizeMin, propFloat(block, "size_max", 0.88f));
        int surfaceBulges = Math.clamp(propInt(block, "surface_bulges", 3), 2, 3);
        float surfaceRadius = Math.max(0.1f, propFloat(block, "surface_radius", 6.65f));
        float surfacePulse = Math.max(0f, propFloat(block, "surface_pulse", 0.42f));
        float lifetime = Math.max(1f, propFloat(block, "lifetime", 120f));
        byte layer = ParticleBuffer.layerByte(propString(block, "layer", "plasma_mote"));
        float[] color = propColor(block, "color");
        boolean[] fired = {false};
        return (buf, ctx) -> {
            if (!fired[0]) {
                fired[0] = true;
                int start = buf.count();
                for (int index = 0; index < count; index++) {
                    int particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, sizeMin);
                    buf.setColor(particle, color[0], color[1], color[2], color[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setAge(particle, 0f);
                    buf.setLayer(particle, layer);
                }
                ctx.emitBatch(start, buf.count());
            }

            float progress = clamp01(ctx.paramFloat(progressParam, 0f));
            float formation = clamp01(ctx.paramFloat(formationParam, progress));
            float surfaceBlend = smoothstep(clamp01((progress - 0.60f) / 0.28f));
            int stepsPerArm = Math.max(1, (count + armCount - 1) / armCount);
            int moteIndex = 0;
            for (int particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != layer) continue;
                int localIndex = moteIndex++;
                float particleSeed = buf.seed(particle);
                int arm = localIndex % armCount;
                int step = localIndex / armCount;
                float pointOffset = 0.32f + stableUnit(localIndex, 9.37f) * 0.36f;
                float pathU = clamp01((step + pointOffset) / stepsPerArm);
                float localProgress = clamp01((progress * (1f + stagger * 2f) - pathU * stagger)
                        / (1f + stagger));
                float convergence = smoothstep(localProgress);
                float radialCollapse = (float) Math.pow(convergence, 1.42f);
                float absorption = smoothstep(clamp01((convergence - 0.80f) / 0.20f));

                float armPhase = arm * (float) (Math.PI * 2.0 / armCount);
                float pathPhase = pathU * turns * (float) (Math.PI * 2.0);
                float seedWander = stableWave(localIndex, 3.71f) * irregularity;
                float spinSpeed = 0.72f + angularAcceleration * convergence * convergence;
                float orbit = armPhase + pathPhase + seedWander
                        + ctx.time() * spinSpeed
                        + convergence * (float) Math.PI * (2.4f + pathU * 1.8f);
                float outerBand = 0.70f + pathU * 0.30f;
                float radiusNoise = 1f + stableWave(localIndex, 5.13f) * irregularity
                        * (1f - convergence * 0.74f);
                float radius = lerp(startRadius * outerBand * radiusNoise,
                        endRadius * (0.72f + stableUnit(localIndex, 7.19f) * 0.56f), radialCollapse);
                float verticalEnvelope = (pathU * 2f - 1f) * startHeight;
                float vertical = verticalEnvelope * (1f - (float) Math.pow(convergence, 0.82f));
                float x = (float) Math.cos(orbit) * radius;
                float z = (float) Math.sin(orbit) * radius;
                float y = vertical + (float) Math.sin(orbit * 1.45f + particleSeed)
                        * radius * irregularity * (1f - convergence * 0.68f);
                boolean surfaceBulge = localIndex < surfaceBulges;
                float bulgeWave = (float) Math.sin(ctx.time() * 0.72f + localIndex * 2.17f);
                if (surfaceBulge) {
                    float bulgeOrbit = ctx.time() * (0.14f + localIndex * 0.018f)
                            + localIndex * (float) (Math.PI * 2.0 / surfaceBulges)
                            + stableWave(localIndex, 8.43f) * 0.42f;
                    float latitude = surfaceBulges == 2
                            ? (localIndex == 0 ? -0.32f : 0.32f)
                            : (localIndex - 1) * 0.39f;
                    float liveSurfaceRadius = lerp(0.18f, surfaceRadius,
                            (float) Math.pow(formation, 2.3));
                    float protrusion = surfacePulse * bulgeWave * (0.28f + formation * 0.72f);
                    float bulgeRadius = Math.max(0.08f, liveSurfaceRadius + protrusion);
                    float latitudeRadius = (float) Math.cos(latitude) * bulgeRadius;
                    float bulgeX = (float) Math.cos(bulgeOrbit) * latitudeRadius;
                    float bulgeY = (float) Math.sin(latitude) * bulgeRadius;
                    float bulgeZ = (float) Math.sin(bulgeOrbit) * latitudeRadius;
                    x = lerp(x, bulgeX, surfaceBlend);
                    y = lerp(y, bulgeY, surfaceBlend);
                    z = lerp(z, bulgeZ, surfaceBlend);
                    orbit = bulgeOrbit;
                }
                buf.setPosition(particle, x, y, z);
                buf.setRotation(particle, orbit);
                float sizeVariation = 0.68f + stableUnit(localIndex, 8.91f) * 0.54f;
                float mergingSize = lerp(sizeMin, sizeMax, (float) Math.pow(convergence, 0.76f))
                        * sizeVariation * (1f - absorption * 0.78f);
                float bulgeSize = lerp(sizeMin * 0.92f, sizeMax * 0.82f, formation)
                        * (1f + bulgeWave * 0.075f);
                float liveSize = surfaceBulge
                        ? lerp(mergingSize, bulgeSize, surfaceBlend)
                        : mergingSize;
                buf.setSizeScaled(particle, liveSize);
                float mergingAlpha = color[3] * (0.58f + convergence * 0.42f) * (1f - absorption);
                float liveAlpha = surfaceBulge
                        ? lerp(mergingAlpha, color[3] * 0.9f, surfaceBlend)
                        : mergingAlpha;
                float heat = convergence * 0.30f;
                buf.setColorRgb(particle,
                        lerp(color[0], 1f, heat),
                        lerp(color[1], 0.82f, heat),
                        lerp(color[2], 1f, heat * 0.72f));
                buf.setAlpha(particle, liveAlpha);
            }
        };
    }

    private static SimNode arcPlasmaShell(VfxBlock block, PortValueSource ports) {
        String positionParam = propString(block, "position_param", "");
        String emissionParam = propString(block, "emission_param", "");
        String progressParam = propString(block, "progress_param", "");
        float radius = propFloat(block, "radius", 8.45f);
        float radiusMin = propFloat(block, "radius_min", 0.42f);
        float radiusMax = propFloat(block, "radius_max", radius);
        float radiusPower = Math.max(0.1f, propFloat(block, "radius_power", 2.3f));
        float duration = propFloat(block, "duration", 0f);
        int count = Math.max(1, propInt(block, "count", 7));
        int segments = Math.max(8, propInt(block, "segments", 22));
        float rotationSpeed = propFloat(block, "rotation_speed", 0.72f);
        float jitter = Math.max(0f, propFloat(block, "jitter", 0.022f));
        float arcSpanMin = Math.max(0.04f, propFloat(block, "arc_span_min", 0.16f));
        float arcSpanMax = Math.max(arcSpanMin, propFloat(block, "arc_span_max", 0.38f));
        float surfaceOffset = Math.max(0f, propFloat(block, "surface_offset", 0.22f));
        float flickerRate = Math.max(1f, propFloat(block, "flicker_rate", 12f));
        float width = propFloat(block, "width", 0.045f);
        float lifetime = propFloat(block, "lifetime", 0.025f);
        float[] color = propColor(block, "color");
        long[] seed = {0L};
        long transientGroup = NEXT_TRANSIENT_ARC_GROUP.getAndIncrement();
        return (buf, ctx) -> {
            ctx.arcs().removeGroup(transientGroup);
            float emission = clamp01(ctx.paramFloat(emissionParam, 1f));
            if (duration > 0f) {
                emission *= 1f - clamp01(ctx.time() / duration);
            }
            if (emission <= 0.001f) return;
            float cx = ctx.paramVec3(positionParam, 0, 0f);
            float cy = ctx.paramVec3(positionParam, 1, 0f);
            float cz = ctx.paramVec3(positionParam, 2, 0f);
            float shellProgress = clamp01(ctx.paramFloat(progressParam, 1f));
            float liveRadius = progressParam.isEmpty()
                    ? radius
                    : lerp(radiusMin, radiusMax, (float) Math.pow(shellProgress, radiusPower));
            float spin = ctx.time() * rotationSpeed;
            float flickerFrame = (float) Math.floor(ctx.time() * flickerRate);
            float shellRadius = liveRadius + surfaceOffset * (0.35f + shellProgress * 0.65f);
            for (int index = 0; index < count; index++) {
                if (hash(index + 0.71f, flickerFrame + 3.19f, 5.83f) < 0.16f) continue;

                float normalY = lerp(-0.82f, 0.82f, hash(index + 1.7f, 19.3f, 2.1f));
                float normalAngle = spin * (0.56f + index * 0.07f)
                        + hash(index + 2.3f, 23.7f, 1.9f) * (float) (Math.PI * 2.0);
                float normalHorizontal = (float) Math.sqrt(1f - normalY * normalY);
                float nx = (float) Math.cos(normalAngle) * normalHorizontal;
                float ny = normalY;
                float nz = (float) Math.sin(normalAngle) * normalHorizontal;
                float horizontalLength = Math.max(1.0e-4f, (float) Math.sqrt(nx * nx + nz * nz));
                float ux = nz / horizontalLength;
                float uy = 0f;
                float uz = -nx / horizontalLength;
                float vx = ny * uz;
                float vy = nz * ux - nx * uz;
                float vz = -ny * ux;
                float spanFraction = lerp(arcSpanMin, arcSpanMax,
                        hash(index + 7.1f, flickerFrame + 2.7f, 11.9f));
                float arcSpan = (float) (Math.PI * 2.0) * spanFraction;
                float startAngle = spin * (1.08f + index * 0.035f)
                        + hash(index + 13.4f, flickerFrame + 0.43f, 17.2f)
                        * (float) (Math.PI * 2.0);
                int liveSegments = Math.max(7, Math.round(segments * spanFraction / arcSpanMax));
                var arc = ctx.arcs().add(transientGroup);
                for (int point = 0; point <= liveSegments; point++) {
                    float u = (float) point / liveSegments;
                    float angle = startAngle + arcSpan * u;
                    float cos = (float) Math.cos(angle);
                    float sin = (float) Math.sin(angle);
                    float qx = ux * cos + vx * sin;
                    float qy = uy * cos + vy * sin;
                    float qz = uz * cos + vz * sin;
                    float tx = -ux * sin + vx * cos;
                    float ty = -uy * sin + vy * cos;
                    float tz = -uz * sin + vz * cos;
                    float tangentJolt = (hash(index * 31.7f + point, flickerFrame * 17.3f, 29.1f) * 2f - 1f)
                            * jitter;
                    float crossJolt = (hash(index * 43.1f + point, flickerFrame * 23.9f, 37.7f) * 2f - 1f)
                            * jitter * 0.82f;
                    float sx = qx + tx * tangentJolt + nx * crossJolt;
                    float sy = qy + ty * tangentJolt + ny * crossJolt;
                    float sz = qz + tz * tangentJolt + nz * crossJolt;
                    float invLength = 1f / Math.max(1.0e-4f, (float) Math.sqrt(sx * sx + sy * sy + sz * sz));
                    float radialCrackle = shellRadius * jitter * 0.055f
                            * (hash(point + 5.2f, index + flickerFrame * 3.1f, 41.3f) * 2f - 1f);
                    float taper = (float) Math.pow(Math.sin(u * Math.PI), 0.34);
                    arc.addPoint(
                            cx + sx * invLength * (shellRadius + radialCrackle),
                            cy + sy * invLength * (shellRadius + radialCrackle),
                            cz + sz * invLength * (shellRadius + radialCrackle),
                            width * (0.18f + taper * 0.82f), 0f);
                }
                float flash = 0.78f + hash(index + 3.6f, flickerFrame + 8.2f, 47.5f) * 0.34f;
                configureCleanArc(arc, color, emission * flash, lifetime, ++seed[0]);
                arc.setNoiseStrength(jitter * 0.32f);
            }
        };
    }

    private static SimNode arcShockwave(VfxBlock block, PortValueSource ports) {
        float duration = propFloat(block, "duration", 1.25f);
        float baseRadius = propFloat(block, "base_radius", 1.5f);
        float maxRadius = propFloat(block, "max_radius", 28f);
        int ringCount = Math.max(1, propInt(block, "ring_count", 5));
        int segments = Math.max(16, propInt(block, "segments", 80));
        float width = propFloat(block, "width", 0.085f);
        float lifetime = propFloat(block, "lifetime", 0.075f);
        float[] color = propColor(block, "color");
        long[] seed = {0L};
        long group = NEXT_TRANSIENT_ARC_GROUP.getAndIncrement();
        return (buf, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = ctx.paramFloat("time", ctx.time());
            if (time < 0) time = ctx.time();
            if (time > duration) return;
            for (int ring = 0; ring < ringCount; ring++) {
                float delay = ring * 0.075f;
                float t = clamp01((time / duration - delay) / Math.max(0.05f, 1f - delay));
                if (t <= 0f || t >= 0.999f) continue;
                float eased = 1f - (float) Math.pow(1f - t, 3f);
                float radius = lerp(baseRadius, maxRadius * (1f - ring * 0.055f), eased);
                float alpha = (1f - smoothstep(t)) * (1f - ring * 0.08f);
                float tilt = (ring - (ringCount - 1) * 0.5f) * 0.11f;
                var arc = ctx.arcs().add(group);
                for (int point = 0; point <= segments; point++) {
                    float angle = (float) (Math.PI * 2.0 * point / segments) + ring * 0.41f;
                    float x = (float) Math.cos(angle) * radius;
                    float z = (float) Math.sin(angle) * radius;
                    float y = z * (float) Math.sin(tilt);
                    z *= (float) Math.cos(tilt);
                    arc.addPoint(x, y, z, width * (1f + (1f - t) * 0.8f), 0f);
                }
                configureCleanArc(arc, color, alpha, lifetime, ++seed[0]);
            }
        };
    }

    private static SimNode arcRadialRipple(VfxBlock block, PortValueSource ports) {
        float duration = Math.max(0.001f, propFloat(block, "duration", 1f));
        String durationParam = propString(block, "duration_param", "");
        float intensity = Math.max(0f, propFloat(block, "intensity", 1f));
        String intensityParam = propString(block, "intensity_param", "");
        float maxRadius = Math.max(0f, propFloat(block, "radius", 1.5f));
        int ringCount = Math.max(1, propInt(block, "ring_count", 8));
        int segments = Math.max(16, propInt(block, "segments", 32));
        float widthScale = Math.max(0.01f, propFloat(block, "width_scale", 0.48f));
        float lifetime = Math.max(0.001f, propFloat(block, "lifetime", 0.075f));
        float[] core = propColor(block, "core_color");
        String coreParam = propString(block, "core_color_param", "");
        float[] edge = propColor(block, "edge_color");
        String edgeParam = propString(block, "edge_color_param", "");
        long[] seed = {0L};
        return (buf, ctx) -> {
            float liveDuration = Math.max(0.001f, ctx.paramFloat(durationParam, duration));
            float t = ctx.time() / liveDuration;
            if (t < 0f || t >= 1f) return;
            float liveIntensity = Math.max(0f, ctx.paramFloat(intensityParam, intensity));
            float envelope = 1f - Math.abs(t * 2f - 1f);
            if (envelope <= 1e-4f || liveIntensity <= 1e-4f) return;

            float radius = maxRadius * envelope;
            float halfWidth = Math.max(0.0025f, radius / ringCount * widthScale);
            float coreR = ctx.paramColor(coreParam, 0, core[0]);
            float coreG = ctx.paramColor(coreParam, 1, core[1]);
            float coreB = ctx.paramColor(coreParam, 2, core[2]);
            float coreA = ctx.paramColor(coreParam, 3, core[3]);
            float edgeR = ctx.paramColor(edgeParam, 0, edge[0]);
            float edgeG = ctx.paramColor(edgeParam, 1, edge[1]);
            float edgeB = ctx.paramColor(edgeParam, 2, edge[2]);
            float edgeA = ctx.paramColor(edgeParam, 3, edge[3]);
            float alphaEnvelope = 0.8f * liveIntensity * envelope;

            for (int ring = 0; ring < ringCount; ring++) {
                float ringT = (ring + 0.5f) / ringCount;
                float ringRadius = radius * ringT;
                var arc = ctx.arcs().add();
                for (int point = 0; point <= segments; point++) {
                    float angle = Mth.TWO_PI * point / segments;
                    arc.addPoint(
                            Mth.cos(angle) * ringRadius,
                            0f,
                            Mth.sin(angle) * ringRadius,
                            halfWidth,
                            0f);
                }
                arc.setColor(
                        lerp(coreR, edgeR, ringT),
                        lerp(coreG, edgeG, ringT),
                        lerp(coreB, edgeB, ringT),
                        clamp01(lerp(coreA, edgeA, ringT) * alphaEnvelope));
                arc.setLifetime(lifetime);
                arc.setSeed(++seed[0]);
                arc.setNoiseStrength(0f);
                arc.setDriftSpeed(0f);
            }
        };
    }

    private static SimNode arcCollapsingBox(VfxBlock block, PortValueSource ports) {
        String progressParam = propString(block, "progress_param", "progress");
        String widthParam = propString(block, "width_param", "width");
        String heightParam = propString(block, "height_param", "height");
        String yawParam = propString(block, "yaw_param", "yaw");
        float defaultWidth = Math.max(0.3f, propFloat(block, "width", 1f));
        float defaultHeight = Math.max(0.5f, propFloat(block, "height", 2f));
        float defaultYaw = propFloat(block, "yaw", 0f);
        float collapseDegrees = propFloat(block, "collapse_degrees", 81f);
        float lineWidth = Math.max(0.001f, propFloat(block, "line_width", 0.02f));
        float lifetime = Math.max(0.001f, propFloat(block, "lifetime", 0.075f));
        float[] color = propColor(block, "color");
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        long[] seed = {0L};
        return (buf, ctx) -> {
            float progress = clamp01(ctx.paramFloat(progressParam, 0f));
            if (progress >= 1f) return;
            float width = Math.max(0.3f, ctx.paramFloat(widthParam, defaultWidth));
            float height = Math.max(0.5f, ctx.paramFloat(heightParam, defaultHeight));
            float entityYaw = ctx.paramFloat(yawParam, defaultYaw);
            float collapseProgress = clamp01(progress / 0.72f);
            float inverse = 1f - collapseProgress;
            float pitch = collapseDegrees * (1f - inverse * inverse * inverse) * Mth.DEG_TO_RAD;
            float yaw = (180f - entityYaw) * Mth.DEG_TO_RAD;
            float cosY = Mth.cos(yaw);
            float sinY = Mth.sin(yaw);
            float cosX = Mth.cos(pitch);
            float sinX = Mth.sin(pitch);
            float half = Math.max(0.15f, width * 0.5f);
            float[][] vertices = {
                    {-half, 0f, -half}, {half, 0f, -half}, {half, 0f, half}, {-half, 0f, half},
                    {-half, height, -half}, {half, height, -half}, {half, height, half}, {-half, height, half}
            };
            for (var vertex : vertices) {
                float x = vertex[0];
                float y = vertex[1];
                float z = vertex[2];
                float rotatedX = x * cosY - z * sinY;
                float rotatedZ = x * sinY + z * cosY;
                vertex[0] = rotatedX;
                vertex[1] = y * cosX - rotatedZ * sinX;
                vertex[2] = y * sinX + rotatedZ * cosX;
            }

            float flash = progress < 0.08f ? 1f - progress / 0.08f : 0f;
            float alpha = progress > 0.7f ? clamp01(1f - (progress - 0.7f) / 0.3f) : 1f;
            float red = clamp01(color[0] + (1f - color[0]) * flash);
            float green = clamp01(color[1] + (1f - color[1]) * flash);
            for (var edge : edges) {
                var from = vertices[edge[0]];
                var to = vertices[edge[1]];
                var arc = ctx.arcs().add();
                arc.addPoint(from[0], from[1], from[2], lineWidth, 0f);
                arc.addPoint(to[0], to[1], to[2], lineWidth, 0f);
                arc.setColor(red, green, color[2], color[3] * alpha);
                arc.setLifetime(lifetime);
                arc.setSeed(++seed[0]);
                arc.setNoiseStrength(0f);
                arc.setDriftSpeed(0f);
            }
        };
    }

    private static void configureCleanArc(
            ArcCurve arc,
            float[] color, float emission, float lifetime, long seed
    ) {
        arc.setColor(color[0] * emission, color[1] * emission, color[2] * emission, color[3] * emission);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);
        arc.setNoiseStrength(0f);
        arc.setDriftSpeed(0f);
    }

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
        if (base == null) return (buf, ctx) -> {
        };
        var surface = offsetTriangles(base, ox, oy, oz);
        var distributor = new SurfaceDistributor(surface);
        long[] seed = {0L};
        long[] lastGateFrame = {Long.MIN_VALUE};
        return (buf, ctx) -> {
            var frame = (long) (ctx.time() * fps);
            if (frequency > 0f) {
                if (frame % framePeriod != 0) return;
                if (frame == lastGateFrame[0]) return;
            }
            lastGateFrame[0] = frame;
            seed[0]++;
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
        if (base == null || contactBase == null) return (buf, ctx) -> {
        };
        var surface = offsetTriangles(base, ox, oy, oz);
        var contact = offsetTriangles(contactBase, cox, coy, coz);
        var distributor = new SurfaceDistributor(surface);
        long[] seed = {0L};
        long[] lastGateFrame = {Long.MIN_VALUE};
        return (buf, ctx) -> {
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
                var dist = MeshDistance.nearestDistance(contact, px, py, pz);
                if (dist > contactRange) continue;
                var nearest = MeshDistance.nearestPoint(contact, px, py, pz);
                var arc = ctx.arcs().add();
                CurveGenerator.generateContactArc(
                        arc, px, py, pz,
                        nearest[0], nearest[1], nearest[2],
                        width, segments,
                        color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                        lifetime, seed[0] * 97 + (long) (s.x() * 1000f));
                arc.setSurface(contact);
                arc.setNoiseStrength(noiseStrength);
                arc.setDriftSpeed(driftSpeed);
            }
        };
    }

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
                if (!arc.fresh() || !arc.hasSurface()) continue;
                var size = arc.size();
                if (size < 2) continue;
                var spawned = 0;
                var pointCount = Math.min(size, 10);
                for (var i = 0; i < pointCount; i++) {
                    if (spawned >= maxSparks) break;
                    if (random.nextFloat() > probability) continue;
                    var idx = i * (size - 1) / Math.max(1, pointCount - 1);
                    float px = arc.x(idx), py = arc.y(idx), pz = arc.z(idx);
                    var ref = surfaceTangent(arc, idx);
                    var dir = SurfaceDistributor.tangentDirection(ref[0], ref[1], ref[2],
                            (float) Math.PI / 3f, random);
                    var speed = splashSpeed * (0.3f + 0.9f * random.nextFloat());
                    var vx = dir[0] * speed;
                    var vy = dir[1] * speed;
                    var vz = dir[2] * speed;
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


    private static float portFloat(PortValueSource ports, String portId, int particleIndex,
                                   ParticleBuffer buffer, SimContext ctx, float fallback) {
        var v = ports.eval(portId, particleIndex, buffer, ctx);
        if (v == null || v.type() != ValueType.FLOAT) return fallback;
        return v.asFloat();
    }

    private static byte layerOf(VfxBlock block) {
        return ParticleBuffer.layerByte(propString(block, "layer", "fire"));
    }

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

    private static void applyPositionParam(float[] position, String param, SimContext ctx) {
        if (param.isEmpty()) return;
        position[0] += ctx.paramVec3(param, 0, 0f);
        position[1] += ctx.paramVec3(param, 1, 0f);
        position[2] += ctx.paramVec3(param, 2, 0f);
    }

    private static float stableWave(int index, float salt) {
        return (float) Math.sin((index + 1) * 12.9898f + salt * 78.233f);
    }

    private static float stableUnit(int index, float salt) {
        return stableWave(index, salt) * 0.5f + 0.5f;
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0f, 1f);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
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
