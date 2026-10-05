package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class SpawnNodes {
    private SpawnNodes() {
    }

    static SimNode rate(GraphNode node) {
        var properties = node.properties();
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var param = BlockProps.getString(properties, "param", "");
        var shape = BlockProps.buildShape(properties);
        var layer = BlockSim.layerOf(properties);
        float[] acc = {0f};

        return (buf, ctx) -> {
            var rate = param.isEmpty() ? BlockProps.getFloat(properties, "rate", 10f) : ctx.paramFloat(param, 10f);
            acc[0] += rate * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            ctx.spawnStart = buf.count();
            if (n == 0) return;
            var p = new float[3];
            for (var k = 0; k < n; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
        };
    }

    static SimNode burst(GraphNode node) {
        var properties = node.properties();
        var count = BlockProps.getInt(properties, "count", 10);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var shape = BlockProps.buildShape(properties);
        var layer = BlockSim.layerOf(properties);
        boolean[] fired = {false};

        return (buf, ctx) -> {
            if (fired[0]) {
                ctx.spawnStart = buf.count();
                return;
            }
            fired[0] = true;
            ctx.spawnStart = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
        };
    }

    static SimNode periodic(GraphNode node) {
        var properties = node.properties();
        var count = BlockProps.getInt(properties, "count", 5);
        var interval = BlockProps.getFloat(properties, "interval", 1f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var shape = BlockProps.buildShape(properties);
        var layer = BlockSim.layerOf(properties);
        float[] acc = {0f};

        return (buf, ctx) -> {
            acc[0] += ctx.dt();
            ctx.spawnStart = buf.count();
            if (acc[0] < interval) return;
            acc[0] = 0f;
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                shape.sample(ctx.random(), p);
                var i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
        };
    }

    static SimNode distance(GraphNode node) {
        var properties = node.properties();
        var rate = BlockProps.getFloat(properties, "rate", 5f);
        var speed = BlockProps.getFloat(properties, "speed", 1f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var layer = BlockSim.layerOf(properties);
        float[] acc = {0f};

        return (buf, ctx) -> {
            acc[0] += rate * speed * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            ctx.spawnStart = buf.count();
            if (n == 0) return;
            for (var k = 0; k < n; k++) {
                var i = buf.spawn();
                buf.setVelocity(i, vx, vy, vz);
                buf.setSize(i, size);
                buf.setColor(i, color[0], color[1], color[2], color[3]);
                buf.setLifetime(i, lifetime);
                buf.setAge(i, 0f);
                buf.setLayer(i, layer);
            }
        };
    }
}
