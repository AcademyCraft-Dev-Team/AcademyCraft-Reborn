package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class SpawnBlocks {
    private SpawnBlocks() {
    }

    static SimNode rate(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var layer = BlockSim.layerOf(properties);
        var rate = BlockProps.getFloat(properties, "rate", 10f);
        var positionParam = BlockProps.getString(properties, "position_param", "");
        var shape = BlockProps.buildShape(properties);
        float[] acc = {0f};
        return (buf, ctx) -> {
            var r = BlockSim.portFloat(ports, "rate", -1, buf, ctx, rate);
            acc[0] += r * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            if (n == 0) return;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < n; k++) {
                BlockSim.samplePosition(shape, p, positionParam, ctx);
                int i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    static SimNode burst(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        int count = BlockProps.getInt(properties, "count", 10);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var layer = BlockSim.layerOf(properties);
        var positionParam = BlockProps.getString(properties, "position_param", "");
        var shape = BlockProps.buildShape(properties);
        boolean[] fired = {false};
        return (buf, ctx) -> {
            if (fired[0]) return;
            fired[0] = true;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                BlockSim.samplePosition(shape, p, positionParam, ctx);
                int i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    static SimNode periodic(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        int count = BlockProps.getInt(properties, "count", 5);
        var interval = BlockProps.getFloat(properties, "interval", 1f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var layer = BlockSim.layerOf(properties);
        var positionParam = BlockProps.getString(properties, "position_param", "");
        var shape = BlockProps.buildShape(properties);
        float[] acc = {0f};
        return (buf, ctx) -> {
            acc[0] += ctx.dt();
            if (acc[0] < interval) return;
            acc[0] = 0f;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < count; k++) {
                BlockSim.samplePosition(shape, p, positionParam, ctx);
                int i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }

    static SimNode distance(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var rate = BlockProps.getFloat(properties, "rate", 5f);
        var speed = BlockProps.getFloat(properties, "speed", 1f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var size = BlockProps.getFloat(properties, "size", 0.1f);
        var color = BlockProps.getColor(properties, "color");
        var vx = BlockProps.getFloat(properties, "vx", 0f);
        var vy = BlockProps.getFloat(properties, "vy", 0f);
        var vz = BlockProps.getFloat(properties, "vz", 0f);
        var layer = BlockSim.layerOf(properties);
        var positionParam = BlockProps.getString(properties, "position_param", "");
        var shape = BlockProps.buildShape(properties);
        float[] acc = {0f};
        return (buf, ctx) -> {
            acc[0] += rate * speed * ctx.dt();
            var n = (int) acc[0];
            acc[0] -= n;
            if (n == 0) return;
            var start = buf.count();
            var p = new float[3];
            for (var k = 0; k < n; k++) {
                BlockSim.samplePosition(shape, p, positionParam, ctx);
                int i = buf.spawn();
                BlockSim.writeParticle(buf, i, p[0], p[1], p[2], vx, vy, vz, size, color, lifetime, layer);
            }
            ctx.emitBatch(start, buf.count());
        };
    }
}
