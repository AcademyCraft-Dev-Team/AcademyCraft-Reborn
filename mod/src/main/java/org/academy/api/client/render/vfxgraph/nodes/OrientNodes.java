package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class OrientNodes {
    private OrientNodes() {
    }

    static SimNode faceCamera(GraphNode node) {
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, 0f);
            }
        };
    }

    static SimNode velocity(GraphNode node) {
        var offset = BlockProps.getFloat(node.properties(), "offset", 0f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var angle = (float) Math.atan2(buf.velocityZ(i), buf.velocityX(i));
                buf.setRotation(i, angle + offset);
            }
        };
    }

    static SimNode fixed(GraphNode node) {
        var rotation = BlockProps.getFloat(node.properties(), "rotation", 0f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, rotation);
            }
        };
    }

    static SimNode spin(GraphNode node) {
        var speed = BlockProps.getFloat(node.properties(), "speed", 1f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, buf.rotation(i) + speed * ctx.dt());
            }
        };
    }
}
