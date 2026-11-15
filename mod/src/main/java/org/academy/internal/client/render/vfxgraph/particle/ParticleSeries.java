package org.academy.internal.client.render.vfxgraph.particle;

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
public final class ParticleSeries {
    private ParticleSeries() {
    }

    @SubscribeEvent
    public static void onRegisterVfxBlocks(VfxBlockRegistrationEvent event) {
        var metadata = event.metadata();
        var blocks = event.blocks();

        metadata.register(type("vfx.block.material_sheets", "spawn", "Material Sheets / Sampled Surface",
                MaterialSheetEmitter.properties()));
        blocks.register("vfx.block.material_sheets", (block, ports) -> MaterialSheetEmitter.create(block));

        metadata.register(type("vfx.block.organic_strands", "spawn", "Translucent Organic Strands",
                List.of(
                        prop("mode", ValueType.INT, Value.of(0)),
                        prop("count", ValueType.INT, Value.of(12)),
                        prop("segments", ValueType.INT, Value.of(80)),
                        prop("duration", ValueType.FLOAT, Value.of(0.65f)),
                        prop("radius", ValueType.FLOAT, Value.of(0.65f)),
                        prop("length", ValueType.FLOAT, Value.of(16f)),
                        prop("speed", ValueType.FLOAT, Value.of(8f)),
                        prop("thickness", ValueType.FLOAT, Value.of(0.04f)),
                        prop("opacity", ValueType.FLOAT, Value.of(0.6f)))));
        blocks.register("vfx.block.organic_strands", (block, ports) -> OrganicStrandEmitter.create(block));
    }
}
