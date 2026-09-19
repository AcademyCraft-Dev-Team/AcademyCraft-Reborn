package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/** A turbulent cloud funnel along local +Y with a clear eye. No curves or arcs are emitted. */
public final class CloudVortexEmitter {
    /** Circumscribed radius of the cloud shader's 1.28 by 0.88 billboard. */
    public static final float CLOUD_BOUNDS_SCALE = 1.56f;
    private CloudVortexEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(number("count", 1024), number("length", 64), number("width", 1),
                number("duration", 0.65f), number("root_radius", 0.35f), number("head_radius", 8.5f),
                number("rotation_speed", 17), number("flow_speed", 14), number("turbulence", 0.24f),
                number("size", 2f), number("opacity", 0.5f), number("eye_ratio", 0.38f),
                number("first_person_opacity", 1f));
    }

    public static SimNode create(VfxBlock block, PortValueSource ports) {
        int count = Math.clamp((int) property(block, "count", 1024), 32, 1536);
        // Stable buffer seeds keep ownership valid when another block swap-removes particles.
        var owned = new HashMap<Float, Integer>();
        int[] indices = new int[count];
        return (buffer, ctx) -> {
            float time = ctx.paramFloat("time", -1);
            if (!Float.isFinite(time) || time < 0) time = ctx.time();
            float duration = value(block, ctx, "duration", 0.65f, 0.01f, 30);
            Arrays.fill(indices, -1);
            for (int i = buffer.count() - 1; i >= 0; i--) {
                var ordinal = owned.get(buffer.seed(i));
                if (ordinal == null) continue;
                if (time >= duration) buffer.kill(i);
                else indices[ordinal] = i;
            }
            if (time >= duration) { owned.clear(); return; }
            owned.entrySet().removeIf(entry -> indices[entry.getValue()] < 0);
            int start = buffer.count();
            for (int ordinal = 0; ordinal < count; ordinal++) {
                if (indices[ordinal] >= 0) continue;
                int particle = buffer.spawn();
                indices[ordinal] = particle;
                owned.put(buffer.seed(particle), ordinal);
            }
            ctx.emitBatch(start, buffer.count());
            float length = value(block, ctx, "length", 64, 0.1f, 512);
            float width = value(block, ctx, "width", 1, 0.01f, 32);
            float root = value(block, ctx, "root_radius", 0.35f, 0, 16);
            float head = value(block, ctx, "head_radius", 8.5f, 0, 32);
            float spin = value(block, ctx, "rotation_speed", 17, -100, 100);
            float flow = value(block, ctx, "flow_speed", 14, -100, 100);
            float turbulence = value(block, ctx, "turbulence", 0.24f, 0, 2);
            float size = value(block, ctx, "size", 2f, 0.01f, 8);
            float opacity = value(block, ctx, "opacity", 0.5f, 0, 1);
            float eyeRatio = value(block, ctx, "eye_ratio", 0.38f, 0, 0.8f);
            float firstPersonOpacity = value(block, ctx, "first_person_opacity", 1, 0, 1);
            float firstPerson = value(block, ctx, "view_first_person", 0, 0, 1);
            opacity *= 1 + firstPerson * (firstPersonOpacity - 1);
            float age = time / duration;
            float fade = smooth(age / 0.1f) * (1 - smooth((age - 0.58f) / 0.42f));
            long seed = (long) ctx.paramFloat("seed", 42);
            for (int ordinal = 0; ordinal < count; ordinal++) {
                int particle = indices[ordinal];
                long random = seed + ordinal * 7919L;
                boolean wisp = ordinal % 5 == 0;
                float u = fract((ordinal + 0.5f) / count + time * flow / length);
                float phase = unit(random, 1) * 6.283185f;
                float angle = phase + time * spin * (1.2f - 0.55f * u);
                float cone = root + (head - root) * (float) Math.pow(u, 1.45);
                // Distribute cloud centres across an annular wall, leaving the axial eye empty.
                float radial = wisp ? 0.88f + unit(random, 2) * 0.3f
                        : (float) Math.sqrt(0.58f * 0.58f + unit(random, 2) * (0.88f * 0.88f - 0.58f * 0.58f));
                float billow = 1 + turbulence * (float) Math.sin(u * 27 - time * 12 + phase);
                float radius = width * cone * radial * billow;
                float curlX = width * turbulence * u * (float) Math.sin(u * 13 - time * 7);
                float curlZ = width * turbulence * u * (float) Math.cos(u * 17 - time * 9);
                float particleSize = width * size * (0.28f + u * 0.85f)
                        * (0.7f + unit(random, 3) * 0.6f) * (wisp ? 0.72f : 1);
                // Keeping only the centres outside the eye is insufficient: camera-facing cloud
                // quads would still overlap the axis. Reserve their complete footprint as well.
                if (eyeRatio > 0) {
                    float clearance = width * cone * eyeRatio + particleSize * CLOUD_BOUNDS_SCALE
                            + (float) Math.hypot(curlX, curlZ) + width * 0.03f;
                    radius = Math.max(radius, clearance);
                }
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                buffer.setPosition(particle, cos * radius + curlX, u * length, sin * radius + curlZ);
                buffer.setVelocity(particle, -sin * radius * spin, flow, cos * radius * spin);
                buffer.setSize(particle, particleSize);
                float ends = smooth(u / 0.025f) * smooth((1 - u) / 0.09f);
                float light = 0.76f + 0.2f * (0.5f + 0.5f * sin);
                buffer.setColor(particle, light, light, light,
                        opacity * fade * ends * (wisp ? 0.26f : 0.8f + unit(random, 4) * 0.2f));
                // The shader uses this deterministic phase, so editor rewind reproduces the image.
                buffer.setRotation(particle, phase);
                buffer.setAge(particle, time);
                buffer.setLifetime(particle, duration);
                buffer.setLayer(particle, ParticleBuffer.layerByte("wind_volume"));
            }
        };
    }

    private static float fract(float value) { return value - (float) Math.floor(value); }
    private static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
    private static float property(VfxBlock block, String key, float fallback) {
        try {
            float value = Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback)));
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }
    private static float value(VfxBlock block, SimContext ctx, String key, float fallback, float min, float max) {
        float value = ctx.paramFloat(key, property(block, key, fallback));
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    private static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id.replace('_', ' '), ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
