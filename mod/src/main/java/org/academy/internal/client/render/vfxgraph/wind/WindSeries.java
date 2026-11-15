package org.academy.internal.client.render.vfxgraph.wind;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistrationEvent;

import java.util.List;

import static org.academy.api.client.render.vfxgraph.nodes.VfxBlockTypes.prop;
import static org.academy.api.client.render.vfxgraph.nodes.VfxBlockTypes.type;

@EventBusSubscriber(Dist.CLIENT)
public final class WindSeries {
    private WindSeries() {
    }

    @SubscribeEvent
    public static void onRegisterVfxBlocks(VfxBlockRegistrationEvent event) {
        var metadata = event.metadata();
        var blocks = event.blocks();

        metadata.register(type("vfx.block.cloud_vortex", "spawn", "Cloud Vortex / Hollow Turbulent Funnel",
                CloudVortexEmitter.properties()));
        blocks.register("vfx.block.cloud_vortex", CloudVortexEmitter::create);

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
    }
}
