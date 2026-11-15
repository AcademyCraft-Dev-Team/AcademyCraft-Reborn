package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.curve.CurveBuffer;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

import static org.academy.internal.client.render.vfxgraph.arc.SkyDischargeGeometry.*;


public final class SkyDischargeEmitter {
    static long newGroup() {
        return ArcGroups.next();
    }

    private SkyDischargeEmitter() {
    }

    public static List<PropertySpec> channelProperties() {
        return List.of(number("height", 40), number("radius", 0.85f), number("spread", 4.4f),
                number("sustain", 1.65f), number("decay", 0.5f), number("branches", 14),
                number("ground_radius", 10), number("segments", 72),
                number("corona", 0.18f), number("twist_rate", 7), number("twist_strength", 1), number("twist_duration", 0.2f));
    }

    public static List<PropertySpec> atmosphereProperties() {
        return List.of(number("height", 40), number("cloud_radius", 14), number("cloud_count", 38),
                number("sustain", 1.65f), number("decay", 0.5f), number("afterglow", 0.65f),
                number("impact_size", 4), number("dust_count", 14));
    }

    public static SimNode channel(VfxBlock block, PortValueSource ports) {
        var group = ArcGroups.next();
        var point = new Vector3f();
        var start = new Vector3f();
        float[] epoch = {Float.NaN};
        return (buffer, context) -> {
            context.arcs().removeGroup(group);
            var time = time(context, epoch);
            var sustain = value(block, context, "sustain", 1.65f, 0.05f, 20);
            var decay = value(block, context, "decay", 0.5f, 0.05f, 5);
            var current = current(time, sustain, decay) * value(block, context, "opacity", 1, 0, 1);
            if (current < 0.002f) return;
            var detail = value(block, context, "detail", 1, 0, 1);
            var height = value(block, context, "height", 40, 1, 192);
            var radius = value(block, context, "radius", 0.85f, 0.01f, 4);
            var spread = value(block, context, "spread", 4.4f, 0, 16);
            var twistRate = value(block, context, "twist_rate", 7, 0, 24);
            var twistStrength = value(block, context, "twist_strength", 1, 0, 1);
            var motionTime = Math.min(time, value(block, context, "twist_duration", 0.2f, 0, 5));
            var pulse = impactPulse(time);
            var extinction = 1f - smooth((time - sustain) / decay);
            var segments = Math.max(16, Math.round(value(block, context, "segments", 72, 16, 128)
                    * (0.4f + detail * 0.6f)));
            var seed = seed(context);
            var bottom = 1f - smooth(time / 0.055f);
            for (var shell = 0; shell < 2; shell++) {
                var arc = arc(context.arcs(), group, seed + shell,
                        shell == 0 ? 0.10f : 0.91f, shell == 0 ? 0.48f : 0.98f, 1f,
                        current * (shell == 0 ? value(block, context, "corona", 0.32f, 0, 1) : 1f));
                for (var i = 0; i <= segments; i++) {
                    var u = bottom + (1f - bottom) * i / segments;
                    sample(u, motionTime, seed, height, spread, twistRate, twistStrength, point);
                    var width = radius * (1.10f - 0.22f * u) * (0.93f + 0.07f * unit(seed + 48, i / 2));
                    width *= (0.35f + 0.65f * extinction) * (1f + pulse * 0.45f) * (shell == 0 ? 1.45f : 0.70f);
                    arc.addPoint(point.x, point.y, point.z, width, 0);
                }
            }
            var branches = Math.round(value(block, context, "branches", 14, 0, 32) * detail);
            var frame = (int) (motionTime * 13);
            for (var branch = 0; branch < branches; branch++) {
                var branchSeed = seed + branch * 79L + frame * 157L;
                var top = 0.28f + unit(seed, branch + 110) * 0.68f;
                if (top < bottom) continue;
                var span = 0.10f + unit(branchSeed, 5) * 0.35f;
                var angle = branch * 2.399963f + unit(seed, 53) * 6.283185f;
                var reach = spread * (0.45f + unit(branchSeed, 9) * 1.15f);
                sample(top, motionTime, seed, height, spread, twistRate, twistStrength, start);
                var arc = arc(context.arcs(), group, branchSeed, 0.28f, 0.72f, 1f,
                        current * (0.45f + unit(branchSeed, 12) * 0.45f));
                for (var j = 0; j <= 16; j++) {
                    var u = j / 16f;
                    var fork = reach * u;
                    var jitter = (unit(branchSeed, j + 100) - 0.5f) * spread * 0.23f * u;
                    arc.addPoint(start.x + (float) Math.cos(angle) * fork + jitter,
                            height * Math.max(bottom, top - u * span),
                            start.z + (float) Math.sin(angle) * fork - jitter * 0.7f,
                            radius * 0.11f * (1f - u * 0.96f), 1);
                }
            }
            if (time < 0.04f) return;
            var groundRadius = value(block, context, "ground_radius", 10, 0.1f, 32);
            for (var spark = 0; spark < Math.round(18 * detail); spark++) {
                var phase = (time * 1.4f + unit(seed, spark + 500)) % 1f;
                var angle = spark * 2.399963f;
                var distance = groundRadius * (0.15f + phase * 0.5f);
                var y = 0.25f + (float) Math.sin(phase * Math.PI) * (1f + unit(seed, spark + 501) * 2f);
                var x = (float) Math.cos(angle) * distance;
                var z = (float) Math.sin(angle) * distance;
                var mote = arc(context.arcs(), group, seed + 500 + spark, 0.64f, 0.90f, 1,
                        current * (float) Math.sin(phase * Math.PI));
                mote.addPoint(x, y, z, radius * 0.022f, 0);
                mote.addPoint(x + (float) Math.cos(angle) * 0.18f, y + 0.18f,
                        z + (float) Math.sin(angle) * 0.18f, 0.003f, 0);
            }
            for (var wave = 0; wave < 2 && detail > 0; wave++) {
                var shock = clamp((time - 0.04f - wave * 0.13f) / 0.58f);
                if (shock <= 0 || shock >= 1) continue;
                var ring = arc(context.arcs(), group, seed + 701 + wave, 0.32f, 0.71f, 1,
                        (1f - shock) * (wave == 0 ? 0.95f : 0.65f) * current);
                for (var i = 0; i <= 80; i++) {
                    var theta = i * 6.283185f / 80;
                    var r = groundRadius * (1f - (1f - shock) * (1f - shock))
                            * (0.98f + unit(seed, i) * 0.04f);
                    ring.addPoint((float) Math.cos(theta) * r, 0.12f + wave * 0.08f,
                            (float) Math.sin(theta) * r,
                            0.035f + radius * 0.08f * (1f - shock), 0, i / 12);
                }
            }
        };
    }

