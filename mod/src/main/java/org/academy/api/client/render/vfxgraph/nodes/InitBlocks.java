package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class InitBlocks {
    private InitBlocks() {
    }

    static SimNode position(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var shape = BlockProps.buildShape(properties);
        var positionParam = BlockProps.getString(properties, "position_param", "");
        return (buf, ctx) -> {
            var p = new float[3];
            ctx.forEachIncoming(i -> {
                BlockSim.samplePosition(shape, p, positionParam, ctx);
                buf.setPosition(i, p[0], p[1], p[2]);
            });
        };
    }

    static SimNode velocity(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 1f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var random = BlockProps.getFloat(properties, "random", 0f);
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            var rvx = BlockSim.portFloat(ports, "vx", i, buf, ctx, vx);
            var rvy = BlockSim.portFloat(ports, "vy", i, buf, ctx, vy);
            var rvz = BlockSim.portFloat(ports, "vz", i, buf, ctx, vz);
            if (!param.isEmpty()) {
                rvx = ctx.paramVec3(param, 0, rvx);
                rvy = ctx.paramVec3(param, 1, rvy);
                rvz = ctx.paramVec3(param, 2, rvz);
            }
            var rx = BlockSim.jitter(ctx, random);
            var ry = BlockSim.jitter(ctx, random);
            var rz = BlockSim.jitter(ctx, random);
            buf.setVelocity(i, rvx + rx, rvy + ry, rvz + rz);
        });
    }

    static SimNode color(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var color = BlockProps.getColor(properties, "color");
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            var v = ports.eval("color", i, buf, ctx);
            float r = color[0], g = color[1], b = color[2], a = color[3];
            if (v != null && v.type() == ValueType.COLOR) {
                var c = v.asColor();
                r = c.x;
                g = c.y;
                b = c.z;
                a = c.w;
            } else if (!param.isEmpty()) {
                r = ctx.paramColor(param, 0, r);
                g = ctx.paramColor(param, 1, g);
                b = ctx.paramColor(param, 2, b);
                a = ctx.paramColor(param, 3, a);
            }
            buf.setColor(i, r, g, b, a);
        });
    }

    static SimNode size(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            var s = BlockSim.portFloat(ports, "size", i, buf, ctx, size);
            if (!param.isEmpty()) s = ctx.paramFloat(param, s);
            buf.setSize(i, s);
        });
    }

    static SimNode rotation(VfxBlock block, PortValueSource ports) {
        var rotation = BlockProps.getFloat(block.properties(), "rotation", 0f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> buf.setRotation(i, rotation));
    }

    static SimNode lifetime(VfxBlock block, PortValueSource ports) {
        var lifetime = BlockProps.getFloat(block.properties(), "lifetime", 1f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> buf.setLifetime(i, lifetime));
    }

    static SimNode mass(VfxBlock block, PortValueSource ports) {
        var mass = BlockProps.getFloat(block.properties(), "mass", 1f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> buf.setMass(i, mass));
    }

    static SimNode randomize(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var posAmp = BlockProps.getFloat(properties, "pos", 0.1f);
        var velAmp = BlockProps.getFloat(properties, "vel", 0.1f);
        var sizeAmp = BlockProps.getFloat(properties, "size", 0.1f);
        var lifeAmp = BlockProps.getFloat(properties, "lifetime", 0.1f);
        return (buf, ctx) -> ctx.forEachIncoming(i -> {
            buf.setPosition(i,
                    buf.positionX(i) + BlockSim.jitter(ctx, posAmp),
                    buf.positionY(i) + BlockSim.jitter(ctx, posAmp),
                    buf.positionZ(i) + BlockSim.jitter(ctx, posAmp));
            buf.setVelocity(i,
                    buf.velocityX(i) * (1f + BlockSim.jitter(ctx, velAmp)),
                    buf.velocityY(i) * (1f + BlockSim.jitter(ctx, velAmp)),
                    buf.velocityZ(i) * (1f + BlockSim.jitter(ctx, velAmp)));
            buf.setSizeScaled(i, buf.size(i) * (1f + BlockSim.jitter(ctx, sizeAmp)));
            buf.setLifetime(i, buf.lifetime(i) * (1f + BlockSim.jitter(ctx, lifeAmp)));
        });
    }
}
