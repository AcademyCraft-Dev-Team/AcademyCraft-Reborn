package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.shape.EmitterShape;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;

import java.util.Map;
import java.util.function.IntConsumer;

final class BlockSim {
    private BlockSim() {
    }

    static byte layerOf(Map<String, String> properties) {
        return ParticleBuffer.layerByte(BlockProps.getString(properties, "layer", "fire"));
    }

    static byte layerFilter(Map<String, String> properties) {
        return ParticleBuffer.layerFilter(BlockProps.getString(properties, "layer", ""));
    }

    static float lifeT(ParticleBuffer buf, int i) {
        var l = buf.lifetime(i);
        if (l <= 0f) return 1f;
        return Math.min(1f, buf.age(i) / l);
    }

    static float jitter(SimContext ctx, float amp) {
        return amp * (ctx.random().nextFloat() * 2f - 1f);
    }

    static float portFloat(PortValueSource ports, String portId, int particleIndex,
                           ParticleBuffer buffer, SimContext ctx, float fallback) {
        var v = ports.eval(portId, particleIndex, buffer, ctx);
        if (v == null || v.type() != ValueType.FLOAT) return fallback;
        return v.asFloat();
    }

    static void samplePosition(EmitterShape shape, float[] position, String positionParam, SimContext ctx) {
        shape.sample(ctx.random(), position);
        applyPositionParam(position, positionParam, ctx);
    }

    static void applyPositionParam(float[] position, String param, SimContext ctx) {
        if (param.isEmpty()) return;
        position[0] += ctx.paramVec3(param, 0, 0f);
        position[1] += ctx.paramVec3(param, 1, 0f);
        position[2] += ctx.paramVec3(param, 2, 0f);
    }

    static void writeParticle(ParticleBuffer buf, int i,
                              float px, float py, float pz,
                              float vx, float vy, float vz,
                              float size, float[] color, float lifetime, byte layer) {
        buf.setPosition(i, px, py, pz);
        buf.setVelocity(i, vx, vy, vz);
        buf.setSize(i, size);
        buf.setColor(i, color[0], color[1], color[2], color[3]);
        buf.setLifetime(i, lifetime);
        buf.setAge(i, 0f);
        buf.setLayer(i, layer);
    }

    static void forEachLayer(ParticleBuffer buf, byte filter, IntConsumer action) {
        for (var i = 0; i < buf.count(); i++) {
            if (filter >= 0 && buf.layer(i) != filter) continue;
            action.accept(i);
        }
    }

    static float clamp01(float value) {
        return Math.clamp(value, 0f, 1f);
    }

    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    static float hash(float x, float y, float z) {
        var s = (float) Math.sin(x * 127.1f + y * 311.7f + z * 74.7f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    static float stableWave(int index, float salt) {
        return (float) Math.sin((index + 1) * 12.9898f + salt * 78.233f);
    }

    static float stableUnit(int index, float salt) {
        return stableWave(index, salt) * 0.5f + 0.5f;
    }
}
