package org.academy.api.client.render.vfxgraph.nodes;

import net.neoforged.bus.api.Event;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.vfxgraph.sim.VfxLayers;

public final class VfxBlockRegistrationEvent extends Event {
    private final NodeRegistry metadata;
    private final VfxBlockRegistry blocks;
    private final VfxLayers.Registry layers;

    public VfxBlockRegistrationEvent(NodeRegistry metadata, VfxBlockRegistry blocks) {
        this.metadata = metadata;
        this.blocks = blocks;
        this.layers = VfxLayers::register;
    }

    public NodeRegistry metadata() {
        return metadata;
    }

    public VfxBlockRegistry blocks() {
        return blocks;
    }

    public VfxLayers.Registry layers() {
        return layers;
    }
}
