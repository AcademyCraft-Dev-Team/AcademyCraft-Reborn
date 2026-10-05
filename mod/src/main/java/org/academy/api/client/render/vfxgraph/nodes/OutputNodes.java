package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class OutputNodes {
    private OutputNodes() {
    }

    static SimNode noop(GraphNode node) {
        return (buf, ctx) -> {
        };
    }

    static SimNode trail(GraphNode node) {
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.pushTrail(i, buf.positionX(i), buf.positionY(i), buf.positionZ(i));
            }
        };
    }
}
