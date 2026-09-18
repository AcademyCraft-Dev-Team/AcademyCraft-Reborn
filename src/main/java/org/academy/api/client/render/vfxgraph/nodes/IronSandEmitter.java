package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;

import java.util.List;

import static org.academy.api.client.render.vfxgraph.nodes.ElectricArcEmitter.*;
import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/** Entity-independent, seekable granular shapes. All dimensions and presentation live in the graph. */
public final class IronSandEmitter {
    private IronSandEmitter() {}

    public static List<PropertySpec> properties() {
        return List.of(number("form", 0), number("radius", 2), number("count", 2600),
                number("grain_size", 0.065f), number("band_width", 0.32f), number("height", 2.1f),
                number("speed", 1.4f), number("duration", 0.65f), number("opacity", 1),
                number("detail", 1), number("arc_count", 7), number("arc_width", 0.015f), number("source_elevation", 0));
    }

    /** form: 0 = ankle ring, 1 = rising directional guard (+Z), 2 = whip, 3 = chainsaw cloud. */
    public static SimNode create(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        // mass is an emitter ownership tag, as in SkyDischargeEmitter; this graph has no physics blocks.
        float owner = group;
        float[] epoch = {Float.NaN};
        var point = new Vector3f();
        var path = new ArcCurve();
        return (buffer, ctx) -> {
            for (int i = buffer.count() - 1; i >= 0; i--) if (buffer.mass(i) == owner) buffer.kill(i);
            ctx.arcs().removeGroup(group);
            float t = time(ctx, epoch);
            int form = Math.round(value(block, ctx, "form", 0, 0, 3));
            float duration = value(block, ctx, "duration", 0.65f, 0.1f, 4);
            float fade = value(block, ctx, "opacity", 1, 0, 1);
            if (form == 1 || form == 2) fade *= 1 - smooth((t / duration - 0.65f) / 0.35f);
            if (fade < 0.001f) return;
            float radius = value(block, ctx, "radius", 2, 0.1f, 64);
            float size = value(block, ctx, "grain_size", 0.065f, 0.008f, 0.5f);
            float band = value(block, ctx, "band_width", 0.32f, 0.01f, 3);
            float height = value(block, ctx, "height", 2.1f, 0.1f, 12);
            float speed = value(block, ctx, "speed", 1.4f, 0, 8);
            float elevation = value(block, ctx, "source_elevation", 0, -1, 1);
            float lift = form == 1 ? smooth(t / 0.09f) * (1 - smooth((t / duration - 0.48f) / 0.52f)) : 0;
            float detail = value(block, ctx, "detail", 1, 0.15f, 1);
            int count = Math.round(value(block, ctx, "count", 2600, 32, 6000) * detail);
            long seed = (long) ctx.paramFloat("seed", 42);
            for (int n = 0; n < count; n++) {
                float u = unit(seed, n * 7), v = unit(seed, n * 7 + 1), w = unit(seed, n * 7 + 2);
                position(form, u, v, w, t, duration, radius, band, height, speed, point);
                if (form == 1) orientGuard(point, elevation, lift);
                int i = buffer.spawn();
                buffer.setMass(i, owner);
                buffer.setLayer(i, ParticleBuffer.layerByte("iron_sand"));
                buffer.setPosition(i, point.x, point.y, point.z);
                buffer.setVelocity(i, 0, 0, 0);
                float metal = 0.42f + unit(seed, n * 7 + 3) * 0.58f;
                buffer.setColor(i, metal, metal, metal, fade * (0.78f + v * 0.22f));
                buffer.setSize(i, size * (0.45f + unit(seed, n * 7 + 4) * 0.9f));
                buffer.setRotation(i, u * 31 + t * (v - 0.5f) * 3);
                buffer.setAge(i, 0);
                buffer.setLifetime(i, 100);
            }
            int arcs = Math.round(value(block, ctx, "arc_count", 7, 0, 20) * detail);
            float width = value(block, ctx, "arc_width", 0.015f, 0.001f, 0.12f);
            long frameSeed = seed + (long) (t * 18) * 911;
            for (int a = 0; a < arcs; a++) {
                // Short broken currents run inside the same sand stream, never a separate neon hoop.
                float start = unit(frameSeed, a + 101);
                float span = form == 2 ? 0.20f : form == 1 ? 0.26f : 0.065f;
                path.clearPoints();
                for (int p = 0; p <= 18; p++) {
                    float f = p / 18f;
                    float u = form == 1 || form == 2 ? Math.clamp(start + span * f, 0, 1) : (start + span * f) % 1;
                    float v = form == 1 ? Math.clamp(0.12f + unit(frameSeed, a + 210) * 0.60f
                            + f * 0.18f + (unit(frameSeed + a * 37L, p) - 0.5f) * 0.035f, 0, 1)
                            : 0.5f + (unit(frameSeed + a * 37L, p) - 0.5f) * 0.3f;
                    position(form, u, v, form == 3 ? (a % 6 + 0.5f) / 6 : 0.5f,
                            t, duration, radius, band, height, speed, point);
                    if (form == 1) orientGuard(point, elevation, lift);
                    float jitter = (unit(frameSeed + a * 53L, p + 30) - 0.5f) * 0.07f;
                    path.addPoint(point.x, point.y + jitter, point.z,
                            width * (0.12f + (float) Math.sin(f * Math.PI)), 0);
                }
                emit(ctx, path, group, frameSeed + a, fade * 0.8f, "electricity");
            }
        };
    }

