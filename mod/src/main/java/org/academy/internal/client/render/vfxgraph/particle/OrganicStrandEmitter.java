package org.academy.internal.client.render.vfxgraph.particle;
import org.academy.internal.client.render.vfxgraph.arc.ArcSupport;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, deterministic translucent strands. Geometry is local +Y, independent of a skill or actor.
 */
public final class OrganicStrandEmitter {
    private static final AtomicLong GROUPS = new AtomicLong(-1000000);

    private OrganicStrandEmitter() {
    }

    public static SimNode create(VfxBlock block) {
        var mode = (int) number(block, "mode", 0);
        var count = Math.clamp((int) number(block, "count", 12), 1, 48);
        var segments = Math.clamp((int) number(block, "segments", 80), 16, 128);
        var duration = Math.max(0.01f, number(block, "duration", 0.65f));
        var radius = number(block, "radius", 0.65f);
        var length = number(block, "length", 16f);
        var speed = number(block, "speed", 8f);
        var thickness = number(block, "thickness", 0.04f);
        var opacity = number(block, "opacity", 0.6f);
        var group = GROUPS.getAndDecrement();
        return (buffer, context) -> {
            context.arcs().removeGroup(group);
            var time = context.paramFloat("time", context.time());
            if (time < 0) time = context.time();
            var age = time / Math.max(0.01f, context.paramFloat("duration", duration));
            if (age < 0 || age >= 1) return;
            var fade = Math.min(1f, age * 12f) * Math.min(1f, (1f - age) * 4f);
            var liveLength = Math.clamp(context.paramFloat("length", length), 0.01f, 512f);
            var liveRadius = Math.clamp(context.paramFloat("width", radius), 0.01f, 32f);
            var height = Math.clamp(context.paramFloat("height", 1.8f), 0.1f, 32f);
            var seed = context.paramFloat("seed", 42f);
            for (var strand = 0; strand < count; strand++) {
                var arc = ArcSupport.add(context.arcs(), group);
                arc.setLayer("organic");
                arc.setMaxTubeSegments(6);
                var phase = strand * 2.399963f + seed * 0.017f;
                for (var point = 0; point <= segments; point++) {
                    var u = point / (float) segments;
                    var envelope = (float) Math.sin(Math.PI * u);
                    var wave = (float) Math.sin(u * 19f + phase + time * speed);
                    var angle = phase + u * (mode == 1 ? 13f : 7f) - time * speed;
                    float r, y;
                    if (mode == 0) {
                        // Straight light blades with soft organic edges; their endpoints stay on the ray.
                        r = liveRadius * envelope * (0.35f + 0.65f * strand / count) * (1f + wave * 0.13f);
                        angle = phase + wave * 0.14f;
                        y = liveLength * u;
                    } else if (mode == 1) {
                        r = liveRadius * (0.85f + wave * 0.13f + 0.06f * (float) Math.sin(u * 37f + phase)) * (1f - age * 0.25f);
                        y = height * (u * 0.65f + 0.35f * strand / count);
                    } else {
                        // Short curling wisps form a porous cloud, expanding as the target breaks apart.
                        r = liveRadius * (0.5f + 0.5f * (float) Math.sin(phase * 3f + u * 2f))
                                * (0.8f + age * 0.8f);
                        y = height * 0.5f + (float) Math.sin(phase + u * 4f) * height * 0.55f;
                    }
                    var width = thickness * (0.12f + 0.88f * envelope) * (1f + wave * 0.28f);
                    arc.addPoint((float) Math.cos(angle) * r, y, (float) Math.sin(angle) * r, width, 0f);
                }
                arc.setColor(1f, 1f, 1f, opacity * fade);
                arc.setLifetime(1f);
                arc.setSeed((long) seed + strand);
                arc.setNoiseStrength(0f);
                arc.setDriftSpeed(0f);
            }
        };
    }

    private static float number(VfxBlock block, String key, float fallback) {
        try {
            return Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
