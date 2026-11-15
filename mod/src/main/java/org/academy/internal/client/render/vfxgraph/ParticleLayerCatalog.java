package org.academy.internal.client.render.vfxgraph;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistrationEvent;

@EventBusSubscriber(Dist.CLIENT)
public final class ParticleLayerCatalog {
    private ParticleLayerCatalog() {
    }

    @SubscribeEvent
    public static void onRegisterVfxBlocks(VfxBlockRegistrationEvent event) {
        var layers = event.layers();
        layers.register("smoke", (byte) 1);
        layers.register("plasma_core", (byte) 2);
        layers.register("plasma_halo", (byte) 3);
        layers.register("trail_white", (byte) 4);
        layers.register("trail_plasma", (byte) 5);
        layers.register("ion_spark", (byte) 6);
        layers.register("impact_core", (byte) 7);
        layers.register("plasma_mote", (byte) 8);
        layers.register("wind_volume", (byte) 9);
        layers.register("wind_dust", (byte) 10);
        layers.register("impact_mono", (byte) 11);
        layers.register("sky_cloud", (byte) 12);
        layers.register("sky_halo", (byte) 13);
        layers.register("iron_sand", (byte) 14);
    }
}