    /** Deterministic local geometry shared by grains and their embedded electric currents. */
    public static Vector3f position(int form, float u, float v, float w, float time, float duration,
                                    float radius, float band, float height, float speed, Vector3f result) {
        float angle, r, y;
        if (form == 1) {
            float lift = smooth(time / 0.09f) * (1 - smooth((time / duration - 0.48f) / 0.52f));
            angle = (u - 0.5f) * 1.18f;
            r = radius - lift * (0.43f + 0.16f * (float) Math.sin(v * Math.PI)) + (w - 0.5f) * band;
            float crown = 0.72f + 0.28f * (float) Math.sin(u * Math.PI);
            y = 0.13f + lift * (0.10f + v * height * crown) + (1 - lift) * v * 0.20f;
            y += (float) Math.sin(u * 35 + time * 24) * 0.035f * lift;
        } else if (form == 2) {
            float progress = smooth(time / duration);
            angle = (float) Math.toRadians(-60 + 120 * progress - (1 - u) * 24);
            r = 0.45f + u * (radius - 0.45f);
            y = 1 + (float) Math.sin(u * 11 - time * 25) * 0.09f + (v - 0.5f) * band;
            r += (w - 0.5f) * band;
        } else if (form == 3) {
            int stream = Math.min(5, (int) (w * 6));
            angle = u * (float) (Math.PI * 2) + time * speed * (stream % 2 == 0 ? 1 : -1);
            r = radius * (0.23f + stream * 0.15f) + (v - 0.5f) * band;
            y = 0.65f + (float) Math.sin(angle * 2 + stream) * height * 0.24f + v * height * 0.32f;
        } else {
            angle = u * (float) (Math.PI * 2) + time * speed;
            float curl = angle * 9 - time * 5;
            r = radius + (v - 0.5f) * band + (float) Math.sin(curl + w * 6.28f) * 0.035f;
            y = 0.10f + w * 0.22f + (float) Math.sin(curl) * 0.045f;
        }
        return result.set((float) Math.sin(angle) * r, y, (float) Math.cos(angle) * r);
    }

    private static float smooth(float x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Tilt the raised shield towards elevated attacks while its lift-off/return stays at the feet. */
    public static Vector3f orientGuard(Vector3f point, float elevation, float lift) {
        float angle = (float) Math.asin(Math.clamp(elevation, -1, 1)) * lift;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float y = point.y - 1.1f;
        return point.set(point.x, 1.1f + y * c + point.z * s, point.z * c - y * s);
    }
}
