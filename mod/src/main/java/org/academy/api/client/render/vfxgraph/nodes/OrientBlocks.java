package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class OrientBlocks {
    private OrientBlocks() {
    }

    static SimNode faceCamera(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) buf.setRotation(i, 0f);
        };
    }

    static SimNode velocity(VfxBlock block, PortValueSource ports) {
        var offset = BlockProps.getFloat(block.properties(), "offset", 0f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var angle = (float) Math.atan2(buf.velocityZ(i), buf.velocityX(i));
                buf.setRotation(i, angle + offset);
            }
        };
    }

    static SimNode fixed(VfxBlock block, PortValueSource ports) {
        var rotation = BlockProps.getFloat(block.properties(), "rotation", 0f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, rotation);
            }
        };
    }

    static SimNode spin(VfxBlock block, PortValueSource ports) {
        var speed = BlockProps.getFloat(block.properties(), "speed", 1f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                buf.setRotation(i, buf.rotation(i) + speed * ctx.dt());
            }
        };
    }
}
