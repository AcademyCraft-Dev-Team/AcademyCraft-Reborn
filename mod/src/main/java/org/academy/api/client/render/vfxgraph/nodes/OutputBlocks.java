package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

public final class OutputBlocks {
    private OutputBlocks() {
    }

    public static SimNode noop(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
        };
    }

    public static SimNode trail(VfxBlock block, PortValueSource ports) {
        var filter = BlockSim.layerFilter(block.properties());
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        };
    }
}