    public static SimNode atmosphere(VfxBlock block, PortValueSource ports) {
        float owner = ArcGroups.next();
        float[] epoch = {Float.NaN};
        return (buffer, context) -> {
            for (var i = buffer.count() - 1; i >= 0; i--) {
                if (buffer.mass(i) == owner) buffer.kill(i);
            }
            var t = time(context, epoch);
            var sustain = value(block, context, "sustain", 1.65f, 0.05f, 20);
            var decay = value(block, context, "decay", 0.5f, 0.05f, 5);
            var afterglow = value(block, context, "afterglow", 0.65f, 0.05f, 5);
            var opacity = value(block, context, "opacity", 1, 0, 1);
            var fade = (1f - smooth((t - sustain - decay) / afterglow)) * opacity;
            if (fade < 0.002f) return;
            var current = SkyDischargeGeometry.current(t, sustain, decay);
            var height = value(block, context, "height", 40, 1, 192);
            var radius = value(block, context, "cloud_radius", 14, 1, 48);
            var detail = value(block, context, "detail", 1, 0, 1);
            var cloudAlpha = value(block, context, "cloud_opacity", 1, 0, 1);
            var count = Math.round(value(block, context, "cloud_count", 38, 6, 64) * (0.35f + detail * 0.65f));
            var seed = seed(context);
            for (var i = 0; i < count; i++) {
                var u = (i + 0.5f) / count;
                var lobe = StormCloudShape.lobe(
                        i, count, t, seed, height, radius, new Vector4f());
                var light = current * (1f - u) * (0.20f + impactPulse(t) * 0.55f);
                particle(buffer, owner, "sky_cloud", lobe.x, lobe.y, lobe.z, lobe.w,
                        0.045f + light * 0.20f, 0.065f + light * 0.70f, 0.10f + light * 1.5f,
                        fade * cloudAlpha * 0.88f, i * 1.73f, t);
            }
            var impact = value(block, context, "impact_size", 4, 0.1f, 12);
            var burst = impactPulse(t);
            if (current > 0.002f) {
                particle(buffer, owner, "sky_halo", 0, height - 0.3f, 0, radius * (0.36f + burst * 0.22f),
                        0.10f, 0.52f, 1, current * cloudAlpha * 0.7f * opacity, 0, t);
                particle(buffer, owner, "sky_halo", 0, 0.5f, 0, impact * (1f + burst * 1.2f),
                        0.25f, 0.72f, 1, current * opacity, 0, t);
                particle(buffer, owner, "sky_halo", 0, 0.4f, 0, impact * (0.33f + burst * 0.4f),
                        0.92f, 0.98f, 1, current * opacity, 0, t);
            }
            var dust = Math.round(value(block, context, "dust_count", 14, 0, 32) * detail);
            for (var i = 0; i < dust; i++) {
                var angle = i * 2.399963f;
                var progress = clamp(t / (sustain + decay + afterglow));
                var r = impact * (0.5f + unit(seed, i + 301)) * (0.4f + progress * 1.5f);
                particle(buffer, owner, "sky_cloud", (float) Math.cos(angle) * r,
                        0.25f + progress * (0.8f + unit(seed, i + 302) * 1.2f),
                        (float) Math.sin(angle) * r, impact * (0.12f + progress * 0.28f),
                        0.08f, 0.14f, 0.20f, fade * smooth(t / 0.15f) * 0.23f,
                        70 + i * 1.43f, t);
            }
        };
    }

