package org.academy;

import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.internal.client.render.vfxgraph.ParticleLayerCatalog;
import org.academy.internal.client.render.vfxgraph.arc.ArcSeries;
import org.academy.internal.client.render.vfxgraph.particle.ParticleSeries;
import org.academy.internal.client.render.vfxgraph.wind.WindSeries;

public final class VfxSeriesTestRegistration {
    private static boolean installed;

    private VfxSeriesTestRegistration() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        NeoForge.EVENT_BUS.start();
        NeoForge.EVENT_BUS.register(ParticleLayerCatalog.class);
        NeoForge.EVENT_BUS.register(ArcSeries.class);
        NeoForge.EVENT_BUS.register(WindSeries.class);
        NeoForge.EVENT_BUS.register(ParticleSeries.class);
    }

    public static void registerAll(NodeRegistry metadata, VfxBlockRegistry blocks) {
        install();
        VfxBlocks.registerAll(metadata, blocks);
    }
}
