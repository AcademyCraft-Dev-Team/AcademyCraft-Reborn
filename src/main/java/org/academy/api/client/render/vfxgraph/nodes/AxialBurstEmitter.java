package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/**
 * Analytic burst layers along local +Z, usable by any graph or entity adapter.
 * Time is in seconds; radius/opacity curves use normalized duration. Explicit time supports
 * editor scrubbing and a paused game clock without accumulating particles between samples.
 */
public final class AxialBurstEmitter {
    private AxialBurstEmitter() {
    }

    public static List<PropertySpec> beamProperties() {
        return List.of(number("length", 50), number("radius", 0.52f), number("duration", 1.6f),
                number("width_scale", 1), number("radius_scale", 1), number("intensity", 1),
                number("red", 1), number("green", 0.45f), number("blue", 0.08f),
                number("segments", 64), number("travel_time", 0.055f), number("end_cap", 1),
                text("radius_curve", "radius_envelope"), text("opacity_curve", "beam_opacity"));
    }

    public static List<PropertySpec> dischargeProperties() {
        return List.of(number("length", 50), number("duration", 1.6f), number("width_scale", 1),
                number("spread", 2.8f), number("arcs", 7), number("arc_width", 0.11f),
                number("arc_reach", 25), number("flicker_rate", 18), number("intensity", 1),
                text("opacity_curve", "arc_opacity"));
    }

    public static List<PropertySpec> shockProperties() {
        return List.of(number("length", 50), number("duration", 1.6f), number("width_scale", 1),
                number("shock_radius", 3.8f), number("rings", 3), number("sparks", 22),
                number("muzzle", 1), number("intensity", 1));
    }