    private static ArcCurve arc(CurveBuffer buffer, long group, long seed, float r, float g, float b, float alpha) {
        var arc = ArcSupport.add(buffer, group);
        arc.setColor(r, g, b, alpha);
        arc.setLayer("sky_current");
        arc.setSeed(seed);
        arc.setLifetime(1f);
        arc.setNoiseStrength(0);
        arc.setDriftSpeed(0);
        return arc;
    }

    private static void particle(ParticleBuffer buffer, float owner, String layer, float x, float y, float z,
                                 float size, float r, float g, float b, float alpha, float rotation, float time) {
        var i = buffer.spawn();
        buffer.setMass(i, owner);
        buffer.setLayer(i, ParticleBuffer.layerByte(layer));
        buffer.setPosition(i, x, y, z);
        buffer.setVelocity(i, 0, 0, 0);
        buffer.setColor(i, r, g, b, alpha);
        buffer.setSize(i, size);
        buffer.setRotation(i, rotation);
        buffer.setAge(i, time);
        buffer.setLifetime(i, 100f);
    }

    private static float time(SimContext context, float[] epoch) {
        return ElectricArcEmitter.time(context, epoch);
    }

    private static long seed(SimContext context) {
        return (long) context.paramFloat("seed", 42);
    }

    private static float value(VfxBlock block, SimContext context, String key, float fallback, float min, float max) {
        return ElectricArcEmitter.value(block, context, key, fallback, min, max);
    }

    private static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id.replace('_', ' '), ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
