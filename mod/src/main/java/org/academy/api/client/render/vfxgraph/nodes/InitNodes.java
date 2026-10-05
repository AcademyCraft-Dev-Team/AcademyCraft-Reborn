package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class InitNodes {
    private InitNodes() {
    }

    static SimNode position(GraphNode node) {
        var shape = BlockProps.buildShape(node.properties());
        return (buf, ctx) -> {
            var p = new float[3];
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                shape.sample(ctx.random(), p);
                buf.setPosition(i, p[0], p[1], p[2]);
            }
        };
    }

    static SimNode velocity(GraphNode node) {
        var properties = node.properties();
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 1f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var random = BlockProps.getFloat(properties, "random", 0f);
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> {
            float rvx = vx, rvy = vy, rvz = vz;
            if (!param.isEmpty()) {
                rvx = ctx.paramVec3(param, 0, vx);
                rvy = ctx.paramVec3(param, 1, vy);
                rvz = ctx.paramVec3(param, 2, vz);
            }
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                var rx = BlockSim.jitter(ctx, random);
                var ry = BlockSim.jitter(ctx, random);
                var rz = BlockSim.jitter(ctx, random);
                buf.setVelocity(i, rvx + rx, rvy + ry, rvz + rz);
            }
        };
    }

    static SimNode color(GraphNode node) {
        var properties = node.properties();
        var color = BlockProps.getColor(properties, "color");
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> {
            float r = color[0], g = color[1], b = color[2], a = color[3];
            if (!param.isEmpty()) {
                r = ctx.paramColor(param, 0, color[0]);
                g = ctx.paramColor(param, 1, color[1]);
                b = ctx.paramColor(param, 2, color[2]);
                a = ctx.paramColor(param, 3, color[3]);
            }
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                buf.setColor(i, r, g, b, a);
            }
        };
    }

    static SimNode size(GraphNode node) {
        var properties = node.properties();
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> {
            var rs = param.isEmpty() ? size : ctx.paramFloat(param, size);
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                buf.setSize(i, rs);
            }
        };
    }

    static SimNode rotation(GraphNode node) {
        var rotation = BlockProps.getFloat(node.properties(), "rotation", 0f);
        return (buf, ctx) -> {
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                buf.setRotation(i, rotation);
            }
        };
    }

    static SimNode lifetime(GraphNode node) {
        var lifetime = BlockProps.getFloat(node.properties(), "lifetime", 1f);
        return (buf, ctx) -> {
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                buf.setLifetime(i, lifetime);
            }
        };
    }

    static SimNode mass(GraphNode node) {
        var mass = BlockProps.getFloat(node.properties(), "mass", 1f);
        return (buf, ctx) -> {
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
                buf.setMass(i, mass);
            }
        };
    }

    static SimNode randomize(GraphNode node) {
        var properties = node.properties();
        var posAmp = BlockProps.getFloat(properties, "pos", 0.1f);
        var velAmp = BlockProps.getFloat(properties, "vel", 0.1f);
        var sizeAmp = BlockProps.getFloat(properties, "size", 0.1f);
        var lifeAmp = BlockProps.getFloat(properties, "lifetime", 0.1f);
        return (buf, ctx) -> {
            for (var i = ctx.spawnStart; i < buf.count(); i++) {
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
            }
        };
    }
}
