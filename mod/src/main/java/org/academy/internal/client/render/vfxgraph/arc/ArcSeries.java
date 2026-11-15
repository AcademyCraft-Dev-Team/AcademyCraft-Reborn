package org.academy.internal.client.render.vfxgraph.arc;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.curve.CurveFactories;
import org.academy.api.client.render.vfxgraph.nodes.OutputBlocks;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistrationEvent;
import org.academy.api.client.render.vfxgraph.render.CurveLightings;
import org.academy.api.client.render.vfxgraph.sim.CurveSimulations;

import java.util.List;
import java.util.stream.Stream;

import static org.academy.api.client.render.vfxgraph.nodes.VfxBlockTypes.prop;
import static org.academy.api.client.render.vfxgraph.nodes.VfxBlockTypes.type;

@EventBusSubscriber(Dist.CLIENT)
public final class ArcSeries {
    private ArcSeries() {
    }

    private static final List<PropertySpec> OUTPUT_PROPERTIES = List.of(
            prop("vertex", ValueType.STRING, Value.string("")),
            prop("shader", ValueType.STRING, Value.string("")),
            prop("texture", ValueType.STRING, Value.string("")),
            prop("blend", ValueType.STRING, Value.string("")),
            prop("layer", ValueType.STRING, Value.string(""))
    );

    private static final List<PropertySpec> ARC_SURFACE_PROPS = List.of(
            prop("mesh", ValueType.STRING, Value.string("builtin:plane")),
            prop("surface", ValueType.STRING, Value.string("")),
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

    @SubscribeEvent
    public static void onRegisterVfxBlocks(VfxBlockRegistrationEvent event) {
        var metadata = event.metadata();
        var blocks = event.blocks();
        CurveFactories.register(new ArcCurveFactory());
        CurveSimulations.register(new ArcCurveSimulation());
        CurveLightings.register(new ArcCurveLighting());

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
        metadata.register(type("vfx.block.ball_lightning", "spawn", "Ball Lightning Shell and Filaments",
                BallLightningArcEmitter.properties()));
        blocks.register("vfx.block.ball_lightning", BallLightningArcEmitter::create);
        metadata.register(type("vfx.block.iron_sand", "spawn", "Iron Sand / Granular Field and Embedded Currents",
                IronSandEmitter.properties()));
        blocks.register("vfx.block.iron_sand", IronSandEmitter::create);

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

        metadata.register(type("vfx.block.arc_surface", "spawn", "Arc Surface", ARC_SURFACE_PROPS));
        blocks.register("vfx.block.arc_surface", SurfaceArcBlocks::surface);
        metadata.register(type("vfx.block.arc_contact", "spawn", "Arc Contact",
                Stream.concat(ARC_SURFACE_PROPS.stream(), ARC_CONTACT_PROPS.stream()).toList()));
        blocks.register("vfx.block.arc_contact", SurfaceArcBlocks::contact);
        metadata.register(type("vfx.block.arc_spark", "spawn", "Arc Spark", ARC_SPARK_PROPS));
        blocks.register("vfx.block.arc_spark", SurfaceArcBlocks::spark);

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

        metadata.register(type("vfx.block.output_arc", "output", "Output Arc",
                Stream.concat(OUTPUT_PROPERTIES.stream(), ARC_OUTPUT_PROPERTIES.stream()).toList()));
        blocks.register("vfx.block.output_arc", OutputBlocks::noop);
    }
}