    /** A straight cylindrical layer with a narrow muzzle and optional flat terminal disk. */
    public static SimNode beam(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            float duration = value(block, ctx, "duration", 1.6f, 0.05f, 2);
            float length = value(block, ctx, "length", 50, 0, Float.MAX_VALUE);
            float width = value(block, ctx, "width_scale", 1, 0, Float.MAX_VALUE);
            if (time >= duration || length <= 0 || width <= 0) return;
            float opacity = envelope(block, ctx, "opacity_curve", "beam_opacity", time / duration)
                    * value(block, ctx, "opacity", 1, 0, 1);
            if (opacity <= 0.001f) return;
            float radius = value(block, ctx, "radius", 0.52f, 0, 4) * width
                    * value(block, ctx, "radius_scale", 1, 0, 8)
                    * envelope(block, ctx, "radius_curve", "radius_envelope", time / duration);
            float intensity = value(block, ctx, "intensity", 1, 0, 4);
            float nearView = value(block, ctx, "view_near_origin", 0, 0, 1);
            var arc = arc(ctx.arcs(), group, seed(ctx),
                    value(block, ctx, "red", 1, 0, 4), value(block, ctx, "green", 0.45f, 0, 4),
                    value(block, ctx, "blue", 0.08f, 0, 4), opacity * intensity);
            arc.setEndCap(value(block, ctx, "end_cap", 1, 0, 1) > 0.5f);
            float head = smooth(time / value(block, ctx, "travel_time", 0.055f, 0.001f, 1));
            int segments = Math.round(value(block, ctx, "segments", 64, 8, 128));
            float muzzleLength = Math.min(length * 0.12f, 2.25f * (float) Math.sqrt(width));
            float tipLength = Math.min(length * 0.12f, 5.5f * (float) Math.sqrt(width));
            for (int i = 0; i <= segments; i++) {
                float u = (float) i / segments;
                // Reserve samples at both ends; a longer shot must not stretch the muzzle to tens of metres.
                float distance = u < 0.125f ? muzzleLength * u / 0.125f
                        : u > 0.875f ? length - tipLength * (1 - u) / 0.125f
                        : muzzleLength + (length - muzzleLength - tipLength) * (u - 0.125f) / 0.75f;
                // Keep the hand entry narrow, but carry the full cylinder radius to its flat endpoint.
                float taper = 0.14f + 0.86f * smooth(distance / muzzleLength);
                float ripple = 1f + 0.035f * (float) Math.sin(u * 85 - time * 72);
                // Use one concave width envelope near the hand instead of multiplying two S-curves.
                // Its projected width decreases towards the target, so the corona cannot balloon
                // midway through the view as the full ammunition radius is restored.
                float nearWidth = Math.min(width, 0.12f);
                float progress = Math.clamp(distance * head / (12 + 9 * width), 0, 1);
                float presentedWidth = nearWidth + (width - nearWidth) * progress * (2 - progress);
                float profile = (1 - nearView) * taper * ripple + nearView * presentedWidth / width;
                arc.addPoint(0, 0, distance * head, radius * profile, 0);
            }
        };
    }

    /** Seeded, irregular blue-white discharge paths with depth, forks, and independent flicker. */
    public static SimNode discharge(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float t = time(ctx, epoch);
            float duration = value(block, ctx, "duration", 1.6f, 0.05f, 2);
            float length = value(block, ctx, "length", 50, 0, Float.MAX_VALUE);
            float widthScale = value(block, ctx, "width_scale", 1, 0, Float.MAX_VALUE);
            if (t >= duration || length <= 0 || widthScale <= 0) return;
            float alpha = envelope(block, ctx, "opacity_curve", "arc_opacity", t / duration)
                    * value(block, ctx, "opacity", 1, 0, 1) * value(block, ctx, "intensity", 1, 0, 4);
            if (alpha <= 0.001f) return;
            float detail = value(block, ctx, "detail", 1, 0, 1);
            float density = 1 + 0.22f * (float) (Math.log(Math.max(1, widthScale)) / Math.log(2));
            int count = Math.round(Math.min(16, value(block, ctx, "arcs", 7, 0, 16) * density) * detail);
            float spread = value(block, ctx, "spread", 2.8f, 0, 12) * (float) Math.sqrt(widthScale);
            // Keep launch arcs close to the hand at every range. Separate overlapping sections
            // cover the rest of the resolved path instead of stretching the entire launch envelope.
            float launchSpan = Math.min(length, value(block, ctx, "arc_reach", 25, 0.1f, 128));
            int launchCount = Math.min(4, count);
            float tailBegin = Math.min(length * 0.45f, launchSpan * 0.55f);
            float stride = (length - tailBegin) / Math.max(1, count - launchCount);
            float width = value(block, ctx, "arc_width", 0.11f, 0.001f, 0.3f) * widthScale;
            float nearView = value(block, ctx, "view_near_origin", 0, 0, 1);
            int frame = (int) (t * value(block, ctx, "flicker_rate", 18, 1, 40));
            for (int branch = 0; branch < count; branch++) {
                long random = seed(ctx) + branch * 317L + frame * 911L;
                // Always retain two nearby paths and the terminal section.
                if (branch >= 2 && branch < count - 1 && unit(random, 3) < 0.16f) continue;
                float angle = branch * 2.399963f + unit(random, 4) * 1.3f;
                float span;
                float start;
                if (branch < launchCount) {
                    span = launchSpan * (0.65f + unit(random, 5) * 0.35f);
                    start = Math.min(length - span, unit(random, 6) * 0.75f);
                } else {
                    int section = branch - launchCount;
                    float overlap = Math.min(3, stride * 0.25f);
                    start = Math.max(0, tailBegin + section * stride - overlap);
                    float end = Math.min(length, tailBegin + (section + 1) * stride + overlap);
                    span = end - start;
                }
                int segments = Math.clamp((int) Math.ceil(span / 0.8f), 28, 96);
                for (int shell = 0; shell < 2; shell++) {
                    var arc = arc(ctx.arcs(), group, random, shell == 0 ? 0.12f : 0.78f,
                            shell == 0 ? 0.48f : 0.94f, 1, alpha * (shell == 0 ? 0.48f : 0.92f));
                    arc.setMaxTubeSegments(6);
                    for (int i = 0; i <= segments; i++) {
                        float u = (float) i / segments;
                        float bend = (float) Math.sin(u * Math.PI);
                        float radius = spread * bend * (0.5f + unit(random, 7) * 0.8f);
                        float phase = angle + (float) Math.sin(u * 8 + unit(random, 11) * 6) * 0.8f;
                        float jitter = (unit(random, i + 70) - 0.5f) * spread * 0.22f * bend;
                        arc.addPoint((float) Math.cos(phase) * radius + jitter,
                                (float) Math.sin(phase) * radius - jitter * 0.6f, start + u * span,
                                width * (shell == 0 ? 2.5f : 0.7f) * (0.2f + 0.8f * bend)
                                        * (1 - nearView + nearView * smooth((start + u * span - 1.2f) / 4)), 0);
                    }
                }
                var fork = arc(ctx.arcs(), group, random + 1, 0.35f, 0.73f, 1, alpha * 0.6f);
                fork.setMaxTubeSegments(4);
                float forkU = 0.5f;
                float forkRadius = spread * (0.5f + unit(random, 7) * 0.8f);
                float forkPhase = angle + (float) Math.sin(forkU * 8 + unit(random, 11) * 6) * 0.8f;
                for (int i = 0; i <= 8; i++) {
                    float u = i / 8f;
                    float r = forkRadius + u * spread * 0.6f;
                    fork.addPoint((float) Math.cos(forkPhase) * r,
                            (float) Math.sin(forkPhase) * r + (unit(random, i + 100) - 0.5f) * u * spread * 0.2f,
                            Math.min(length, start + (forkU + u * 0.22f) * span), width * 0.45f * (1 - u), 1);
                }
            }
        };
    }

    /** Broken pressure rings and outward ballistic streaks; never pretends to damage terrain. */
    public static SimNode shock(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float t = time(ctx, epoch);
            float length = value(block, ctx, "length", 50, 0, Float.MAX_VALUE);
            float width = value(block, ctx, "width_scale", 1, 0, Float.MAX_VALUE);
            if (t >= 0.7f || length <= 0 || width <= 0) return;
            float opacity = value(block, ctx, "opacity", 1, 0, 1)
                    * value(block, ctx, "intensity", 1, 0, 4) * value(block, ctx, "detail", 1, 0, 1);
            opacity *= 1 - 0.94f * value(block, ctx, "view_near_origin", 0, 0, 1);
            if (opacity <= 0.001f) return;
            float radius = value(block, ctx, "shock_radius", 3.8f, 0, 12) * (float) Math.sqrt(width);
            int rings = Math.round(value(block, ctx, "rings", 3, 0, 6));
            long seed = seed(ctx);
            for (int ring = 0; ring < rings; ring++) {
                if (ring == 0 && value(block, ctx, "muzzle", 1, 0, 1) == 0) continue;
                float age = (t - 0.025f - ring * 0.065f) / 0.38f;
                float z = (0.25f + ring * 2.8f + Math.max(0, age) * 1.8f) * (float) Math.sqrt(width);
                if (age <= 0 || age >= 1 || z > length) continue;
                float r = radius * (0.12f + 0.88f * smooth(age)) * (1f - ring * 0.12f);
                for (int piece = 0; piece < 3; piece++) {
                    var arc = arc(ctx.arcs(), group, seed + ring * 30L + piece,
                            0.74f, 0.84f, 0.94f, opacity * (1 - age) * 0.40f);
                    arc.setMaxTubeSegments(6);
                    for (int i = 0; i <= 22; i++) {
                        float angle = piece * 2.094395f + i / 22f * 1.8f + ring * 0.65f;
                        float flutter = 1f + (unit(seed + ring, i + piece * 22) - 0.5f) * 0.08f;
                        arc.addPoint((float) Math.cos(angle) * r * flutter,
                                (float) Math.sin(angle) * r * flutter, z,
                                0.12f * width * (1 - age) * (float) Math.sin(Math.PI * i / 22f), 0);
                    }
                }
            }
            if (value(block, ctx, "muzzle", 1, 0, 1) == 0) return;
            int sparks = Math.round(Math.min(48, value(block, ctx, "sparks", 22, 0, 48) * (float) Math.sqrt(width)));
            float fade = (1 - smooth(t / 0.65f)) * smooth(t / 0.025f);
            for (int i = 0; i < sparks; i++) {
                float angle = i * 2.399963f;
                float speed = (5 + unit(seed, i + 200) * 13) * (float) Math.sqrt(width);
                float r = t * speed * 0.6f;
                float z = Math.min(length, t * speed * (0.6f + unit(seed, i + 250)));
                var arc = arc(ctx.arcs(), group, seed + i + 200, 1, 0.55f, 0.16f, opacity * fade * 0.8f);
                arc.setMaxTubeSegments(4);
                arc.addPoint((float) Math.cos(angle) * r, (float) Math.sin(angle) * r - t * t * 2,
                        z, 0.016f * width, 0);
                arc.addPoint((float) Math.cos(angle) * r * 0.87f,
                        (float) Math.sin(angle) * r * 0.87f - t * t * 2,
                        Math.max(0, z - 0.25f - t * 0.4f), 0.002f, 0);
            }
        };
    }

    private static ArcCurve arc(ArcBuffer buffer, long group, long seed, float r, float g, float b, float alpha) {
        var arc = buffer.add(group);
        arc.setColor(r, g, b, alpha);
        arc.setSeed(seed);
        arc.setLifetime(1);
        arc.setNoiseStrength(0);
        arc.setDriftSpeed(0);
        return arc;
    }

    private static float envelope(VfxBlock block, SimContext ctx, String property, String fallback, float progress) {
        var curve = ctx.curve(block.properties().getOrDefault(property, fallback));
        float sample = curve == null ? 1 : CurveSampler.sample(curve, progress);
        return Float.isFinite(sample) ? Math.clamp(sample, 0, 4) : 0;
    }

    private static float time(SimContext ctx, float[] epoch) {
        if (!Float.isFinite(epoch[0])) epoch[0] = ctx.time();
        float override = ctx.paramFloat("time", -1);
        return Math.max(0, Float.isFinite(override) && override >= 0 ? override : ctx.time() - epoch[0]);
    }

    private static long seed(SimContext ctx) {
        return (long) ctx.paramFloat("seed", 42);
    }

    private static float value(VfxBlock block, SimContext ctx, String key, float fallback, float min, float max) {
        float property = fallback;
        try {
            property = Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
        }
        float value = ctx.paramFloat(key, property);
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id.replace('_', ' '), ValueType.FLOAT, Value.of(value), Optional.empty());
    }

    private static PropertySpec text(String id, String value) {
        return new PropertySpec(id, id.replace('_', ' '), ValueType.STRING, Value.string(value), Optional.empty());
    }
}
